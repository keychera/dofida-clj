(ns minusthree.engine.utils
  (:require
   [fastmath.matrix :as mat]))

(defn f32s->get-mat4
  "Return a 4x4 matrix from a float-array / Float32Array `f32s`.
  `idx` is the start index (optional, defaults to 0)."
  ([^java.nio.FloatBuffer fb idx]
   (let [i (* (or idx 0) 16)]
     (mat/mat
      (.get fb i)       (.get fb (+ i 1))  (.get fb (+ i 2))  (.get fb (+ i 3))
      (.get fb (+ i 4)) (.get fb (+ i 5))  (.get fb (+ i 6))  (.get fb (+ i 7))
      (.get fb (+ i 8)) (.get fb (+ i 9))  (.get fb (+ i 10)) (.get fb (+ i 11))
      (.get fb (+ i 12)) (.get fb (+ i 13)) (.get fb (+ i 14)) (.get fb (+ i 15))))))
