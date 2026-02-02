(ns minusthree.engine.systems 
  (:require
    [minusthree.engine.time :as time]
    [minusthree.engine.loading :as loading]))

(def all
  [time/system
   loading/system])
