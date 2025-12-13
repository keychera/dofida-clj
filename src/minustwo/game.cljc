(ns minustwo.game
  (:require
   [engine.world :as world]))

(defn ->game
  "webgl-context for WebGL, glfw-window handle for lwjgl, value from glfwCreateWindow"
  [{:keys [total-time delta-time glfw-window webgl-context]}]
  (merge
   (cond-> {::render-fns* (atom nil)
            :total-time total-time
            :delta-time delta-time}
     glfw-window   (assoc :glfw-window glfw-window)
     webgl-context (assoc :webgl-context webgl-context))
   (world/->init)))