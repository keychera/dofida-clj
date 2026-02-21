(ns minusthree.engine.rendering.playground
  (:require
   [minusthree.engine.loader :as loader])
  (:import
   [box2d b2d]
   [thorvg tvg]))

(defonce _loadlib
  (do (loader/load-libs "box2dd")
      (loader/load-libs "libthorvg-1")))

(comment
  (type b2d)
  (type tvg)
  :-)
