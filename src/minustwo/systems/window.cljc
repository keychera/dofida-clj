(ns minustwo.systems.window
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]
   [minustwo.utils :as utils]))

(s/def ::dimension map?)

(defn set-window [world width height]
  (o/insert world ::world/global ::dimension {:width width :height height}))

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
      [::world/global ::dimension dimension]]})})
