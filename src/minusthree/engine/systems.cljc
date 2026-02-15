(ns minusthree.engine.systems
  (:require
   [minusthree.anime.anime :as anime]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.gl.texture :as texture]
   [minusthree.model.gltf-model :as gltf]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.stage.sankyuu :as sankyuu]))

(def all
  [time/system
   loading/system
   texture/system

   t3d/system
   model-rendering/system
   gltf/system

   ;; anime
   anime/system

   sankyuu/system])
