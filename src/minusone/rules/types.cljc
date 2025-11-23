(ns minusone.rules.types
  (:require
   [clojure.spec.alpha :as s]
   #?@(:cljs
       [[thi.ng.geom.matrix :refer [Matrix44]]
        [thi.ng.geom.quaternion :refer [Quat4]]
        [thi.ng.geom.vector :refer [Vec3]]]))
  #?(:clj
     (:import
      [thi.ng.geom.matrix Matrix44]
      [thi.ng.geom.quaternion Quat4]
      [thi.ng.geom.vector Vec3])))

(s/def ::vec3 #(instance? Vec3 %))
(s/def ::mat4 #(instance? Matrix44 %))
(s/def ::quat #(instance? Quat4 %))