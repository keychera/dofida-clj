(ns minusthree.platform.glfw
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
   [org.lwjgl.glfw GLFW]
   [org.lwjgl.opengl GL GL33]))

(defn create-window
  "return handle of the glfw window"
  ([] (create-window {}))
  ([config]
   (when-not (GLFW/glfwInit)
     (throw (Exception. "Unable to initialize GLFW")))
   (let [{:keys [w h x y floating?]} config]
     (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
     (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
     (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
     (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
     (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL33/GL_TRUE)
     (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
     (GLFW/glfwWindowHint GLFW/GLFW_SAMPLES 4)
     (if-let [window (GLFW/glfwCreateWindow w h "Hello, dofida!" 0 0)]
       (do
         (GLFW/glfwSetWindowPos window x, y)
         (GLFW/glfwMakeContextCurrent window)
         (GLFW/glfwSwapInterval 1)
         (GL/createCapabilities)
         (when floating? (GLFW/glfwSetWindowAttrib window GLFW/GLFW_FLOATING GLFW/GLFW_TRUE))
         window)
       (throw (Exception. "Failed to create window"))))))
