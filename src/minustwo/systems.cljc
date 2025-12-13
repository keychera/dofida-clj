(ns minustwo.systems
  (:require
   [minustwo.systems.view.projection :as projection]
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.systems.window :as window]
   [minustwo.gl.gl :as gl]
   [minustwo.gl.gl-magic :as gl-magic]))

(def all
  [window/system
   projection/system
   firstperson/system

   gl/system
   gl-magic/system])