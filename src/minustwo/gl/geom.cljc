(ns minustwo.gl.geom
  (:require
   [clojure.spec.alpha :as s]
   [engine.types :as types]))

(s/def ::matrix ::types/mat4)
(s/def ::translation ::types/vec3)
(s/def ::rotation ::types/quat)
(s/def ::scale ::types/vec3)
(s/def ::node+transform (s/keys :req-un [::translation ::rotation] :opt-un [::matrix ::scale]))
(s/def ::transform-tree (s/coll-of ::node+transform :kind vector?))
