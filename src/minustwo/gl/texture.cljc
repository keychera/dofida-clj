(ns minustwo.gl.texture
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minustwo.gl.constants :refer [GL_COLOR_ATTACHMENT0 GL_FRAMEBUFFER
                                  GL_FRAMEBUFFER_COMPLETE GL_NEAREST GL_RGBA
                                  GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TEXTURE_MAG_FILTER GL_TEXTURE_MIN_FILTER
                                  GL_UNSIGNED_BYTE]]
   [minustwo.gl.gl-system :as gl-system]
   [engine.utils :as utils #?@(:cljs [:refer [data-uri->ImageBitmap]])]
   [odoyle.rules :as o]))

(defn texture-spell [ctx data width height tex-unit]
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
    (vars->map texture tex-unit)))

(defn fbo-spell [ctx width height tex-unit]
  (let [frame-buf (gl ctx #?(:clj genFramebuffers :cljs createFramebuffer))
        _         (gl ctx bindFramebuffer GL_FRAMEBUFFER frame-buf)
        fbo-tex   (gl ctx #?(:clj genTextures :cljs createTexture))]

      ;; bind, do stuff, unbind, hmm hmm
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
    (gl ctx bindTexture GL_TEXTURE_2D #?(:clj 0 :cljs nil))

    (gl ctx framebufferTexture2D GL_FRAMEBUFFER GL_COLOR_ATTACHMENT0 GL_TEXTURE_2D fbo-tex 0)

    (when (not= (gl ctx checkFramebufferStatus GL_FRAMEBUFFER) GL_FRAMEBUFFER_COMPLETE)
      (println "warning: framebuffer creation incomplete"))
    (gl ctx bindFramebuffer GL_FRAMEBUFFER #?(:clj 0 :cljs nil))

    (vars->map frame-buf fbo-tex tex-unit)))

(s/def ::fbo map?)

(s/def ::texture any?)
(s/def ::tex-unit int?)
(s/def ::data (s/keys :req-un [::texture ::tex-unit]))

(s/def ::uri-to-load string?)
(s/def ::loaded? #{:pending :loading true})

(defonce db* (atom {}))

(def rules
  (o/ruleset
   {::uri-to-load
    [:what
     [tex-name ::uri-to-load uri]
     [tex-name ::tex-unit tex-unit]]}))

(def system
  {::world/rules rules})

(defn load-texture->world*
  [textures-to-load world*]
  (let [ctx (:ctx (utils/query-one @world* ::gl-system/data))]
    (doseq [to-load textures-to-load]
      (let [tex-name (:tex-name to-load)
            uri      (:uri to-load)
            tex-unit (:tex-unit to-load)]
        (println "[texture] loading texture" tex-name)
        (swap! world* #(-> %
                           (o/retract tex-name ::uri-to-load)
                           (o/insert tex-name ::loaded? :loading)))
        (cond
          (str/starts-with? uri "data:")
          #?(:clj  (println "[texture] no data-uri handling yet in JVM")
             :cljs (data-uri->ImageBitmap
                    uri
                    (fn [{:keys [bitmap width height]}]
                      (let [tex-data (texture-spell ctx bitmap width height tex-unit)]
                        (println "[texture] loaded" tex-name tex-data)
                        (swap! db* assoc tex-name tex-data)
                        (swap! world* o/insert tex-name {::loaded? true})))))

          (str/ends-with? uri ".png")
          (utils/get-image
           uri
           (fn on-image-load [{:keys [data width height]}]
             (let [tex-data (texture-spell ctx data width height tex-unit)]
               (swap! db* assoc tex-name (s/conform ::data tex-data))
               (swap! world* o/insert tex-name {::loaded? true}))))

          :else
          (println uri "not supported"))))))
