(ns minustwo.systems
  (:require
   #?(:clj  [minustwo.model.assimp-lwjgl :as assimp-jvm]
      :cljs [minustwo.model.assimp-js :as assimp-js])
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl :as gl-system]
   [minustwo.systems.view.projection :as projection]
   [minustwo.systems.window :as window]))

(def all
  [window/system
   projection/system
   firstperson/system

   #?(:clj  assimp-jvm/system
      :cljs assimp-js/system)

   gl-system/system
   gl-magic/system])