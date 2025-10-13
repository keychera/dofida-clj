(ns engine.utils
  (:require #?@(:clj [[clojure.java.io :as io]
                      [clojure.string :as str]])
            [clojure.edn :as edn])
  #?(:cljs (:require-macros [engine.utils :refer [load-model-on-compile]]))
  #?(:clj (:import [java.nio ByteBuffer]
                   [org.lwjgl.glfw GLFW]
                   [org.lwjgl.system MemoryUtil]
                   [org.lwjgl.stb STBImage])))

(defn get-image [fname callback]
  #?(:clj  (let [is (io/input-stream (io/resource (str "public/" fname)))
                 ^bytes barray (with-open [out (java.io.ByteArrayOutputStream.)]
                                 (io/copy is out)
                                 (.toByteArray out))
                 *width (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 *components (MemoryUtil/memAllocInt 1)
                 direct-buffer (doto ^ByteBuffer (ByteBuffer/allocateDirect (alength barray))
                                 (.put barray)
                                 (.flip))
                 decoded-image (STBImage/stbi_load_from_memory
                                direct-buffer *width *height *components
                                STBImage/STBI_rgb_alpha)
                 image {:data decoded-image
                        :width (.get *width)
                        :height (.get *height)}]
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
                 _ (GLFW/glfwGetFramebufferSize ^long (:context game) *width *height)
                 w (.get *width)
                 h (.get *height)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             [w h])
     :cljs [(-> game :context .-canvas .-clientWidth)
            (-> game :context .-canvas .-clientHeight)]))

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

(defn model->vertex-data
  "call this with (load-model-on-compile).
   they need to be called separately because load-model-on-compile will
   produce string data of the model at compile time 
   
     e.g.
   
     (-> (utils/load-model-on-compile \"assets/defaultcube.obj\")
         (utils/model->vertex-data))"
  [load-model-here]
  (let [grouped       (-> load-model-here
                          (edn/read-string))
        faces         (mapcat identity (get grouped "f"))
        all-vertices  (get grouped "v")
        all-uvs       (get grouped "vt")
        all-normals   (get grouped "vn")
        vertex-count  (count faces)]
    (loop [i 0
           [[v-i uv-i n-i] & remains] faces
           vertices   (#?(:clj float-array :cljs #(js/Float32Array. %)) (* vertex-count 3))
           uvs        (#?(:clj float-array :cljs #(js/Float32Array. %)) (* vertex-count 2))
           normals    (#?(:clj float-array :cljs #(js/Float32Array. %)) (* vertex-count 3))]
      (if (some? v-i)
        (let [[v1 v2 v3] (nth all-vertices (dec v-i))
              [uv1 uv2]  (nth all-uvs (dec uv-i))
              [n1 n2 n3] (nth all-normals (dec n-i))]
          (aset vertices (* i 3) v1)
          (aset vertices (+ (* i 3) 1) v2)
          (aset vertices (+ (* i 3) 2) v3)
          (aset uvs (* i 2)  uv1)
          (aset uvs (+ (* i 2) 1) uv2)
          (aset normals (* i 3) n1)
          (aset normals (+ (* i 3) 1) n2)
          (aset normals (+ (* i 3) 2) n3)
          (recur (inc i) remains vertices uvs normals))
        [vertices uvs normals]))))
