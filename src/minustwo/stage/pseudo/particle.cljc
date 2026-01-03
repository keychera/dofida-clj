(ns minustwo.stage.pseudo.particle
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.world :as world]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_TRIANGLES]]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
   [thi.ng.geom.core :as g]))

(s/def ::age-in-step number?)
(s/def ::fire (s/keys :req-un [::age-in-step]))

(def rules
  (o/ruleset
   {::fire-particle
    [:what
     [esse-id ::fire config]
     :then
     (s-> session
          (o/retract esse-id ::fire)
          (o/insert esse-id ::age-in-step (:age-in-step config)))]

    ::live-particles
    [:what
     [::time/now ::time/step _]
     [esse-id ::age-in-step age {:then false}]
     :then
     (if (<= age 0)
       (s-> session
            (o/retract esse-id ::age-in-step))
       (s-> session
            (o/insert esse-id ::age-in-step (dec age))))]}))

;; this is for gltf-renderer/custom-draw-fn, I wonder if there is a static way to define this, protocol?
;; one idea is a protocol that not only define draw element, but also something that can prevent the cost of doing all that data passing before draw
(defn draw-fn [world ctx gltf-model prim]
  (when-let [particles (seq (o/query-all world ::live-particles))]
    (let [{:keys [model program-info]} gltf-model
          indices        (:indices prim)
          vert-count     (:count indices)
          component-type (:componentType indices)]
      (doseq [particle particles]
        (let [particle-trans (or (g/transform-vector model (v/vec3)) particle)]
          (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec particle-trans)))
          (gl ctx drawElements GL_TRIANGLES vert-count component-type 0))))))

(def system
  {::world/rules #'rules})
