(ns minusthree.platform.jvm-game
  (:require
   [minusthree.platform.glfw :as glfw]
   [minustwo.systems.window :as window])
  (:import
   [org.lwjgl.glfw Callbacks GLFW]))

(defn start
  ([]
   (let [window (glfw/create-window)]
     (start window {})))
  ([glfw-window {:keys [stop-flag*]}]
   (println "hello -3")
   (try
     (GLFW/glfwShowWindow glfw-window)
     (loop [game {:total-time 0.0}]
       (when-not (or (GLFW/glfwWindowShouldClose glfw-window)
                     (and (some? stop-flag*) @stop-flag*))
         (let [ts (* (GLFW/glfwGetTime) 1000)
               dt (- ts (:total-time game))
               game (assoc game
                           :delta-time dt
                           :total-time ts)]
           (GLFW/glfwSwapBuffers glfw-window)
           (GLFW/glfwPollEvents)
           (GLFW/glfwSetWindowTitle glfw-window (str "frametime(ms): " dt))
           (recur game))))
     (finally
       (Callbacks/glfwFreeCallbacks glfw-window)
       (GLFW/glfwDestroyWindow glfw-window)
       (GLFW/glfwTerminate)))))
