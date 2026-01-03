(ns minustwo.stage.pseudo.particle
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.types :as types]
   [engine.world :as world]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_TRIANGLES]]
   [minustwo.systems.time :as time]
   [minustwo.systems.uuid-instance :as inst :refer [esse-inst remove-esse-inst]]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::age-in-step number?)
(s/def ::physics map?)
(s/def ::fire (s/keys :req-un [::age-in-step]
                      :opt-un [::physics]))

;; pseudo gravity
(s/def ::position ::types/vec3)
(s/def ::velocity ::types/vec3)

(def rules
  (o/ruleset
   {::fire-particle
    [:what
     [esse-id ::fire config]
     :then
     (let [physic (or (:physics config) {})]
       (s-> session
            (o/retract esse-id ::fire)
            (esse-inst esse-id
             {::age-in-step (:age-in-step config)
              ::position (v/vec3) ;; this is the local position, the global one is controlled by t3d/transform
              ::velocity (or (:initial-velocity physic) (v/vec3))})))]

    ::gravity
    [:what
     [::time/now ::time/delta dt]
     [esse-uuid ::age-in-step age {:then false}] ;; just to make sure it's alive
     [esse-uuid ::position p {:then false}]
     [esse-uuid ::velocity v {:then false}]
     :then
     (s-> session
          (o/insert esse-uuid ::position (m/+ p (g/scale v dt)))
          (o/insert esse-uuid ::velocity (m/+ v (v/vec3 0.0 (* -9.8 dt 1e-6) 0.0))))]

    ::live-particles
    [:what
     [::time/now ::time/step _]
     [esse-uuid ::inst/origin-id esse-id {:then false}]
     [esse-uuid ::age-in-step age {:then false}]
     [esse-uuid ::position position {:then false}]
     :then
     (if (<= age 0)
       (s-> session (remove-esse-inst esse-uuid))
       (s-> session
            (o/insert esse-uuid ::age-in-step (dec age))))]}))

;; this is for gltf-renderer/custom-draw-fn, I wonder if there is a static way to define this, protocol?
;; one idea is a protocol that not only define draw element, but also something that can prevent the cost of doing all that data passing before draw
(defn draw-fn [world ctx gltf-model prim]
  ;; hmm this cross backward chaining needs hammock time later I feel like
  (when-let [particles (seq (into []
                                  (filter #(= (:esse-id %) (:esse-id gltf-model)))
                                  (o/query-all world ::live-particles)))]
    (let [{:keys [model program-info]} gltf-model
          indices        (:indices prim)
          vert-count     (:count indices)
          component-type (:componentType indices)]
      (doseq [{:keys [position]} particles]
        (let [particle-trans (m/* (m-ext/translation-mat position) model)]
          (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec particle-trans)))
          (gl ctx drawElements GL_TRIANGLES vert-count component-type 0))))))

(def system
  {::world/rules #'rules})
