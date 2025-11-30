(ns minusone.rules.gl.texture
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:cljs [minusone.rules.model.assimp-js :refer [data-uri->ImageBitmap]])
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]))

(defn texture-incantation [game data width height tex-unit]
  (let [texture (gl game #?(:clj genTextures :cljs createTexture))]
    (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
    (gl game bindTexture (gl game TEXTURE_2D) texture)
    #?(:cljs (gl game pixelStorei (gl game UNPACK_FLIP_Y_WEBGL) true))

    (gl game texImage2D (gl game TEXTURE_2D)
        #_:mip-level    0
        #_:internal-fmt (gl game RGBA)
        (int width)
        (int height)
        #_:border       0
        #_:src-fmt      (gl game RGBA)
        #_:src-type     (gl game UNSIGNED_BYTE)
        data)

    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game NEAREST))
    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game NEAREST))
    (vars->map texture tex-unit)))

(defn fbo-incantation [game width height tex-unit]
  (let [frame-buf (gl game #?(:clj genFramebuffers :cljs createFramebuffer))
        _         (gl game bindFramebuffer (gl game FRAMEBUFFER) frame-buf)
        fbo-tex   (gl game #?(:clj genTextures :cljs createTexture))]

      ;; bind, do stuff, unbind, hmm hmm
    (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
    (gl game bindTexture (gl game TEXTURE_2D) fbo-tex)
    (gl game texImage2D (gl game TEXTURE_2D)
        #_:mip-level    0
        #_:internal-fmt (gl game RGBA)
        (int width)
        (int height)
        #_:border       0
        #_:src-fmt      (gl game RGBA)
        #_:src-type     (gl game UNSIGNED_BYTE)
        #?(:clj 0 :cljs nil))

    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game NEAREST))
    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game NEAREST))
    (gl game bindTexture (gl game TEXTURE_2D) #?(:clj 0 :cljs nil))

    (gl game framebufferTexture2D (gl game FRAMEBUFFER) (gl game COLOR_ATTACHMENT0) (gl game TEXTURE_2D) fbo-tex 0)

    (when (not= (gl game checkFramebufferStatus (gl game FRAMEBUFFER)) (gl game FRAMEBUFFER_COMPLETE))
      (println "warning: framebuffer creation incomplete"))
    (gl game bindFramebuffer (gl game FRAMEBUFFER) #?(:clj 0 :cljs nil))

    (vars->map frame-buf fbo-tex tex-unit)))

(s/def ::texture any?)
(s/def ::tex-unit int?)
(s/def ::data (s/keys :req-un [::texture ::tex-unit]))

(s/def ::data-uri-to-load string?)
(s/def ::texture-loaded? #{:pending :loading true})

(defmethod asset/process-asset ::asset/texture-from-png
  [world* game asset-id {::asset/keys [asset-to-load] ::keys [tex-unit]}]
  (utils/get-image
   asset-to-load
   (fn on-image-load [{:keys [data width height]}]
     (let [texture (texture-incantation game data width height tex-unit)]
       (swap! world* o/insert asset-id {::asset/loaded? true ::data texture})))))

(s/def ::fbo map?)

(defmethod asset/process-asset ::asset/fbo
  [world* game asset-id {::keys [tex-unit]}]
  (let [[width height] (utils/get-size game)
        fbo            (fbo-incantation game width height tex-unit)]
    (swap! world* o/insert asset-id {::asset/loaded? true ::fbo fbo})))

(def rules
  (o/ruleset
   {::data-uri-to-load
    [:what
     [tex-id ::data-uri-to-load data-uri]
     [tex-id ::tex-unit tex-unit]
     [tex-id ::texture-loaded? :pending]]}))

(def system
  {::world/rules rules})

(defn data-uri->texture->world*
  [textures-to-load world* game]
  (doseq [to-load textures-to-load]
    (let [tex-id   (:tex-id to-load)
          data-uri (:data-uri to-load)
          tex-unit (:tex-unit to-load)]
      (println "loading texture" tex-id)
      (swap! world* #(-> %
                         (o/retract tex-id ::data-uri-to-load)
                         (o/insert tex-id ::texture-loaded? :loading)))
      #?(:clj :noop
         :cljs
         (data-uri->ImageBitmap
          data-uri
          (fn [{:keys [bitmap width height]}]
            (let [tex-data (texture-incantation game bitmap width height tex-unit)]
              (println "[assimp-js] loaded" tex-id tex-data)
              (swap! world* o/insert tex-id {::data tex-data ::texture-loaded? true}))))))))

