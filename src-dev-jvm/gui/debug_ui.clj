(ns gui.debug-ui
  (:require
   [platform.start :as start])
  (:import
   (imgui ImGui ImVec2)
   (imgui.flag ImGuiConfigFlags)
   (org.lwjgl.glfw GLFW)))

(def fps-buf (atom (float-array 40)))
(def fps-idx (atom 0))
(def last-sample-time (atom 0))

(defn sample-fps [fps]
  (let [now (System/currentTimeMillis)]
    (when (>= (- now @last-sample-time) 1000)
      (let [buf @fps-buf]
        (aset-float buf @fps-idx (float fps))
        (swap! fps-idx #(mod (inc %) (alength buf)))
        (reset! last-sample-time now)))))

(def config (merge {:title "dofida" :text "is grateful"}
                   (:imgui start/config)))

(defn fps-panel!
  []
  (let [fps (.getFramerate (ImGui/getIO))]
    (ImGui/text (format "FPS: %.1f (%.2f ms)" fps (if (pos? fps) (/ 1000.0 fps) 0.0)))
    (sample-fps fps)
    (let [buf  @fps-buf size (ImVec2. (float 240) (float 120))]
      ;; label, values, count, offset, overlay, scaleMin, scaleMax, size  
      (ImGui/plotLines "" buf (alength buf) @fps-idx "FPS graph" (float 0.0) (float 180.0) size))))

(defn init [imguiGlfw imGuiGl3 window]
  (ImGui/createContext)
  (doto (ImGui/getIO)
    (.addConfigFlags ImGuiConfigFlags/ViewportsEnable)
    (.addConfigFlags ImGuiConfigFlags/DockingEnable)
    (.setFontGlobalScale 1.0))
  (doto imguiGlfw
    (.init (:handle window) true)
    (.setCallbacksChainForAllWindows true))
  (.init imGuiGl3 "#version 300 es"))

(defn layer []
  (let [{:keys [title text]} config]
    (ImGui/begin title)
    (ImGui/text text))
  (fps-panel!)
  (ImGui/end))

(defn frame [imguiGlfw imGuiGl3]
  (.newFrame imguiGlfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (layer)
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData))
  (let [backupWindowPtr (GLFW/glfwGetCurrentContext)]
    (ImGui/updatePlatformWindows)
    (ImGui/renderPlatformWindowsDefault)
    (GLFW/glfwMakeContextCurrent backupWindowPtr)))

(defn destroy [imguiGlfw imGuiGl3]
  (.shutdown imGuiGl3)
  (.shutdown imguiGlfw)
  (ImGui/destroyContext))