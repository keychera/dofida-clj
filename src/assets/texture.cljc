(ns assets.texture
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [odoyle.rules :as o]))

(s/def ::tex-unit int?)

(s/def ::from-png map?)

(defmethod asset/process-asset ::asset/texture-from-png
  [world* game asset-id {::asset/keys [asset-to-load]
                         ::keys [tex-unit]}]
  (utils/get-image
   asset-to-load
   (fn on-image-load [{:keys [data width height]}]
     (let [texture (gl game #?(:clj genTextures :cljs createTexture))]
       (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
       (gl game bindTexture (gl game TEXTURE_2D) texture)

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
       (swap! world* o/insert asset-id
              {::asset/loaded? true
               ::from-png (vars->map texture tex-unit)})))))

(s/def ::fbo map?)

(defmethod asset/process-asset ::asset/fbo
  [world* game asset-id {::keys [tex-unit]}]
  (let [[width height] (utils/get-size game)
        frame-buf (gl game #?(:clj genFramebuffers :cljs createFramebuffer))
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

    (swap! world* o/insert asset-id
           {::asset/loaded? true
            ::fbo (vars->map frame-buf fbo-tex tex-unit)})))