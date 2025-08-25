(ns systems.time 
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

;; specs
(s/def ::total number?)
(s/def ::delta number?)

(def rules [])

(defn insert [world total delta]
  (o/insert world ::now {::total total ::delta delta}))