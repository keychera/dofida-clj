(ns minustwo.game
  (:require
   [engine.world :as world]))

(defn ->game
  "gl-content is only used for WebGL abstraction at the moment.
   In lwjgl, you can pass nil or empty map since it won't be used"
  [gl-context]
  (merge
   {::gl-context  gl-context
    ::render-fns* (atom nil)}
   (world/->init)))