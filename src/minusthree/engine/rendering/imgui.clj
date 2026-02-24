(ns minusthree.engine.rendering.imgui
  (:require
   [minusthree.engine.gui.fps-panel :as fps-panel])
  (:import
   [imgui ImGui]
   [imgui.flag ImGuiConfigFlags]
   [imgui.gl3 ImGuiImplGl3]
   [imgui.glfw ImGuiImplGlfw]
   [org.lwjgl.glfw GLFW]))

(defn init [{:keys [glfw-window] :as game}]
  (let [imGuiGlfw (ImGuiImplGlfw.)
        imGuiGl3  (ImGuiImplGl3.)]
    (ImGui/createContext)
    (doto (ImGui/getIO)
      (.addConfigFlags ImGuiConfigFlags/ViewportsEnable)
      (.setConfigWindowsMoveFromTitleBarOnly  true)
      (.setFontGlobalScale 1.0))
    (doto imGuiGlfw
      (.init glfw-window true)
      (.setCallbacksChainForAllWindows true))
    (.init imGuiGl3 "#version 300 es")
    (assoc game
           ::imGuiglfw imGuiGlfw
           ::imGuiGl3 imGuiGl3)))

(defn frame
  [{::keys [imGuiGl3 imGuiglfw]
    :keys [config]}]
  (.newFrame imGuiglfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (let [{:keys [title text]} (:imgui config)]
    (fps-panel/render! title text))
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData))
  (let [backupWindowPtr (GLFW/glfwGetCurrentContext)]
    (ImGui/updatePlatformWindows)
    (ImGui/renderPlatformWindowsDefault)
    (GLFW/glfwMakeContextCurrent backupWindowPtr)))

(defn destroy [{::keys [imGuiGl3 imGuiglfw]}]
  (println "destroy imgui context")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiglfw (.shutdown imGuiglfw))
  (ImGui/destroyContext))
