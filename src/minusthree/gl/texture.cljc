(ns minusthree.gl.texture
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.macros :refer [s-> vars->map]]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.utils :as utils]
   [minusthree.engine.world :as world]
   [minustwo.gl.constants :refer [GL_CLAMP_TO_EDGE GL_COLOR_ATTACHMENT0
                                  GL_DEPTH_ATTACHMENT GL_DEPTH_COMPONENT24
                                  GL_FRAMEBUFFER GL_FRAMEBUFFER_COMPLETE
                                  GL_NEAREST GL_RENDERBUFFER GL_RGBA
                                  GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TEXTURE_MAG_FILTER GL_TEXTURE_MIN_FILTER
                                  GL_TEXTURE_WRAP_S GL_TEXTURE_WRAP_T
                                  GL_UNSIGNED_BYTE]]
   [odoyle.rules :as o]))

(s/def ::uri-to-load string?)
(s/def ::tex-unit int?)
(s/def ::for ::world/esse-id)

(s/def ::image-data some?)
(s/def ::width int?)
(s/def ::height int?)
(s/def ::image (s/keys :req-un [::image-data ::width ::height ::tex-unit]))

(s/def ::gl-texture some?)
(s/def ::tex-name some?)
(s/def ::texture (s/keys :req-un [::gl-texture ::tex-unit]))
(s/def ::data (s/map-of ::tex-name ::texture))
(s/def ::count number?)

(defn load-image [uri]
  (cond
    (or (str/ends-with? uri ".png") (str/ends-with? uri ".bmp"))
    (utils/get-image
     uri
     (fn on-image-load [{:keys [data width height]}]
       {:image-data data :width width :height height}))

    :else
    (throw (ex-info (str "uri not supported: " uri) {}))))

(defn cast-texture-spell [ctx data width height tex-unit]
  (let [texture (gl ctx #?(:clj genTextures :cljs createTexture))]
    (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
    (gl ctx bindTexture GL_TEXTURE_2D texture)
    #?(:cljs (gl ctx pixelStorei (gl ctx UNPACK_FLIP_Y_WEBGL) false))

    (gl ctx texImage2D GL_TEXTURE_2D
        #_:mip-level    0
        #_:internal-fmt GL_RGBA
        (int width)
        (int height)
        #_:border       0
        #_:src-fmt      GL_RGBA
        #_:src-type     GL_UNSIGNED_BYTE
        data)

    (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_MAG_FILTER GL_NEAREST)
    (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_MIN_FILTER GL_NEAREST)
    texture))

(defn cast-fbo-spell
  ([ctx width height tex-unit] (cast-fbo-spell ctx width height tex-unit {}))
  ([ctx width height tex-unit {:keys [color-attachment] :as conf
                               :or {color-attachment GL_COLOR_ATTACHMENT0}}]
   (let [fbo       (gl ctx #?(:clj genFramebuffers :cljs createFramebuffer))
         _         (gl ctx bindFramebuffer GL_FRAMEBUFFER fbo)
         fbo-tex   (gl ctx #?(:clj genTextures :cljs createTexture))
         depth-buf (gl ctx #?(:clj genRenderbuffers :cljs createRenderbuffer))]

    ;; bind, do stuff, unbind, hmm hmm
    ;; attach texture
     (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
     (gl ctx bindTexture GL_TEXTURE_2D fbo-tex)
     (gl ctx texImage2D GL_TEXTURE_2D
         #_:mip-level    0
         #_:internal-fmt GL_RGBA
         (int width)
         (int height)
         #_:border       0
         #_:src-fmt      GL_RGBA
         #_:src-type     GL_UNSIGNED_BYTE
         #?(:clj 0 :cljs nil))
     (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_MAG_FILTER GL_NEAREST)
     (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_MIN_FILTER GL_NEAREST)
     (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_WRAP_S GL_CLAMP_TO_EDGE)
     (gl ctx texParameteri GL_TEXTURE_2D GL_TEXTURE_WRAP_T GL_CLAMP_TO_EDGE)
     (gl ctx bindTexture GL_TEXTURE_2D #?(:clj 0 :cljs nil))
     (gl ctx framebufferTexture2D GL_FRAMEBUFFER color-attachment GL_TEXTURE_2D fbo-tex 0)

    ;; attach depth buffer, will parameterize later or never if not needed 
     (gl ctx bindRenderbuffer GL_RENDERBUFFER depth-buf);
     (gl ctx renderbufferStorage GL_RENDERBUFFER GL_DEPTH_COMPONENT24 width height);
     (gl ctx framebufferRenderbuffer GL_FRAMEBUFFER GL_DEPTH_ATTACHMENT GL_RENDERBUFFER depth-buf);

     (when (not= (gl ctx checkFramebufferStatus GL_FRAMEBUFFER) GL_FRAMEBUFFER_COMPLETE)
       (println "warning: framebuffer creation incomplete"))
     (gl ctx bindFramebuffer GL_FRAMEBUFFER #?(:clj 0 :cljs nil))

     (merge conf (vars->map fbo fbo-tex tex-unit width height)))))

(defn load-texture-fn [tex-name uri tex-unit]
  (fn []
    (let [image (assoc (load-image uri)
                       :tex-unit tex-unit)]
      [[tex-name ::image image]])))

(def rules
  (o/ruleset
   {::load-texture
    [:what
     [tex-name ::uri-to-load uri]
     [tex-name ::tex-unit tex-unit]
     :then
     (s-> session
          (o/retract tex-name ::uri-to-load)
          (o/retract tex-name ::tex-unit)
          (o/insert tex-name (loading/push (load-texture-fn tex-name uri tex-unit))))]

    ::cast-texture
    [:what
     [tex-name ::image data]
     :then
     (let [ctx nil
           {:keys [image-data width height tex-unit]} data
           texture (cast-texture-spell ctx image-data width height tex-unit)]
       (s-> session
            (o/retract tex-name ::image)
            (o/insert tex-name ::texture {:gl-texture texture :tex-unit tex-unit})))]

    ::aggregate
    [:what
     [tex-name ::texture texture]
     [tex-name ::for esse-id]
     [esse-id ::count tex-count]
     [esse-id ::data tex-data {:then false}]
     :then-finally
     ;; still, this part feels expensive
     (let [all-tex-facts   (o/query-all session ::aggregate)
           esse->tex-facts (group-by :esse-id all-tex-facts)]
       (s-> (reduce-kv
             (fn [s' esse-id tex-facts]
               (let [{:keys [tex-count tex-data]} (first tex-facts)
                     texname->texture (-> (group-by :tex-name tex-facts)
                                          (update-vals (comp :texture first)))]
                 (cond-> s'
                   (and (nil? (seq tex-data)) (= (count texname->texture) tex-count))
                   (o/insert esse-id ::data texname->texture))))
             session
             esse->tex-facts)))]}))

(def system
  {::world/rules #'rules})
