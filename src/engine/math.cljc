(ns engine.math
  (:require
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]))

;; math sugar

(defn scaling-mat
  ([s] (scaling-mat s s s))
  ([sx sy sz]
   (mat/matrix44
    sx,  0.0  0.0  0.0
    0.0  sy,  0.0  0.0
    0.0  0.0  sz,  0.0
    0.0  0.0  0.0  1.0)))

(defn translation-mat
  ([xyz]
   (let [[^float tx ^float ty ^float tz] xyz]
     (mat/matrix44
      1.0  0.0  0.0  0.0
      0.0  1.0  0.0  0.0
      0.0  0.0  1.0  0.0
      tx,  ty,  tz,  1.0)))
  ([tx ty tz]
   (mat/matrix44
    1.0  0.0  0.0  0.0
    0.0  1.0  0.0  0.0
    0.0  0.0  1.0  0.0
    tx,  ty,  tz,  1.0)))

(defn ^:vibe decompose-matrix44
  "Return {:translation (v/vec3) :rotation (q/quat) :scale (v/vec3)}"
  [^thi.ng.geom.matrix.Matrix44 m]
  (let [tx    (.-m30 m)
        ty    (.-m31 m)
        tz    (.-m32 m)
        ;; column lengths = scales
        sx    (Math/sqrt (+ (* (.-m00 m) (.-m00 m))
                            (* (.-m10 m) (.-m10 m))
                            (* (.-m20 m) (.-m20 m))))
        sy    (Math/sqrt (+ (* (.-m01 m) (.-m01 m))
                            (* (.-m11 m) (.-m11 m))
                            (* (.-m21 m) (.-m21 m))))
        sz    (Math/sqrt (+ (* (.-m02 m) (.-m02 m))
                            (* (.-m12 m) (.-m12 m))
                            (* (.-m22 m) (.-m22 m))))
        ;; avoid div by zero
        sx    (if (zero? sx) 1.0 sx)
        sy    (if (zero? sy) 1.0 sy)
        sz    (if (zero? sz) 1.0 sz)
        ;; normalized rotation columns
        r00   (/ (.-m00 m) sx)
        r10   (/ (.-m10 m) sx)
        r20   (/ (.-m20 m) sx)
        r01   (/ (.-m01 m) sy)
        r11   (/ (.-m11 m) sy)
        r21   (/ (.-m21 m) sy)
        r02   (/ (.-m02 m) sz)
        r12   (/ (.-m12 m) sz)
        r22   (/ (.-m22 m) sz)
        ;; build a Matrix44 for quat-from-matrix (no translation)
        rot-m (mat/matrix44
               r00 r01 r02 0.0
               r10 r11 r12 0.0
               r20 r21 r22 0.0
               0.0 0.0 0.0 1.0)]
    {:translation (v/vec3 tx ty tz)
     :rotation    (q/quat-from-matrix rot-m)
     :scale       (v/vec3 sx sy sz)}))
