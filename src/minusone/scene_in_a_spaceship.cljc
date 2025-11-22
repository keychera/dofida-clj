(ns minusone.scene-in-a-spaceship
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.sugar :refer [f32-arr i32-arr identity-mat-4]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.transform3d :as t3d]
   [odoyle.rules :as o]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

;; for the umpteenth time, we learn opengl again
;; https://learnopengl.com/Getting-started/Hello-Triangle

(def esse-default-facts [t3d/default])
(defn esse [world id & facts]
  (o/insert world id (apply utils/deep-merge (concat esse-default-facts facts))))

(def triangle-data
  (f32-arr 0.5  0.5 0.0
           0.5 -0.5 0.0
           -0.5 -0.5 0.0
           -0.5  0.5 0.0))

(def triangle-indices
  (i32-arr 0 1 3
           1 2 3))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_pos vec3}
   :uniforms   '{mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* mvp (vec4 a_pos "1.0"))))}})

(def fragment-shader
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "0.42" "1.0" "0.69" "0.9")))}})

(defn init-fn [world game]
  (-> world
      (esse ::a-triangle
            #::shader{:program-data (shader/create-program game vertex-shader fragment-shader)}
            #::vao{:buffers [{:data triangle-data
                              :buffer-type (gl game ARRAY_BUFFER)
                              :attr 'a_pos ;; bind to attribute parts feel complected
                              :size 3
                              :type (gl game FLOAT)}
                             {:data triangle-indices
                              :buffer-type (gl game ELEMENT_ARRAY_BUFFER)}]})))

(defn after-load-fn [world _game]
  (-> world
      (esse ::a-cube
            #::t3d{:position (v/vec3 0.0 5.0 0.0)
                   :rotation (q/quat-from-axis-angle
                              (v/vec3 0.0 1.0 0.0)
                              (m/degrees 45.0))})
      (esse ::dofida
            #::t3d{:position (v/vec3 -5.0 0.0 5.0)})))

(def rules
  (o/ruleset
   {::esses
    [:what
     [esse-id ::shader/program-data program-data]
     [esse-id ::vao/vao vao]
     :then
     (println esse-id "ready to render")]}))

(defn render-fn [world game]
  (doseq [esse (o/query-all world ::esses)]
    (let [{:keys [program-data vao]} esse
          mvp-loc (get (:uni-locs program-data) 'mvp)]
      (gl game useProgram (:program program-data))
      (gl game bindVertexArray vao)
      (gl game uniformMatrix4fv mvp-loc false identity-mat-4)
      (gl game drawElements (gl game TRIANGLES) 6 (gl game UNSIGNED_INT) 0))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn})

