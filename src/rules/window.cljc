(ns rules.window
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]
   [engine.utils :as utils]))

(s/def ::dimension map?)

(defn set-window [world width height]
  (o/insert world ::window ::dimension {:width width :height height}))

(defn get-window [world]
  (:dimension (first (o/query-all world ::window))))

(def system
  {::world/init-fn
   (fn [world game]
     (let [[w h] (utils/get-size game)]
       (set-window world w h)))

   ::world/rules
   (o/ruleset
    {::window
     [:what
      [::window ::dimension dimension]]})})
