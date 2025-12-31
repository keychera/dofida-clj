(ns minustwo.anime.pacing
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]))

(s/def ::max-progress number?)
(s/def ::timescale number?)
(s/def ::config (s/keys :req-un [::max-progress ::timescale]))
(s/def ::progress number?)

(defn set-config [world config]
  (o/insert world ::world/global ::config config))

(defn init-fn [world _game]
  (set-config world {:max-progress 16.0
                     :timescale    (/ 1 640)}))

(def rules
  (o/ruleset
   {::pacing
    [:what
     [::time/now ::time/total tt {:then false}]
     [::time/now ::time/slice 1]
     [::world/global ::config config]
     :then
     (let [{:keys [max-progress timescale]} config
           progress (mod (* tt timescale) max-progress)]
       (insert! ::world/global ::progress progress))]}))

#_("maybe it will (go) away ))( maybe (not). )(( we will (welcome) them with (open) arms")

(def system
  {::world/init-fn init-fn
   ::world/rules #'rules})
