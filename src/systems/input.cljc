(ns systems.input
  (:require
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::x number?)
(s/def ::y number?)

(def system
  {::world/rules
   (o/ruleset
    {::mouse
     [:what
      [::mouse ::x x]
      [::mouse ::y y]]})})

(defn insert-mouse [world x y]
  (o/insert world ::mouse {::x x ::y y}))