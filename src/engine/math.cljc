(ns engine.math
  (:require
   [play-cljc.math :as pl-cljc-m]
   [thi.ng.geom.matrix :as mat]))

;; math sugar

(defn scaling-mat [sx sy sz]
  (mat/matrix44
   sx,  0.0  0.0  0.0
   0.0  sy,  0.0  0.0
   0.0  0.0  sz,  0.0
   0.0  0.0  0.0  1.0))

(defn translation-mat [tx ty tz]
  (mat/matrix44
   1.0  0.0  0.0  0.0
   0.0  1.0  0.0  0.0
   0.0  0.0  1.0  0.0
   tx,  ty,  tz,  1.0))

(def identity-mat-4
  #?(:clj (float-array (pl-cljc-m/identity-matrix 4))
     :cljs (pl-cljc-m/identity-matrix 4)))