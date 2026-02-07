(ns minusthree.engine.systems
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.time :as time]
   [minusthree.stage.sankyuu :as sankyuu]
   [minusthree.gl.texture :as texture]))

(def all
  [time/system
   loading/system
   texture/system

   sankyuu/system])
