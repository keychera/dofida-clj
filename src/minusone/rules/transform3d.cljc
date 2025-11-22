(ns minusone.rules.transform3d
  (:require
   [clojure.spec.alpha :as s]
   [thi.ng.geom.quaternion :as q #?@(:cljs [:refer [Quat4]])]
   [thi.ng.geom.vector :as v #?@(:cljs [:refer [Vec3]])])
  #?(:clj
     (:import
      [thi.ng.geom.quaternion Quat4]
      [thi.ng.geom.vector Vec3])))

(s/def ::position #(instance? Vec3 %))
(s/def ::localpos #(instance? Vec3 %))
(s/def ::rotation #(instance? Quat4 %))
(s/def ::localrot #(instance? Quat4 %))

(def default #::{:position (v/vec3) :rotation (q/quat)})