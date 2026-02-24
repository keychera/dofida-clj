(ns minusthree.engine.thorvg
  (:require
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(defn after-refresh [new-world _new-game]
  new-world)

(def rules
  (o/ruleset
   {}))

(defn render [game]
  game)

(defn before-refresh [old-world _old-game]
  old-world)

(def system
  {::world/after-refresh #'after-refresh
   ::world/rules #'rules
   ::world/before-refresh #'before-refresh})
