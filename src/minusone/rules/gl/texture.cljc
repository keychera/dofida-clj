(ns minusone.rules.gl.texture
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:cljs [minusone.rules.model.assimp-js :refer [data-uri->ImageBitmap]])
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusone.rules.gl.gl :refer [GL_COLOR_ATTACHMENT0 GL_FRAMEBUFFER
                                 GL_FRAMEBUFFER_COMPLETE GL_NEAREST GL_RGBA
                                 GL_TEXTURE0 GL_TEXTURE_2D
                                 GL_TEXTURE_MAG_FILTER GL_TEXTURE_MIN_FILTER
                                 GL_UNSIGNED_BYTE]]
   [odoyle.rules :as o]))

(defn texture-incantation [game data width height tex-unit]
  (let [texture (gl game #?(:clj genTextures :cljs createTexture))]
    (gl game activeTexture (+ GL_TEXTURE0 tex-unit))
    (gl game bindTexture GL_TEXTURE_2D texture)
    #?(:cljs (gl game pixelStorei (gl game UNPACK_FLIP_Y_WEBGL) false))

    (gl game texImage2D GL_TEXTURE_2D
        #_:mip-level    0
        #_:internal-fmt GL_RGBA
        (int width)
        (int height)
        #_:border       0
        #_:src-fmt      GL_RGBA
        #_:src-type     GL_UNSIGNED_BYTE
        data)

    (gl game texParameteri GL_TEXTURE_2D GL_TEXTURE_MAG_FILTER GL_NEAREST)
    (gl game texParameteri GL_TEXTURE_2D GL_TEXTURE_MIN_FILTER GL_NEAREST)
    (vars->map texture tex-unit)))

(defn fbo-incantation [game width height tex-unit]
  (let [frame-buf (gl game #?(:clj genFramebuffers :cljs createFramebuffer))
        _         (gl game bindFramebuffer GL_FRAMEBUFFER frame-buf)
        fbo-tex   (gl game #?(:clj genTextures :cljs createTexture))]

      ;; bind, do stuff, unbind, hmm hmm
    (gl game activeTexture (+ GL_TEXTURE0 tex-unit))
    (gl game bindTexture GL_TEXTURE_2D fbo-tex)
    (gl game texImage2D GL_TEXTURE_2D
        #_:mip-level    0
        #_:internal-fmt GL_RGBA
        (int width)
        (int height)
        #_:border       0
        #_:src-fmt      GL_RGBA
        #_:src-type     GL_UNSIGNED_BYTE
        #?(:clj 0 :cljs nil))

    (gl game texParameteri GL_TEXTURE_2D GL_TEXTURE_MAG_FILTER GL_NEAREST)
    (gl game texParameteri GL_TEXTURE_2D GL_TEXTURE_MIN_FILTER GL_NEAREST)
    (gl game bindTexture GL_TEXTURE_2D #?(:clj 0 :cljs nil))

    (gl game framebufferTexture2D GL_FRAMEBUFFER GL_COLOR_ATTACHMENT0 GL_TEXTURE_2D fbo-tex 0)

    (when (not= (gl game checkFramebufferStatus GL_FRAMEBUFFER) GL_FRAMEBUFFER_COMPLETE)
      (println "warning: framebuffer creation incomplete"))
    (gl game bindFramebuffer GL_FRAMEBUFFER #?(:clj 0 :cljs nil))

    (vars->map frame-buf fbo-tex tex-unit)))

(s/def ::fbo map?)

(defmethod asset/process-asset ::asset/fbo
  [world* game asset-id {::keys [tex-unit]}]
  (let [[width height] (utils/get-size game)
        fbo            (fbo-incantation game width height tex-unit)]
    (swap! world* o/insert asset-id {::asset/loaded? true ::fbo fbo})))

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
  [textures-to-load world* game]
  (doseq [to-load textures-to-load]
    (let [tex-name (:tex-name to-load)
          uri      (:uri to-load)
          tex-unit (:tex-unit to-load)]
      (println "[minusone.texture] loading texture" tex-name)
      (swap! world* #(-> %
                         (o/retract tex-name ::uri-to-load)
                         (o/insert tex-name ::loaded? :loading)))
      (cond
        (str/starts-with? uri "data:")
        #?(:clj  (println "[minusone.texture] no data-uri handling yet in JVM")
           :cljs (data-uri->ImageBitmap
                  uri
                  (fn [{:keys [bitmap width height]}]
                    (let [tex-data (texture-incantation game bitmap width height tex-unit)]
                      (println "[minusone.texture] loaded" tex-name tex-data)
                      (swap! db* assoc tex-name tex-data)
                      (swap! world* o/insert tex-name {::loaded? true})))))

        (str/ends-with? uri ".png")
        (utils/get-image
         uri
         (fn on-image-load [{:keys [data width height]}]
           (let [tex-data (texture-incantation game data width height tex-unit)]
             (swap! db* assoc tex-name tex-data)
             (swap! world* o/insert tex-name {::loaded? true}))))

        :else
        (println uri "not supported")))))

