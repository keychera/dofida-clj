(ns engine.utils
  (:require
   #?@(:clj [[clojure.java.io :as io]
             [clojure.string :as str]
             [cheshire.core :as json]])
   [clojure.edn :as edn]
   [engine.macros :refer [vars->map]]
   [engine.sugar :refer [f32-arr]]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat])
  #?(:cljs (:require-macros [engine.utils :refer [load-model-on-compile]]))
  #?(:clj (:import
           [java.nio ByteBuffer]
           [org.lwjgl.glfw GLFW]
           [org.lwjgl.stb STBImage]
           [org.lwjgl.system MemoryUtil])))

#?(:clj
   (defn ^:vibe get-parent-path [path-str]
     (let [last-slash (max (.lastIndexOf path-str "/")
                           (.lastIndexOf path-str "\\"))]
       (if (pos? last-slash)
         (subs path-str 0 last-slash)
         ""))))

#?(:clj (defn get-public-resource [res]
          (io/resource (str "public/" res))))

(defn get-image [fname callback]
  #?(:clj  (let [^bytes barray (with-open [is  (io/input-stream (get-public-resource fname))
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
             (callback image))
     :cljs (let [image (js/Image.)]
             (doto image
               (-> .-src (set! fname))
               (-> .-onload (set! #(callback {:data image
                                              :width image.width
                                              :height image.height})))))))

(defn get-size [game]
  #?(:clj  (let [*width (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 _ (GLFW/glfwGetFramebufferSize ^long (-> game :glfw-window :handle) *width *height)
                 w (.get *width)
                 h (.get *height)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             [w h])
     :cljs [(-> game :webgl-context .-canvas .-clientWidth)
            (-> game :webgl-context .-canvas .-clientHeight)]))

#?(:cljs
   (defn make-limited-logger [limit]
     (let [counter (atom 0)]
       (fn [err & args]
         (let [messages (apply str args)]
           (when (< @counter limit)
             (js/console.error (.-stack err))
             (swap! counter inc))
           (when (= @counter limit)
             (println "[SUPRESSED]" messages)
             (swap! counter inc)))))))

#?(:cljs (def log-limited (make-limited-logger 8)))

#?(:clj
   (defn parse-obj-lines [[_ word unparsed-args :as _regex-result]]
     (condp contains? word
       #{"v" "vn" "vt"} (apply vector word (map Float/parseFloat (str/split unparsed-args #" +")))
       #{"f"} (apply vector word (->> (str/split unparsed-args #" +")
                                      (mapv (fn [el] (mapv Integer/parseUnsignedInt (str/split el #"/"))))))
       nil)))

#?(:clj
   (defmacro load-model-on-compile [fname]
     (let [model-path   (str "public/" fname)
           model-res    (io/reader (io/resource model-path))
           parsed-lines (into []
                              (comp
                               (remove str/blank?)
                               (remove #(str/starts-with? % "#"))
                               (map #(re-find #"^(\w+) +(.*)" %))
                               (remove nil?)
                               (map parse-obj-lines)
                               (remove nil?))
                              (line-seq model-res))
           grouped       (-> (group-by first parsed-lines)
                             (update-vals (fn [v] (mapv rest v))))]
       (-> grouped pr-str))))

(defn triangulate [verts]
  (mapcat (fn [i]
            [(first verts)
             (nth verts i)
             (nth verts (inc i))])
          (range 1 (dec (count verts)))))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn get-json [fname callback]
  #?(:clj (let [json (json/parse-stream (io/reader (io/resource (str "public/" fname))) true)]
            (callback json))
     :cljs (.then
            (js/fetch fname)
            (fn [resp]
              (.then
               (.json resp)
               (fn [data] (callback (js->clj data :keywordize-keys true))))))))

(defn f32s->get-mat4
  "Return a 4x4 matrix from a float-array / Float32Array `f32s`.
  `idx` is the start index (optional, defaults to 0)."
  #?(:clj ([^java.nio.FloatBuffer fb idx]
           (let [i (* (or idx 0) 16)]
             (mat/matrix44
              (.get fb i)       (.get fb (+ i 1))  (.get fb (+ i 2))  (.get fb (+ i 3))
              (.get fb (+ i 4)) (.get fb (+ i 5))  (.get fb (+ i 6))  (.get fb (+ i 7))
              (.get fb (+ i 8)) (.get fb (+ i 9))  (.get fb (+ i 10)) (.get fb (+ i 11))
              (.get fb (+ i 12)) (.get fb (+ i 13)) (.get fb (+ i 14)) (.get fb (+ i 15)))))
     :cljs ([^floats f32s idx]
            (let [i (* (or idx 0) 16)]
              (mat/matrix44
               (aget f32s i)  (aget f32s (+ i 1))  (aget f32s (+ i 2))  (aget f32s (+ i 3))
               (aget f32s (+ i 4))  (aget f32s (+ i 5))  (aget f32s (+ i 6))  (aget f32s (+ i 7))
               (aget f32s (+ i 8))  (aget f32s (+ i 9))  (aget f32s (+ i 10)) (aget f32s (+ i 11))
               (aget f32s (+ i 12)) (aget f32s (+ i 13)) (aget f32s (+ i 14)) (aget f32s (+ i 15)))))))

(defn query-one [world rule-name]
  (first (o/query-all world rule-name)))

#?(:cljs
   (defn data-uri->header+Uint8Array [data-uri]
     (let [[header base64-str] (.split data-uri ",")]
       [header (js/Uint8Array.fromBase64 base64-str)])))

;; following this on loading image to bindTexture with blob
;; https://webglfundamentals.org/webgl/lessons/webgl-qna-how-to-load-images-in-the-background-with-no-jank.html
#?(:cljs
   (defn data-uri->ImageBitmap
     "parse data-uri and pass the resulting bitmap to callback.
      callback will receive {:keys [bitmap width height]}"
     [data-uri callback]
     (let [[header uint8-arr] (data-uri->header+Uint8Array data-uri)
           data-type          (second (re-matches #"data:(.*);.*" header))
           blob               (js/Blob. #js [uint8-arr] #js {:type data-type})]
       (.then (js/createImageBitmap blob)
              (fn [bitmap]
                (let [width  (.-width bitmap)
                      height (.-height bitmap)]
                  (println "blob:" data-type "count" (.-length uint8-arr))
                  (callback (vars->map bitmap width height))))))))

(comment

  ;; for debugging
  (def counter (atom 0))
  (defn print-mat4-until [args end]
    (let [cnt (swap! counter inc)]
      (when (< cnt end)
        (println (vec args)))))

  :-)