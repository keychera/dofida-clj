(ns minusthree.engine.systems
  (:require
   [minusthree.anime.anime :as anime]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.gl.texture :as texture]
   [minusthree.model.gltf-model :as gltf-model]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.stage.sankyuu :as sankyuu]
   [minusthree.model.pmx-model :as pmx-model]))

(def all
  [time/system
   loading/system
   texture/system

   t3d/system
   model-rendering/system
   gltf-model/system
   pmx-model/system

   ;; anime
   anime/system

   sankyuu/system])
