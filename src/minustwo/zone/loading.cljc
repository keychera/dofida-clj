(ns minustwo.zone.loading
  (:require
   #?(:clj  [minustwo.model.assimp-lwjgl :as assimp-lwjgl]
      :cljs [minustwo.model.assimp-js :as assimp-js])
   [engine.world :as world]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.texture :as texture]
   [minustwo.model.assimp :as assimp]
   [odoyle.rules :as o]))

(defn loading-zone [game]
  (let [world*           (::world/atom* game)
        world            @world*
        gl-magic-to-cast (o/query-all world ::gl-magic/to-cast)
        models-to-load   (o/query-all world ::assimp/model-to-load)
        data-uri-to-load (o/query-all world ::texture/uri-to-load)]
    (when gl-magic-to-cast
      (doseq [spell-fact gl-magic-to-cast]
        ;; for now cast-spell is still synchronously so we didn't use :loading flag yet
        (let [summons-map (gl-magic/cast-spell world spell-fact)]
          (swap! world* gl-magic/summons->world* summons-map))))
    (when models-to-load
      #?(:clj  (some-> models-to-load (assimp-lwjgl/load-models-from-world* (::world/atom* game)))
         :cljs (some-> models-to-load (assimp-js/load-models-from-world* (::world/atom* game)))))
    (when data-uri-to-load
      (texture/load-texture->world* data-uri-to-load world*)))
  game)