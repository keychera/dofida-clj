(ns assets.texture
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [odoyle.rules :as o]))

(s/def ::data map?)

(defmethod asset/process-asset ::asset/texture
  [world* game asset-id {::asset/keys [asset-to-load]
                         ::keys [texture-unit]}]
  (utils/get-image
   asset-to-load
   (fn on-image-load [{:keys [data width height]}]
     (let [texture (gl game #?(:clj genTextures :cljs createTexture))]
       #?(:clj (gl game activeTexture (+ (gl game TEXTURE0) texture-unit)))
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
       (swap! world*
              (fn [world]
                (-> world
                    (o/insert asset-id
                              {::asset/loaded? true
                               ::data (vars->map texture texture-unit)}))))))))