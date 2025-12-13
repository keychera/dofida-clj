(ns minustwo.systems
  (:require
   #?(:clj  [minustwo.model.assimp-lwjgl :as assimp-jvm]
      :cljs [minustwo.model.assimp-js :as assimp-js])
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.texture :as texture]
   [minustwo.stage.hidup :as hidup]
   [minustwo.systems.view.projection :as projection]
   [minustwo.systems.window :as window]
   [rules.interface.input :as input]
   [rules.time :as time]))

(def all
  [time/system
   
   window/system
   projection/system
   firstperson/system
   input/system

   #?(:clj  assimp-jvm/system
      :cljs assimp-js/system)
   texture/system

   gl-system/system
   gl-magic/system
   
   hidup/system])