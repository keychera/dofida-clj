(ns minustwo.systems
  (:require
   [minustwo.anime.anime :as anime]
   [minustwo.anime.anime-gltf :as anime-gltf]
   [minustwo.anime.morph :as morph]
   [minustwo.anime.pacing :as pacing]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.texture :as texture]
   [minustwo.gl.vao :as vao]
   [minustwo.model.assimp :as assimp]
   [minustwo.stage.default :as default]
   [minustwo.stage.esse-model :as esse-model]
   [minustwo.stage.gltf-renderer :as gltf-renderer]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.stage.pmx-renderer :as pmx-renderer]
   [minustwo.stage.pseudo.bones :as bones]
   [minustwo.stage.pseudo.particle :as particle]
   [minustwo.stage.wirecube :as wirecube]
   [minustwo.systems.gizmo.perspective-grid :as perspective-grid]
   [minustwo.systems.input :as input]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.uuid-instance :as uuid-instance]
   [minustwo.systems.view.camera :as camera]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.room :as room]
   [minustwo.systems.window :as window]
   [minustwo.zone.director :as director]
   #?(:clj [minustwo.zone.studio :as studio])))

(def all
  [time/system

   window/system
   camera/system
   firstperson/system
   input/system

   assimp/system
   texture/system

   gl-system/system
   gl-magic/system
   vao/system
   anime-gltf/system
   anime/system
   pacing/system
   morph/system

   room/system
   perspective-grid/system
   t3d/system
   uuid-instance/system
   esse-model/system
   director/system
   #?(:clj studio/system)

   pmx-model/system
   pmx-renderer/system
   gltf-renderer/system
   bones/system
   pose/system
   particle/system

   wirecube/system
   default/system])
