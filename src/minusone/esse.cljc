(ns minusone.esse 
  (:require
   [engine.utils :as utils]
   [odoyle.rules :as o]))

(defn esse [world id & facts]
  (o/insert world id (apply utils/deep-merge facts)))
