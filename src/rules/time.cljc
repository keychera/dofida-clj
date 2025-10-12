(ns rules.time 
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(s/def ::now number?)
(s/def ::delta number?)
(s/def ::total number?)

(defn insert [world total delta]
  (o/insert world ::now {::total total ::delta delta}))