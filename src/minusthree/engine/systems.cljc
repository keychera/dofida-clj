(ns minusthree.engine.systems
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.stage.sankyuu :as sankyuu]
   [minusthree.gl.texture :as texture]
   [minustwo.systems.transform3d :as t3d]
   [minusthree.stage.model :as model]))

(def all
  [time/system
   loading/system
   texture/system

   model/system
   t3d/system

   sankyuu/system])
