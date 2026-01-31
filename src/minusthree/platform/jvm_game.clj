(ns minusthree.platform.jvm-game
  (:require
   [minustwo.systems.window :as window]
   [minusthree.platform.sdl3 :as sdl]))

(defn create-window [config]
  #_(minusthree.platform.glfw/create-window config)
  (sdl/create-window config))

(defn start [window config]
  #_(minusthree.platform.glfw/start-glfw-loop window config)
  (sdl/start-sdl-loop window config))

(defn -main []
  (let [sdl-window (sdl/create-window {})]
    (start sdl-window {})))
