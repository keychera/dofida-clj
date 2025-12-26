(ns minustwo.model.pmx-parser
  (:require
   [clojure.java.io :as io]
   [gloss.core :as g :refer [finite-frame repeated string]]
   [gloss.core.codecs :refer [enum header ordered-map]]
   [gloss.io :as gio]))

;; PMX spec
;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f/b3944390bd935f48ddf72dd2fc058ffe87c10708

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

;; _f is a convention, indicating a gloss frame
(def ^:const float_f :float32-le)
(def ^:const vec2_f [float_f float_f])
(def ^:const vec3_f [float_f float_f float_f])
(def ^:const vec4_f [float_f float_f float_f float_f])
;; if index enum is the same keyword as actual types, weird error happens, dont do that
(def ^:const index_f (enum :byte 'byte 'ubyte 'int16-le 'uint16-le 'int32-le))

(def header-codec
  (ordered-map
   :magic               (string :utf-8 :length 4)
   :version             float_f
   :header-size         :byte
   :encoding            (enum :byte :utf-16LE :utf-8)
   :additional-uv-count :byte
   :vertex-index-size   index_f
   :texture-index-size  index_f
   :material-index-size index_f
   :bone-index-size     index_f
   :morph-index-size    index_f
   :rigid-index-size    index_f))

(declare create-vertex-frame create-material-frame)

(defn body-codec-fn
  [{:keys [magic
           encoding
           additional-uv-count
           vertex-index-size
           texture-index-size
           bone-index-size] :as root-header}]
  (when (not= magic "PMX ") (throw (ex-info (str "no PMX magic detected! magic = '" magic "'") {})))
  (let [text_f        (finite-frame :int32-le (string encoding))
        vertex-idx_f  (keyword vertex-index-size)
        bone-idx_f    (keyword bone-index-size)
        texture-idx_f (keyword texture-index-size)]
    (ordered-map
     :root-header    root-header
     :local-name     text_f
     :global-name    text_f
     :local-comment  text_f
     :global-comment text_f
     :vertices       (repeated (create-vertex-frame additional-uv-count bone-idx_f) :prefix :int32-le)
     :faces          (repeated vertex-idx_f :prefix :int32-le)
     :textures       (repeated text_f :prefix :int32-le)
     :materials      (repeated (create-material-frame text_f texture-idx_f) :prefix :int32-le))))

(def weight-type (enum :byte :BDEF1 :BDEF2 :BDEF4 :SDEF #_:QDEF))
(defn create-weight-codec [bone-idx_f]
  (header weight-type
          (fn [weight-type]
            (apply ordered-map
                   (concat
                    [:type weight-type]
                    (case weight-type
                      :BDEF1 [:bone-index1 bone-idx_f]
                      :BDEF2 [:bone-index1 bone-idx_f
                              :bone-index2 bone-idx_f
                              :weight1 float_f]
                      :BDEF4 [:bone-index1 bone-idx_f
                              :bone-index2 bone-idx_f
                              :bone-index3 bone-idx_f
                              :bone-index4 bone-idx_f
                              :weight1 float_f
                              :weight2 float_f
                              :weight3 float_f
                              :weight4 float_f]
                      :SDEF  [:bone-index1 bone-idx_f
                              :bone-index2 bone-idx_f
                              :weight1 float_f
                              :C       vec3_f
                              :R0      vec3_f
                              :R1      vec3_f]))))
          identity))

(defn create-vertex-frame [additional-uv-count bone-idx_f]
  (let [weight-codec  (create-weight-codec bone-idx_f)]
    (ordered-map
     :position      vec3_f
     :normal        vec3_f
     :uv            vec2_f
     :additional-uv (into [] (take additional-uv-count (repeat vec4_f)))
     :weight        weight-codec
     :edge-scale    float_f)))

(defn create-toon-codec [texture-idx_f]
  (header (enum :byte :texture :inbuilt)
          (fn [toon-flag]
            (apply ordered-map
                   [:toon-flag  toon-flag
                    :toon-index (case toon-flag
                                  :texture texture-idx_f
                                  :inbuilt :byte)]))
          identity))

(defn create-material-frame [text_f texture-idx_f]
  (let [toon-codec (create-toon-codec texture-idx_f)]
    (ordered-map
     :local-name        text_f
     :global-name       text_f
     :diffuse-color     vec4_f
     :specular-color    vec3_f
     :specularity       float_f
     :ambient-color     vec3_f
     :drawing-mode      :byte
     :edge-color        vec4_f
     :edge-size         float_f
     :texture-index     texture-idx_f
     :env-texture-index texture-idx_f
     :env-mode          :byte
     :toon              toon-codec
     :memo              text_f
     :face-count        float_f)))

(comment
  (time
   (let [model-path "public/assets/models/SilverWolf/SilverWolf.pmx"
         pmx-byte   (file->bytes (io/file (io/resource model-path)))
         pmx-codec  (header header-codec body-codec-fn identity)
         result     (gio/decode pmx-codec pmx-byte false)]
     (-> result
         (update :vertices (juxt count #(into [] (take 3) %)))
         (update :faces (juxt count #(into [] (take 9) %))))))

  *e

  :-)

;; other refs;
;; https://gist.github.com/ulrikdamm/8274171
;; https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt