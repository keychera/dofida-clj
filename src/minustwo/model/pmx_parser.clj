(ns minustwo.model.pmx-parser
  (:require
   [clojure.java.io :as io]
   [gloss.core :as g :refer [finite-frame string]]
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
   :additional-uv       :byte
   :vertex-index-size   :byte
   :texture-index-size  :byte
   :material-index-size :byte
   :bone-index-size     :byte
   :morph-index-size    :byte
   :rigid-index-size    :byte))

(defn body-codec-fn [{:keys [encoding] :as header-data}]
  (let [t_text (finite-frame :int32-le (string encoding))]
    (ordered-map
     :header-data    header-data
     :local-name     t_text
     :global-name    t_text
     :local-comment  t_text
     :global-comment t_text
     :vert-count     :int32-le)))

(let [model-path   "public/assets/models/SilverWolf/SilverWolf.pmx"
      pmx-byte     (file->bytes (io/file (io/resource model-path)))
      pmx-codec    (header header-codec body-codec-fn identity)]
  (gio/decode pmx-codec pmx-byte false))

;; other refs;
;; https://gist.github.com/ulrikdamm/8274171
;; https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt