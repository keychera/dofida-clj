(ns minusthree.engine.systems
  (:require
   [minusthree.anime.anime :as anime]
   [minusthree.engine.camera :as camera]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.gl.texture :as texture]
   [minusthree.model.gltf-model :as gltf-model]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.model.pmx-model :as pmx-model]
   [minusthree.stage.sankyuu :as sankyuu]))

(def all
  [time/system
   loading/system
   texture/system
   camera/system

   t3d/system
   model-rendering/system
   gltf-model/system
   pmx-model/system

   ;; anime
   anime/system

   sankyuu/system])
