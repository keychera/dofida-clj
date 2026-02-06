(ns minusthree.platform.jvm.jvm-game
  (:require
   [minusthree.platform.jvm.glfw :as glfw]
  ;;  [minusthree.platform.jvm.sdl3 :as sdl]
   ))

(defn create-window [config]
  (glfw/create-window config)
  #_(sdl/create-window config))

(defn start [window config]
  (glfw/start-glfw-loop window config)
  #_(sdl/start-sdl-loop window config))

(defn -main []
  (let [sdl-window (create-window {})]
    (start sdl-window {})))
