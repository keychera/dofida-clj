(ns minustwo.stage.celestial
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :refer [gl-ctx]]
   [engine.math.primitives :as primitives]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_FLOAT GL_TRIANGLES]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.room :as room]
   [minustwo.zone.render :as render]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]))

;; cmzw_ https://x.com/i/status/1964694609046700408

(def shader-omocha-vs
  (str cljgl/version-str
       "
        precision mediump float;
        
        in vec3 a_pos;
        in vec2 a_uv;

        uniform mat4 u_projection;
        uniform mat4 u_view;
        uniform mat4 u_model;
        
        out vec2 uv;
        
        void main() {
          gl_Position = u_projection * u_view * u_model * vec4(a_pos, 1.0);
          uv = a_uv;
        }"))

(def shader-omocha-fs
  (str cljgl/version-str
       "
        precision mediump float;
        
        in vec2 uv;
        out vec4 o_color;
        
        void main() {
          o_color = vec4(uv, 1.0, 1.0);
        }"))

(defn init-fn [world _game]
  (-> world
      (esse ::celestia
            #::shader{:use ::celestia-shader}
            t3d/default
            )))

(defn after-load-fn [world game]
  (let [ctx (gl-ctx game)]
    (-> world
        (esse ::celestia-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx shader-omocha-vs shader-omocha-fs)})
        (esse ::celestia
              #::t3d{:translation (v/vec3 0.0 5.0 0.0)
                     :scale (v/vec3 2.0)}
              #::gl-magic{:casted? :pending
                          :spell [{:bind-vao ::celestia-vao}
                                  {:buffer-data primitives/plane3d-vertices :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr :a_pos :use-shader ::celestia-shader :count 3 :component-type GL_FLOAT}
                                  {:buffer-data primitives/plane3d-uvs :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr :a_uv :use-shader ::celestia-shader :count 2 :component-type GL_FLOAT}
                                  {:unbind-vao true}]}))))

(def rules
  (o/ruleset
   {::celestia
    [:what
     [::celestia ::gl-magic/casted? true]
     [::celestia ::t3d/transform model]
     [::celestia ::shader/use shader-id]
     [shader-id ::shader/program-info program-info]]}))

(defn render-fn [world _game]
  (let [room-data (utils/query-one world ::room/data)
        ctx       (:ctx room-data)
        project   (:project room-data)
        view      (:player-view room-data)
        vao-db*   (:vao-db* room-data)]
    (when-let [{:keys [model program-info]} (utils/query-one world ::celestia)]
      (let [vao (get @vao-db* ::celestia-vao)]
        (gl ctx useProgram (:program program-info))
        (gl ctx bindVertexArray vao)

        (cljgl/set-uniform ctx program-info :u_projection (vec->f32-arr (vec project)))
        (cljgl/set-uniform ctx program-info :u_view (vec->f32-arr (vec view)))
        (cljgl/set-uniform ctx program-info :u_model (vec->f32-arr (vec model)))
        
        (gl ctx drawArrays GL_TRIANGLES 0 6)))))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules
   ::world/render-fn #'render-fn})
