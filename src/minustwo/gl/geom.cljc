(ns minustwo.gl.geom 
    (:require
     [clojure.spec.alpha :as s]
     [thi.ng.geom.matrix]
     [thi.ng.geom.quaternion]
     [thi.ng.geom.vector]))

(s/def ::matrix #(instance? thi.ng.geom.matrix.Matrix44 %))
(s/def ::translation #(instance? thi.ng.geom.vector.Vec3 %))
(s/def ::rotation #(instance? thi.ng.geom.quaternion.Quat4 %))
(s/def ::scale #(instance? thi.ng.geom.vector.Vec3 %))
(s/def ::node+transform (s/keys :req-un [::translation ::rotation] :opt-un [::matrix  ::scale]))
(s/def ::transform-tree (s/coll-of ::node+transform :kind vector?))
