(ns minustwo.model.pmx-parser
  (:require
   [clojure.java.io :as io]
   [gloss.core :as g :refer [finite-frame repeated string]]
   [gloss.core.codecs :refer [enum header ordered-map]]
   [gloss.io :as gio]))


;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f/b3944390bd935f48ddf72dd2fc058ffe87c10708

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(def header-codec
  (ordered-map
   :magic               (string :utf-8 :length 4)
   :version             :float32-le
   :header-size         :byte
   :encoding            (enum :byte :utf-16LE :utf-8)
   :additional-uv-count :byte
   :vertex-index-size   :byte
   :texture-index-size  :byte
   :material-index-size :byte
   :bone-index-size     (enum :byte 'byte 'ubyte 'int16-le 'uint16-le 'int32-le) ;; if this is the same symbol as actual types, weird error happens, dont do that
   :morph-index-size    :byte
   :rigid-index-size    :byte))

(def vec2_t [:float32-le :float32-le])
(def vec3_t [:float32-le :float32-le :float32-le])
(def vec4_t [:float32-le :float32-le :float32-le :float32-le])

(declare create-vertex-frame)

(defn body-codec-fn [{:keys [encoding] :as root-header}]
  (let [text_t (finite-frame :int32-le (string encoding))]
    (ordered-map
     :root-header    root-header
     :local-name     text_t
     :global-name    text_t
     :local-comment  text_t
     :global-comment text_t
     :vertices       (repeated (create-vertex-frame root-header) :prefix :int32-le))))

(def weight-type (enum :byte :BDEF1 :BDEF2 :BDEF4 :SDEF #_:QDEF))
(defn create-weight-codec [idx-size]
  (header weight-type
          (fn [weight-type]
            (apply ordered-map
                   (concat
                    [:type weight-type]
                    (case weight-type
                      :BDEF1 [:bone-index1 idx-size]
                      :BDEF2 [:bone-index1 idx-size
                              :bone-index2 idx-size
                              :weight1 :float32-le]
                      :BDEF4 [:bone-index1 idx-size
                              :bone-index2 idx-size
                              :bone-index3 idx-size
                              :bone-index4 idx-size
                              :weight1 :float32-le
                              :weight2 :float32-le
                              :weight3 :float32-le
                              :weight4 :float32-le]
                      :SDEF  [:bone-index1 idx-size
                              :bone-index2 idx-size
                              :weight1 :float32-le
                              :C       vec3_t
                              :R0      vec3_t
                              :R1      vec3_t]))))
          identity))

(defn create-vertex-frame [{:keys [additional-uv-count bone-index-size] :as _root-header}]
  (let [idx-size     (keyword bone-index-size)
        weight-codec (create-weight-codec idx-size)]
    (ordered-map
     :position      vec3_t
     :normal        vec3_t
     :uv            vec2_t
     :additional-uv (into [] (take additional-uv-count (repeat vec4_t)))
     :weight        weight-codec
     :edge-scale    :float32-le)))

(comment
  (time
   (let [model-path "public/assets/models/SilverWolf/SilverWolf.pmx"
         pmx-byte   (file->bytes (io/file (io/resource model-path)))
         pmx-codec  (header header-codec body-codec-fn identity)
         result     (gio/decode pmx-codec pmx-byte false)]
     (-> result
         (update :vertices (fn [verts] [(count verts) (into [] (take 3) verts)])))))

  *e

  :-)

;; other refs;
;; https://gist.github.com/ulrikdamm/8274171
;; https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt