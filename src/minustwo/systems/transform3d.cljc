(ns minustwo.systems.transform3d
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.math :as m-ext]
   [engine.world :as world]
   [engine.types :as types]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::translation ::types/vec3)
(s/def ::rotation ::types/quat)
(s/def ::scale ::types/vec3)
(s/def ::transform ::types/mat4)

(def default #::{::translation (v/vec3)
                 ::rotation (q/quat)
                 ::scale (v/vec3 1.0 1.0 1.0)})

(def rules
  (o/ruleset
   {::transform
    [:what
     [esse-id ::translation position-vec3]
     [esse-id ::rotation rotation-quat]
     [esse-id ::scale scale-vec3]
     :then
     (let [trans-mat     (m-ext/translation-mat position-vec3)
           rot-mat       (g/as-matrix rotation-quat)
           scale-mat     (m-ext/vec3->scaling-mat scale-vec3)
           transform-mat (reduce m/* [trans-mat rot-mat scale-mat])]
       (insert! esse-id ::transform transform-mat))]}))

(def system
  {::world/rules rules})