(ns minusthree.engine.utils
  (:require
   [clojure.java.io :as io]
   [fastmath.matrix :as mat])
  (:import
   [java.nio ByteBuffer]
   [org.lwjgl.stb STBImage]
   [org.lwjgl.system MemoryUtil]))

(defn ^:vibe get-parent-path [path-str]
  (let [last-slash (max (.lastIndexOf path-str "/")
                        (.lastIndexOf path-str "\\"))]
    (if (pos? last-slash)
      (subs path-str 0 last-slash)
      "")))

(defn get-public-resource [res]
  (io/resource (str "public/" res)))

(defn get-image [fname callback]
  (let [^bytes barray (with-open [is  (io/input-stream (get-public-resource fname))
                                  out (java.io.ByteArrayOutputStream.)]
                        (io/copy is out)
                        (.toByteArray out))
        *width        (MemoryUtil/memAllocInt 1)
        *height       (MemoryUtil/memAllocInt 1)
        *components   (MemoryUtil/memAllocInt 1)
        direct-buffer (doto ^ByteBuffer (ByteBuffer/allocateDirect (alength barray)) (.put barray) (.flip))
        _             (STBImage/stbi_set_flip_vertically_on_load false)
        decoded-image (STBImage/stbi_load_from_memory
                       direct-buffer *width *height *components
                       STBImage/STBI_rgb_alpha)
        image         {:data decoded-image :width (.get *width) :height (.get *height)}]
    (MemoryUtil/memFree *width)
    (MemoryUtil/memFree *height)
    (MemoryUtil/memFree *components)
    (callback image)))

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
