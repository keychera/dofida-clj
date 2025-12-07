(ns rules.time
  (:require
   [engine.macros :refer [insert!]]
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::now number?)
(s/def ::delta number?)
(s/def ::total number?)
(s/def ::tick int?)
(s/def ::slice int?)

(def system
  {::world/rules
   (o/ruleset
    {::slice-per-tick
     [:what
      [::now ::delta _]
      :then
      (insert! ::now ::slice 0)]

     ::slicing
     [:what
      [::now ::slice step]
      :when (< step 4)
      :then 
      (insert! ::now ::slice (inc step))]})})

(defn insert [world total delta]
  (o/insert world ::now {::total total ::delta delta}))