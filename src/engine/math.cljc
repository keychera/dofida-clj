(ns engine.math
  (:require
   [thi.ng.geom.matrix :as mat]))

;; math sugar

(defn scaling-mat
  ([s] (scaling-mat s s s))
  ([sx sy sz]
   (mat/matrix44
    sx,  0.0  0.0  0.0
    0.0  sy,  0.0  0.0
    0.0  0.0  sz,  0.0
    0.0  0.0  0.0  1.0)))

(defn translation-mat [tx ty tz]
  (mat/matrix44
   1.0  0.0  0.0  0.0
   0.0  1.0  0.0  0.0
   0.0  0.0  1.0  0.0
   tx,  ty,  tz,  1.0))
