(ns minusthree.engine.rendering.box2d
  (:require
   [minusthree.engine.loader :as loader])
  (:import
   [box2d b2d]))

(defonce _loadlib
  (loader/load-libs "box2dd"))

(comment
  (type b2d))