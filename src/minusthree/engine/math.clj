(ns minusthree.engine.math
  (:require
   [fastmath.matrix :as mat]
   [fastmath.vector :as v]))

;; bootstrapping from thi.ng/geom to generateme/fastmath

(defn translation-mat
  ([xyz]
   (let [[^float tx ^float ty ^float tz] xyz]
     (translation-mat tx ty tz)))
  ([tx ty tz]
   (mat/mat
    1.0  0.0  0.0  0.0
    0.0  1.0  0.0  0.0
    0.0  0.0  1.0  0.0
    tx,  ty,  tz,  1.0)))

(defn scaling-mat
  ([xyz]
   (let [[^float sx ^float sy ^float sz] xyz]
     (scaling-mat sx sy sz)))
  ([sx sy sz]
   (mat/mat
    sx,  0.0  0.0  0.0
    0.0  sy,  0.0  0.0
    0.0  0.0  sz,  0.0
    0.0  0.0  0.0  1.0)))

(defn perspective
  [fovy aspect near far]
  (let [f      (/ (Math/tan (* 0.5 fovy)))
        nf     (/ (- near far))]
    (mat/mat
     (/ f aspect) 0.0 0.0 0.0
     0.0 f 0.0 0.0
     0.0 0.0 (* (+ near far) nf) -1.0
     0.0 0.0 (* 2.0 near far nf) 0.0)))

(def eps 1.0e-9)

(defn ortho-normal
  ([[a b c]] (v/normalize (v/cross (v/sub b a) (v/sub c a))))
  ([a b] (v/normalize (v/cross a b)))
  ([a b c] (v/normalize (v/cross (v/sub b a) (v/sub c a)))))

(defn look-at
  [eye target up]
  (let [dir (v/sub eye target)]
    (if (< (v/magsq dir) eps)
      (mat/eye 4)
      (let [[zx zy zz :as z] (v/normalize dir)
            [xx xy xz :as x] (ortho-normal up z)
            [yx yy yz :as y] (ortho-normal z x)]
        (mat/mat
         xx yx zx 0.0
         xy yy zy 0.0
         xz yz zz 0.0
         (- (v/dot x eye))
         (- (v/dot y eye))
         (- (v/dot z eye))
         1.0)))))

(defn quat->mat4 [[^double w ^double x ^double y ^double z]]
  (let [x2 (+ x x)
        y2 (+ y y)
        z2 (+ z z)
        xx (* x x2)
        xy (* x y2)
        xz (* x z2)
        yy (* y y2)
        yz (* y z2)
        zz (* z z2)
        wx (* w x2)
        wy (* w y2)
        wz (* w z2)]
    (mat/mat
     (- 1.0 (+ yy zz)) (+ xy wz) (- xz wy) 0.0
     (- xy wz) (- 1.0 (+ xx zz)) (+ yz wx) 0.0
     (+ xz wy) (- yz wx) (- 1.0 (+ xx yy)) 0.0
     0.0 0.0 0.0 1.0)))
