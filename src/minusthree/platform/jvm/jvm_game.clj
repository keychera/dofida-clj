(ns minusthree.platform.jvm.jvm-game
  (:gen-class)
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

(defn -main [& _]
  ;; config needs hammock
  (let [config {:window-conf {:w 540 :h 540 :x 100 :y 100 :floating? false}
                :imgui       {:title "dofida"
                              :text  "felt gratitude"}}
        window (create-window (:window-conf config))]
    (start window config)))
