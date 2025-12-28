(ns minustwo.stage.pmx-renderer
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert!]]
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_ELEMENT_ARRAY_BUFFER
                                  GL_FLOAT GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.shader :as shader]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]))

(def pmx-vert
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 POSITION;
 in vec3 NORMAL;
 in vec2 TEXCOORD;

 uniform mat4 u_model;
 uniform mat4 u_view;
 uniform mat4 u_projection;

 out vec3 Normal;
 out vec2 TexCoord;

 void main() {
   vec4 pos;
   pos = vec4(POSITION, 1.0);
   gl_Position = u_projection * u_view * u_model * pos;
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

(defn init-fn [world _game]
  (-> world
      (firstperson/insert-player (v/vec3 0.0 15.5 15.0) (v/vec3 0.0 0.0 -1.0))
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
     [esse-id ::pmx-model/data data]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::shader/use ::pmx-shader]
     [::pmx-shader ::shader/program-info program-info]
     :then
     (println esse-id "is ready to render!")]}))

(defn render-fn [world _game]
  (let [{:keys [ctx project player-view vao-db* texture-db*]}
        (utils/query-one world ::room/data)]
    (doseq [{:keys [esse-id data] :as render-data} (o/query-all world ::render-data)]
      (let [program-info (:program-info render-data)
            program      (:program program-info :program)
            vao          (get @vao-db* esse-id)
            materials    (:materials data)]
        ;; (def err [:err (gl ctx getError)])
        #_{:clj-kondo/ignore [:inline-def]}
        (def hmm render-data)
        (gl ctx useProgram program)
        (gl ctx bindVertexArray vao)

        (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
        (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec player-view)))
        (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec (m-ext/scaling-mat 1.0))))

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

  ;; err
  hmm

  :-)