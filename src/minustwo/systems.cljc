(ns minustwo.systems
  (:require
   [minustwo.anime.anime :as anime]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.texture :as texture]
   [minustwo.model.assimp :as assimp]
   [minustwo.stage.hidup :as hidup]
   [minustwo.stage.wirecube :as wirecube]
   [minustwo.systems.gizmo.perspective-grid :as perspective-grid]
   [minustwo.systems.input :as input]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.projection :as projection]
   [minustwo.systems.view.room :as room]
   [minustwo.systems.window :as window]))

(def all
  [time/system

   window/system
   projection/system
   firstperson/system
   input/system

   assimp/system
   texture/system

   gl-system/system
   gl-magic/system
   anime/system

   room/system
   perspective-grid/system
   t3d/system

   hidup/system
   wirecube/system
   pose/system])
