(ns minusthree.platform.glfw
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
   [org.lwjgl.glfw GLFW]
   [org.lwjgl.opengl GL GL42]))

(defn create-window
  "return handle of the glfw window"
  ([] (create-window {}))
  ([{:keys [w h x y floating? title]
     :or   {w 1280 h 720 x 100 y 100 title "Hello, dofida!"}}]
   (when-not (GLFW/glfwInit)
     (throw (ex-info "Unable to initialize GLFW" {}))) 
   (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
   (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
   (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 4)
   (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 2)
   (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL42/GL_TRUE)
   ;; Forward compat is required on macOS, harmless elsewhere
   (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
   (GLFW/glfwWindowHint GLFW/GLFW_SAMPLES 4)
   (let [window (GLFW/glfwCreateWindow w h title 0 0)]
     (when (or (nil? window) (zero? window))
       (throw (ex-info "Failed to create GLFW window" {})))
     (GLFW/glfwSetWindowPos window x y)
     (GLFW/glfwMakeContextCurrent window)
     (GLFW/glfwSwapInterval 0) ;; vsync off
     (GL/createCapabilities)
     (when floating? (GLFW/glfwSetWindowAttrib window GLFW/GLFW_FLOATING GLFW/GLFW_TRUE))
     window)))
