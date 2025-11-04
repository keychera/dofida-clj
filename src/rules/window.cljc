(ns rules.window
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]))

;; specs
(s/def ::dimension map?)

(def system
  {::world/rules
   (o/ruleset
    {::window
     [:what
      [::window ::dimension dimension]]})})

(defn set-window [world width height]
  (o/insert world ::window ::dimension {:width width :height height}))