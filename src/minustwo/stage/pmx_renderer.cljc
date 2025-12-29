(ns minustwo.stage.pmx-renderer
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert!]]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.IK :refer [IK-transducer1]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_DYNAMIC_DRAW
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT GL_TEXTURE0
                                  GL_TEXTURE_2D GL_TRIANGLES GL_UNIFORM_BUFFER
                                  GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.shader :as shader]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
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

(defn init-fn [world game]
  (-> world
      (firstperson/insert-player (v/vec3 0.0 15.5 15.0) (v/vec3 0.0 0.0 -1.0))
      (esse ::skinning-ubo
            (let [ctx (gl-ctx game)
                  ubo (cljgl/create-buffer ctx)]
              (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
              (gl ctx bufferData GL_UNIFORM_BUFFER (* MAX_JOINTS 16 4) GL_DYNAMIC_DRAW)
              (gl ctx bindBufferBase GL_UNIFORM_BUFFER 0 ubo)
              (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
              {::shader/ubo ubo}))
      (esse ::silverwolf-pmx
            #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
            #::shader{:use ::pmx-shader})))

(defn after-load-fn [world game]
  (let [ctx (gl-ctx game)]
    (-> world
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx pmx-vert pmx-frag)}))))

(defn pmx-spell [data {:keys [esse-id tex-unit-offset]}]
  (let [textures (:textures data)]
    (->> [{:bind-vao esse-id}
          {:buffer-data (:POSITION data) :buffer-type GL_ARRAY_BUFFER}
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
           (map-indexed (fn [idx img-uri] {:bind-texture (str "tex-" esse-id "-" idx)
                                           :image {:uri img-uri} :tex-unit (+ (or tex-unit-offset 0) idx)}))
           textures)

          {:buffer-data (:INDICES data) :buffer-type GL_ELEMENT_ARRAY_BUFFER}
          {:unbind-vao true}]
         flatten (into []))))

(def rules
  (o/ruleset
   {::I-cast-pmx-magic!
    [:what
     [esse-id ::pmx-model/data data]
     [::pmx-shader ::shader/program-info _]
     [esse-id ::gl-magic/casted? :pending]
     :then
     (println esse-id "got" (keys data) "!")
     (let [spell (pmx-spell data {:esse-id esse-id})]
       (insert! esse-id #::gl-magic{:spell spell}))]

    ::render-data
    [:what
     [esse-id ::pmx-model/data pmx-data]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::shader/use ::pmx-shader]
     [::pmx-shader ::shader/program-info program-info]
     [::skinning-ubo ::shader/ubo skinning-ubo]
     :then
     (println esse-id "is ready to render!")]}))

(defn create-joint-mats-arr [bones]
  (let [f32s (f32-arr (* 16 (count bones)))]
    (doseq [{:keys [idx global-transform inv-bind-mat]} bones]
      (let [joint-mat (m/* global-transform inv-bind-mat)
            i         (* idx 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (float (nth joint-mat j))))))
    f32s))

(defn pose-for-the-fans! [bones]
  (into []
        (comp
         (IK-transducer1 "左腕" "左ひじ" "左手首" (v/vec3 4.27 3.0 -10.0))
         (IK-transducer1 "右腕" "右ひじ" "右手首" (v/vec3 -4.27 3.0 -10.0)))
        bones))

(defn render-fn [world _game]
  (let [{:keys [ctx project player-view vao-db* texture-db*]}
        (utils/query-one world ::room/data)]
    (doseq [{:keys [esse-id pmx-data program-info skinning-ubo]}
            (o/query-all world ::render-data)]
      (let [program    (:program program-info :program)
            vao        (get @vao-db* esse-id)
            materials  (:materials pmx-data)
            bones      (pose-for-the-fans! (:bones pmx-data))
            bones      (into [] pmx-model/global-transform-xf bones)
            joint-mats (create-joint-mats-arr bones)]
        ;; (def err [:err (gl ctx getError)])
        #_{:clj-kondo/ignore [:inline-def]}
        (def hmm pmx-data)
        (gl ctx useProgram program)
        (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
        (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec player-view)))
        (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec (m-ext/scaling-mat 1.0))))

        (gl ctx bindBuffer GL_UNIFORM_BUFFER skinning-ubo)
        (gl ctx bufferSubData GL_UNIFORM_BUFFER 0 joint-mats)
        (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))

        (gl ctx bindVertexArray vao)

        (doseq [material materials]
          (let [face-count  (:face-count material)
                face-offset (* 4 (:face-offset material))
                tex-idx     (:texture-index material)
                tex         (get @texture-db* (str "tex-" esse-id "-" tex-idx))]
            (when-let [{:keys [tex-unit texture]} tex]
              (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
              (gl ctx bindTexture GL_TEXTURE_2D texture)
              (cljgl/set-uniform ctx program-info 'u_mat_diffuse tex-unit))

            (gl ctx drawElements GL_TRIANGLES face-count GL_UNSIGNED_INT face-offset)))))))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules
   ::world/render-fn #'render-fn})

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  ;; err
  (viscous/inspect hmm)

  :-)