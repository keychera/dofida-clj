(ns minusone.rules.transform3d
  (:require
   [clojure.spec.alpha :as s]
   [minusone.rules.types :as types]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]))

(s/def ::position ::types/vec3)
(s/def ::rotation ::types/quat)

(def default #::{:position (v/vec3) :rotation (q/quat)})