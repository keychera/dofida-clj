(ns minustwo.systems
  (:require
   [minustwo.systems.view.projection :as projection]
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.systems.window :as window]))

(def all
  [window/system
   projection/system 
   firstperson/system])