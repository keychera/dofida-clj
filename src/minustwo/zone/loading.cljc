(ns minustwo.zone.loading
  (:require
   [engine.world :as world]
   [minustwo.gl.gl-magic :as gl-magic]
   [odoyle.rules :as o]))

(defn loading-zone [game]
  (let [world*           (::world/atom* game)
        world            @world*
        gl-magic-to-cast (o/query-all world ::gl-magic/to-cast)]
    (when gl-magic-to-cast
      (doseq [spell-fact gl-magic-to-cast]
        ;; for now cast-spell is still synchronously so we didn't use :loading flag yet
        (let [summons-map (gl-magic/cast-spell world spell-fact)]
          (swap! world* gl-magic/summons->world* summons-map)))))
  game)