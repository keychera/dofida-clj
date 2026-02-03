(ns minusthree.engine.systems
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.stage.sankyuu :as sankyuu]))

(def all
  [time/system
   loading/system

   sankyuu/system])
