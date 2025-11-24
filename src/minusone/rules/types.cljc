(ns minusone.rules.types
  (:require
   [clojure.spec.alpha :as s]
   [thi.ng.geom.matrix #?@(:cljs [:refer [Matrix44]])]
   [thi.ng.geom.quaternion #?@(:cljs [:refer [Quat4]])]
   [thi.ng.geom.vector #?@(:cljs [:refer [Vec3]])])
  #?(:clj
     (:import
      [thi.ng.geom.matrix Matrix44]
      [thi.ng.geom.quaternion Quat4]
      [thi.ng.geom.vector Vec3])))

(s/def ::vec3 #(instance? Vec3 %))
(s/def ::mat4 #(instance? Matrix44 %))
(s/def ::quat #(instance? Quat4 %))