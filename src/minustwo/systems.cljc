(ns minustwo.systems
  (:require
   #?(:clj  [minustwo.model.assimp-lwjgl :as assimp-jvm]
      :cljs [minustwo.model.assimp-js :as assimp-js])
   [minustwo.anime.anime :as anime]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.texture :as texture]
   [minustwo.stage.hidup :as hidup]
   [minustwo.systems.gizmo.perspective-grid :as perspective-grid]
   [minustwo.systems.input :as input]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.projection :as projection]
   [minustwo.systems.window :as window]
   [minustwo.systems.view.room :as room]))

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
   anime/system

   room/system
   perspective-grid/system

   t3d/system
   hidup/system])