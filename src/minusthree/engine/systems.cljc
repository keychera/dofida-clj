(ns minusthree.engine.systems
  (:require
   [minusthree.anime.pose :as pose]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.gl.texture :as texture]
   [minusthree.stage.model :as model]
   [minusthree.stage.sankyuu :as sankyuu]))

(def all
  [time/system
   loading/system
   texture/system

   model/system
   t3d/system

   ;; anime
   pose/system

   sankyuu/system])
