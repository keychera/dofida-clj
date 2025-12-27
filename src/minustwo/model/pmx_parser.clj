(ns minustwo.model.pmx-parser
  (:require
   [clojure.java.io :as io]
   [gloss.core :as g :refer [finite-frame repeated string]]
   [gloss.core.codecs :refer [enum header ordered-map]]
   [gloss.core.structure :refer [compile-frame]]
   [gloss.io :as gio]))

;; PMX spec
;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f/b3944390bd935f48ddf72dd2fc058ffe87c10708

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

;; _f is a convention, indicating a gloss frame
(def empty-frame (compile-frame []))
(def ^:const int_f :int32-le)
(def ^:const float_f :float32-le)
(def ^:const vec2_f [float_f float_f])
(def ^:const vec3_f [float_f float_f float_f])
(def ^:const vec4_f [float_f float_f float_f float_f])
;; if index enum is the same keyword as actual types, weird error happens, dont do that
(def ^:const index_f (enum :byte {'byte 2r001 'uint16-le 2r010 'uint32-le 2r100}))

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

(declare create-vertex-frame create-material-frame create-bone-frame)

(defn body-codec-fn
  [{:keys [magic
           encoding
           additional-uv-count
           vertex-index-size
           texture-index-size
           bone-index-size] :as root-header}]
  (when (not= magic "PMX ") (throw (ex-info (str "no PMX magic detected! magic = '" magic "'") {})))
  (let [text_f        (finite-frame int_f (string encoding))
        vertex-idx_f  (keyword vertex-index-size)
        bone-idx_f    (keyword bone-index-size)
        texture-idx_f (keyword texture-index-size)]
    (ordered-map
     :root-header    root-header
     :local-name     text_f
     :global-name    text_f
     :local-comment  text_f
     :global-comment text_f
     :vertices       (repeated (create-vertex-frame additional-uv-count bone-idx_f) :prefix int_f)
     :faces          (repeated vertex-idx_f :prefix int_f)
     :textures       (repeated text_f :prefix int_f)
     :materials      (repeated (create-material-frame text_f texture-idx_f) :prefix int_f)
     :bones          (repeated (create-bone-frame text_f bone-idx_f) :prefix int_f))))

;; vertices

(def weight-type (enum :byte :BDEF1 :BDEF2 :BDEF4 :SDEF #_:QDEF))
(defn create-weight-codec [bone-idx_f]
  (header weight-type
          (fn [weight-type]
            (apply ordered-map
                   (concat
                    [:weight-type weight-type]
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

;; materials

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
     :face-count        int_f)))

;; bones

;; this one is better regarding the bone flags part
;; https://gist.github.com/hakanai/d442724ac3728c1b50e50f7d1df65e1b#file-pmx21-md
(def ^:const connection             0x0001)
(def ^:const rotatable?             0x0002)
(def ^:const translatable?          0x0004)
(def ^:const visible?               0x0008)
(def ^:const enabled?               0x0010)
(def ^:const IK                     0x0020)
(def ^:const parent-local?          0x0040) ;; https://gist.github.com/felixjones/f8a06bd48f9da9a4539f?permalink_comment_id=4559705#gistcomment-4559705
(def ^:const inherit-scale          0x0080) ;; our interpretation
(def ^:const inherit-rotation       0x0100)
(def ^:const inherit-translation    0x0200)
(def ^:const fixed-axis             0x0400)
(def ^:const local-axis             0x0800)
(def ^:const after-physics-deform   0x1000)
(def ^:const external-parent-deform 0x2000)
(def has-flag? (comp not zero? bit-and))

(defn create-angle-limits-frame []
  (ordered-map :lower vec3_f :upper vec3_f))

(defn create-IK-link-frame [bone-idx_f]
  (ordered-map
   :IK-bone-idx  bone-idx_f
   :angle-limits (header (enum :byte false true)
                         (fn [limit-angle?]
                           (if limit-angle?
                             (create-angle-limits-frame)
                             empty-frame))
                         identity)))

(defn create-bone-codec [bone-idx_f]
  (header
   (compile-frame :int16-le)
   (fn [bone-flag]
     (let [f bone-flag]
       ;; debugging purposes, I wonder if gloss' debuggability can be improved
       #_(println bone-flag
                [:conn          (has-flag? f connection)
                 :rotatable?    (has-flag? f rotatable?)
                 :translatable? (has-flag? f translatable?)
                 :visible?      (has-flag? f visible?)
                 :enabled?      (has-flag? f enabled?)
                 :inherit       (or (has-flag? f inherit-rotation)
                                    (has-flag? f inherit-translation))
                 :fixed         (has-flag? f fixed-axis)
                 :local-axis    (has-flag? f local-axis)
                 :after-physics (has-flag? f after-physics-deform)
                 :parent-deform (has-flag? f external-parent-deform)
                 :IK            (has-flag? f IK)])
       (apply ordered-map
              (cond-> [:rotatable?    (has-flag? f rotatable?)
                       :translatable? (has-flag? f translatable?)
                       :visible?      (has-flag? f visible?)
                       :enabled?      (has-flag? f enabled?)]
                ;; the order is clearer here https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt
                ;; 接続先:0 の場合
                (has-flag? f connection)
                (conj :connection bone-idx_f)

                ;; 接続先:1 の場合
                (not (has-flag? f connection))
                (conj :position-offset vec3_f)

                ;; 回転付与:1 または 移動付与:1 の場合
                (or (has-flag? f inherit-rotation)
                    (has-flag? f inherit-translation))
                (conj :parent-idx       bone-idx_f
                      :parent-influence float_f)

                ;; 軸固定:1 の場合
                (has-flag? f fixed-axis)
                (conj :axis-vector vec3_f)

                ;; ローカル軸:1 の場合
                (has-flag? f local-axis)
                (conj :x-axis-vector vec3_f
                      :z-axis-vector vec3_f)

                ;; 外部親変形:1 の場合
                (has-flag? f external-parent-deform)
                (conj :key-value int_f)

                (has-flag? f IK)
                (conj :IK-bone-idx bone-idx_f
                      :iterations  int_f
                      :limit-angle float_f
                      :links       (repeated (create-IK-link-frame bone-idx_f) :prefix int_f))))))
   identity))

(defn create-bone-frame [text_f bone-idx_f]
  (let [bone-codec (create-bone-codec bone-idx_f)]
    (ordered-map
     :local-name      text_f
     :global-name     text_f
     :position        vec3_f
     :parent-bone-idx bone-idx_f
     :transform-level int_f
     :bone-data       bone-codec)))

(comment
  (time
   (let [model-path
         #_"public/assets/models/Alicia_blade.pmx"
         "public/assets/models/SilverWolf/SilverWolf.pmx"
         pmx-byte   (file->bytes (io/file (io/resource model-path)))
         pmx-codec  (header header-codec body-codec-fn identity)
         result     (gio/decode pmx-codec pmx-byte false)]
     (-> result
         (update :vertices (juxt count #(into [] (take 2) %)))
         (update :faces (juxt count identity))
         (update :materials (juxt count #(into [] (take 2) %)))
         (update :bones (juxt count #(into [] (comp (take 10)) %))))))

  *e

  :-)

;; other refs;
;; https://gist.github.com/ulrikdamm/8274171
;; https://github.com/hirakuni45/glfw3_app/blob/master/glfw3_app/docs/PMX_spec.txt