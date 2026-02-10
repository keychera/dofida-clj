(ns minusthree.engine.math
  (:require
   [fastmath.matrix :as mat]))

;; bootstrapping from thi.ng/geom to generateme/fastmath

(defn perspective
  [fovy aspect near far]
  (let [f      (/ (Math/tan (* 0.5 fovy)))
        nf     (/ (- near far))]
    (mat/mat
     (/ f aspect) 0.0 0.0 0.0
     0.0 f 0.0 0.0
     0.0 0.0 (* (+ near far) nf) -1.0
     0.0 0.0 (* 2.0 near far nf) 0.0)))
