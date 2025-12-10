(ns engine.math
  (:require
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [thi.ng.geom.core :as g]))

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

;; https://github.khronos.org/glTF-Tutorials/gltfTutorial/gltfTutorial_007_Animations.html#linear
(defn quat-mix
  [^thi.ng.geom.quaternion.Quat4 q1
   ^thi.ng.geom.quaternion.Quat4 q2
   ^double t]
  (let [dot-p (m/dot q1 q2)
        q2    (if (< dot-p 0.0) (g/scale q2 -1.0) q2)]
    ;; not handling quats that are too close to each other yet
    (m/mix q1 q2 t)))