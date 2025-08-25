(ns systems.window
  (:require
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

;; specs
(s/def ::width number?)
(s/def ::height number?)

(def system
  {::world/rules
   (o/ruleset
    {::window
     [:what
      [::window ::width width]
      [::window ::height height]]})})

(defn set-window [world width height]
  (o/insert world ::window {::width width ::height height}))