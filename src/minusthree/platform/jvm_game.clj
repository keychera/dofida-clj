(ns minusthree.platform.jvm-game
  (:require
   [minusthree.platform.glfw :as glfw]
   [minusthree.platform.sdl3 :as sdl]
   [minustwo.systems.window :as window]))

(defn create-window [config]
  (glfw/create-window config)
  #_(sdl/create-window config))

(defn start [window config]
  (glfw/start-glfw-loop window config)
  #_(sdl/start-sdl-loop window config))

(defn -main []
  (let [sdl-window (sdl/create-window {})]
    (start sdl-window {})))
