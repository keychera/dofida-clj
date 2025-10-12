(ns rules.interface.input
  (:require
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::x number?)
(s/def ::y number?)

(world/system system
  {::world/rules
   (o/ruleset
    {::mouse
     [:what
      [::mouse ::x mouse-x]
      [::mouse ::y mouse-y]]
     
     ::mouse-delta
     [:what
      [::mouse-delta ::x mouse-dx]
      [::mouse-delta ::y mouse-dy]]})})

(defn update-mouse-pos [world x y]
  (o/insert world ::mouse {::x x ::y y}))

(defn update-mouse-delta [world dx dy]
  (o/insert world ::mouse-delta {::x dx ::y dy}))