(ns rules.time
  (:require
   [engine.macros :refer [insert!]]
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::now number?)
(s/def ::delta number?)
(s/def ::total number?)
(s/def ::step int?)

(def system
  {::world/rules
   (o/ruleset
    {::step-per-tick
     [:what
      [::now ::delta _]
      :then
      (insert! ::now ::step 0)]

     ::stepping
     [:what
      [::now ::step step]
      :when (< step 4)
      :then 
      (insert! ::now ::step (inc step))]})})

(defn insert [world total delta]
  (o/insert world ::now {::total total ::delta delta}))