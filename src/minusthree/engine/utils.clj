(ns minusthree.engine.utils
  (:require
   [clojure.java.io :as io]
   [fastmath.matrix :as mat])
  (:import
   [java.nio.channels Channels ReadableByteChannel]
   [org.lwjgl.stb STBImage]
   [org.lwjgl.system MemoryStack MemoryUtil]))

(defn ^:vibe get-parent-path [path-str]
  (let [last-slash (max (.lastIndexOf path-str "/")
                        (.lastIndexOf path-str "\\"))]
    (if (pos? last-slash)
      (subs path-str 0 last-slash)
      "")))

(defn get-public-resource [res]
  (io/resource (str "public/" res)))

(defn resize-buffer [old-buf new-capacity]
  (MemoryUtil/memRealloc old-buf new-capacity))

 ;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/util/IOUtil.java#L40
(defn resource->ByteBuffer [resource-path initial-buf-size]
  (with-open [is  (io/input-stream (io/resource resource-path))
              rbc ^ReadableByteChannel (Channels/newChannel is)]
    (let [buf (loop [buffer (MemoryUtil/memAlloc initial-buf-size)]
                (if (== (.read rbc buffer) -1)
                  buffer
                  (recur
                   (if (== (.remaining buffer) 0)
                     (resize-buffer buffer (int (* (.capacity buffer) 1.5)))
                     buffer))))]
      (.flip buf)
      (MemoryUtil/memSlice buf))))

(defn get-image [fname callback]
  (let [image-buf (resource->ByteBuffer (str "public/" fname) (* 8 1024))]
    (with-open [stack (MemoryStack/stackPush)]
      (let [*w    (.mallocInt stack 1)
            *h    (.mallocInt stack 1)
            *comp (.mallocInt stack 1)
            image (STBImage/stbi_load_from_memory image-buf *w *h *comp STBImage/STBI_rgb_alpha)]
        (when (nil? image)
          (throw (ex-info (str "get-image failed! reason: " (STBImage/stbi_failure_reason)) {})))
        (callback {:data image :width (.get *w) :height (.get *h)})))))

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
