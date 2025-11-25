(ns minusone.rules.transform3d
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.math :as m-ext]
   [engine.world :as world]
   [minusone.rules.types :as types]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::position ::types/vec3)
(s/def ::rotation ::types/quat)
(s/def ::scale ::types/vec3)
(s/def ::local-translate ::types/mat4)
(s/def ::local-rotate ::types/mat4)
(s/def ::local-scale ::types/mat4)
(s/def ::local-transform ::types/mat4)

(def default #::{::position (v/vec3) ::rotation (q/quat) ::scale (v/vec3 1.0 1.0 1.0)})

(def rules
  (o/ruleset
   {::translation
    [:what [esse-id ::position position-vec3]
     :then (let [[x y z] position-vec3]
             (insert! esse-id ::local-translate (m-ext/translation-mat x y z)))]

    ::rotation
    [:what [esse-id ::rotation rotation-quat]
     :then (insert! esse-id ::local-rotate (g/as-matrix rotation-quat))]

    ::scaling
    [:what [esse-id ::scale scale-vec3]
     :then (let [[x y z] scale-vec3]
             (insert! esse-id ::local-scale (m-ext/scaling-mat x y z)))]

    ::transform
    [:what
     [esse-id ::local-translate local-translate]
     [esse-id ::local-rotate local-rotate]
     [esse-id ::local-scale local-scale]
     :then
     (insert! esse-id ::local-transform (reduce m/* [local-translate local-rotate local-scale]))]}))

(def system
  {::world/rules rules})