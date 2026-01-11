(ns minustwo.stage.pmx-renderer
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert! s->]]
   [engine.sugar :refer [f32-arr vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.morph :as morph]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_DYNAMIC_DRAW
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT GL_TEXTURE0
                                  GL_TEXTURE_2D GL_TRIANGLES GL_UNIFORM_BUFFER
                                  GL_UNSIGNED_INT]]
   [minustwo.gl.geom :as geom]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.shader :as shader]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.stage.esse-model :as esse-model]
   [minustwo.stage.pseudo.bones :as bones]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.math.core :as m]))

(def MAX_JOINTS 500)

(def pmx-vert
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 POSITION;
 in vec3 NORMAL;
 in vec2 TEXCOORD;
 in vec4 WEIGHTS;
 in uvec4 JOINTS;

 uniform mat4 u_model;
 uniform mat4 u_view;
 uniform mat4 u_projection;
 layout(std140) uniform Skinning {
   mat4[" MAX_JOINTS "] u_joint_mats;
 };
 
 out vec3 Normal;
 out vec2 TexCoord;

 void main() {
   vec4 pos;
   pos = vec4(POSITION, 1.0);
   mat4 skin = (WEIGHTS.x * u_joint_mats[JOINTS.x]) 
             + (WEIGHTS.y * u_joint_mats[JOINTS.y]) 
             + (WEIGHTS.z * u_joint_mats[JOINTS.z])
             + (WEIGHTS.w * u_joint_mats[JOINTS.w]);
   gl_Position = u_projection * u_view * u_model * skin * (pos);
   Normal = NORMAL;
   TexCoord = TEXCOORD;
 }
"))

(def pmx-frag
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 Normal;
 in vec2 TexCoord;
 
 uniform sampler2D u_mat_diffuse;
 
 out vec4 o_color;

 void main() {
   o_color = vec4(texture(u_mat_diffuse, TexCoord).rgb, 1.0); 
 }
"))

(defn pmx-default [model-path config]
  (utils/deep-merge
   {::pmx-model/model-path model-path
    ::pmx-model/config config}
   #::shader{:use ::pmx-shader}
   t3d/default
   pose/default))

(defn init-fn [world game]
  (let [ctx (gl-ctx game)]
    (-> world
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx pmx-vert pmx-frag)})
        (esse ::skinning-ubo
              (let [ubo (cljgl/create-buffer ctx)]
                (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
                (gl ctx bufferData GL_UNIFORM_BUFFER (* MAX_JOINTS 16 4) GL_DYNAMIC_DRAW)
                (gl ctx bindBufferBase GL_UNIFORM_BUFFER 0 ubo)
                (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
                {::shader/ubo ubo})))))

(defn after-load-fn [world _game] world)

(defn texture-naming [esse-id tex-idx]
  (str "tex-" esse-id "-" tex-idx))

(defn pmx-spell [data {:keys [esse-id tex-unit-offset]}]
  (let [textures (:textures data)]
    (->> [{:bind-vao esse-id}
          {:buffer-data (:POSITION data) :buffer-type GL_ARRAY_BUFFER :buffer-name :position}
          {:point-attr 'POSITION :use-shader ::pmx-shader :count 3 :component-type GL_FLOAT}
          {:buffer-data (:NORMAL data) :buffer-type GL_ARRAY_BUFFER}
          {:point-attr 'NORMAL :use-shader ::pmx-shader :count 3 :component-type GL_FLOAT}
          {:buffer-data (:TEXCOORD data) :buffer-type GL_ARRAY_BUFFER}
          {:point-attr 'TEXCOORD :use-shader ::pmx-shader :count 2 :component-type GL_FLOAT}

          {:buffer-data (:WEIGHTS data) :buffer-type GL_ARRAY_BUFFER}
          {:point-attr 'WEIGHTS :use-shader ::pmx-shader :count 4 :component-type GL_FLOAT}
          {:buffer-data (:JOINTS data) :buffer-type GL_ARRAY_BUFFER}
          {:point-attr 'JOINTS :use-shader ::pmx-shader :count 4 :component-type GL_UNSIGNED_INT}

          (eduction
           (map-indexed (fn [tex-idx img-uri]
                          {:bind-texture (texture-naming esse-id tex-idx)
                           :image {:uri img-uri} :tex-unit (+ (or tex-unit-offset 0) tex-idx)}))
           textures)

          {:buffer-data (:INDICES data) :buffer-type GL_ELEMENT_ARRAY_BUFFER}
          {:unbind-vao true}]
         flatten (into []))))

(defn create-joint-mats-arr [bones-db* bones]
  (let [f32s (f32-arr (* 16 (count bones)))]
    (doseq [{:keys [idx inv-bind-mat] :as bone} bones]
      (let [gt        (bones/resolve-gt bones-db* bone)
            joint-mat (m/* gt inv-bind-mat)
            i         (* idx 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (float (nth joint-mat j))))))
    f32s))

(defn model-gl-context [room model dynamic-data]
  (let [{:keys [ctx project player-view vao-db*]} room
        {:keys [esse-id pmx-data program-info skinning-ubo position-buffer bones-db*]} model
        {:keys [transform pose-tree]} dynamic-data
        program    (:program program-info :program)
        vao        (get @vao-db* esse-id)
        ^floats POSITION   (:POSITION pmx-data) ;; morph mutate this in a mutable way!
        ^floats joint-mats (create-joint-mats-arr bones-db* pose-tree)]
    (gl ctx useProgram program)
    (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
    (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec player-view)))
    (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec transform)))

    ;; bufferSubData is bottlenecking rn, visualvm checked, todo optimization
    (gl ctx bindBuffer GL_ARRAY_BUFFER position-buffer)
    (gl ctx bufferSubData GL_ARRAY_BUFFER 0 POSITION)

    (gl ctx bindBuffer GL_UNIFORM_BUFFER skinning-ubo)
    (gl ctx bufferSubData GL_UNIFORM_BUFFER 0 joint-mats)
    (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))

    (gl ctx bindVertexArray vao)))

(defn render-material [room model material _dynamic-data]
  (let [{:keys [ctx texture-db*]} room
        {:keys [program-info]} model
        face-count  (:face-count material)
        face-offset (* 4 (:face-offset material))
        tex         (get @texture-db* (:tex-name material))]
    (when-let [{:keys [tex-unit texture]} tex]
      (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
      (gl ctx bindTexture GL_TEXTURE_2D texture)
      (cljgl/set-uniform ctx program-info 'u_mat_diffuse tex-unit))

    (gl ctx drawElements GL_TRIANGLES face-count GL_UNSIGNED_INT face-offset)))

(def rules
  (o/ruleset
   {::I-cast-pmx-magic!
    [:what
     [esse-id ::pmx-model/data data {:then false}]
     [esse-id ::pmx-model/config config]
     [::pmx-shader ::shader/program-info _]
     [esse-id ::gl-magic/casted? :pending]
     :then
     (println esse-id "got" (keys data) "!")
     (let [data   (update data :materials
                          (fn [mats]
                            (into []
                                  (map (fn [mat]
                                         (let [tex-idx  (:texture-index mat)
                                               tex-name (texture-naming esse-id tex-idx)]
                                           (assoc mat :tex-name tex-name))))
                                  mats)))
           spell  (pmx-spell data {:esse-id esse-id :tex-unit-offset (:tex-unit-offset config)})
           pos!   (:POSITION data)
           morphs (into []
                        (map (fn [morph]
                               {:morph-name  (:local-name morph)
                                :offset-coll (or (some-> morph :offsets :offset-data) [])}))
                        (:morphs data))]
       (s-> session
            (o/insert esse-id #::gl-magic{:spell spell})
            (o/insert esse-id ::pmx-model/data data)
            (o/insert esse-id ::morph/position-arr! pos!)
            (o/insert esse-id ::morph/morph-data morphs)))]

    ::bone-to-pose-tree
    [:what
     [esse-id ::gl-magic/casted? true]
     [esse-id ::pmx-model/data pmx-data {:then false}]
     :then
     (println esse-id "prep bones!")
     (insert! esse-id ::geom/transform-tree (:bones pmx-data))]

    ::render-data
    [:what
     [esse-id ::pmx-model/data pmx-data {:then false}]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::shader/use ::pmx-shader]
     [::pmx-shader ::shader/program-info program-info]
     [::skinning-ubo ::shader/ubo skinning-ubo]
     [esse-id ::t3d/transform transform]
     [esse-id ::pose/pose-tree pose-tree {:then false}]
     [:position ::shader/buffer position-buffer]
     [::world/global ::bones/db* bones-db* {:then false}]
     :then
     (println esse-id "is ready to render!")
     (insert! esse-id
              #::esse-model{:data match
                            :gl-context-fn model-gl-context
                            :mat-render-fn render-material
                            :materials (:materials pmx-data)})]

    ::global-transform
    [:what
     [::time/now ::time/delta dt {:then false}]
     [::time/now ::time/slice 4]
     [esse-id ::pmx-model/data pmx-data {:then false}]
     [esse-id ::pose/pose-tree pose-tree {:then false}]
     [::world/global ::bones/db* bones-db* {:then false}]
     :then
     (let [pose-tree (into [] (bones/bone-transducer bones-db* dt) pose-tree)]
       (insert! esse-id ::pose/pose-tree pose-tree))]}))

;; perversion, obsession, what motivates you to move forward?
#_(what ") made (" you choose this path?. interesting that you relied on "(() these" otherworldly dimensions for your happiness.)

#_(defn render-fn [world _game]
    (let [{:keys [ctx project player-view vao-db* texture-db*]}
          (utils/query-one world ::room/data)]
      (doseq [{:keys [esse-id pmx-data program-info skinning-ubo
                      transform pose-tree position-buffer bones-db*]}
              (o/query-all world ::render-data)]
        (let [program    (:program program-info :program)
              vao        (get @vao-db* esse-id)
              materials  (:materials pmx-data)
              ^floats POSITION   (:POSITION pmx-data) ;; morph mutate this in a mutable way!
              ^floats joint-mats (create-joint-mats-arr bones-db* pose-tree)]
        ;; (def err [:err (gl ctx getError)])
          #_{:clj-kondo/ignore [:inline-def]}
          (def hmm pmx-data)
          (gl ctx useProgram program)
          (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
          (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec player-view)))
          (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec transform)))

        ;; bufferSubData is bottlenecking rn, visualvm checked, todo optimization
          (gl ctx bindBuffer GL_ARRAY_BUFFER position-buffer)
          (gl ctx bufferSubData GL_ARRAY_BUFFER 0 POSITION)

          (gl ctx bindBuffer GL_UNIFORM_BUFFER skinning-ubo)
          (gl ctx bufferSubData GL_UNIFORM_BUFFER 0 joint-mats)
          (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))

          (gl ctx bindVertexArray vao)

          (doseq [material materials]
            (let [face-count  (:face-count material)
                  face-offset (* 4 (:face-offset material))
                  tex         (get @texture-db* (:tex-name material))]
              (when-let [{:keys [tex-unit texture]} tex]
                (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
                (gl ctx bindTexture GL_TEXTURE_2D texture)
                (cljgl/set-uniform ctx program-info 'u_mat_diffuse tex-unit))

              (gl ctx drawElements GL_TRIANGLES face-count GL_UNSIGNED_INT face-offset)))))))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules
   #_#_::world/render-fn #'render-fn})

