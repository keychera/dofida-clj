(ns minusthree.engine.rendering.imgui
  (:require
   [fastmath.matrix :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.gui.fps-panel :as fps-panel]
   [minusthree.engine.math :refer [look-at perspective]])
  (:import
   [imgui ImGui]
   [imgui.extension.imguizmo ImGuizmo]
   [imgui.extension.imguizmo ImGuizmo]
   [imgui.flag ImGuiConfigFlags ImGuiWindowFlags]
   [imgui.flag ImGuiConfigFlags ImGuiWindowFlags]
   [imgui.gl3 ImGuiImplGl3]
   [imgui.glfw ImGuiImplGlfw]))

;; need hammock to decide where to abstract these project-view stufff
(def project
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (-> (perspective fov aspect 0.1 1000) mat->float-array)))

(def cam-distance 24.0)

(def view
  (let [position       (v/vec3 0.0 12.0 cam-distance)
        look-at-target (v/vec3 0.0 12.0 0.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (-> (look-at position look-at-target up) mat->float-array)))

(def identity-mat (float-array
                   [1.0 0.0 0.0 0.0
                    0.0 1.0 0.0 0.0
                    0.0 0.0 1.0 0.0
                    0.0 0.0 0.0 1.0]))

(defn init [{:keys [glfw-window] :as game}]
  (let [imGuiGlfw (ImGuiImplGlfw.)
        imGuiGl3  (ImGuiImplGl3.)]
    (ImGui/createContext)
    (doto (ImGui/getIO)
      (.addConfigFlags ImGuiConfigFlags/DockingEnable)
      (.setConfigWindowsMoveFromTitleBarOnly  true)
      (.setFontGlobalScale 1.0))
    (doto imGuiGlfw
      (.init glfw-window true)
      (.setCallbacksChainForAllWindows true))
    (.init imGuiGl3 "#version 300 es")
    (assoc game
           ::imGuiglfw imGuiGlfw
           ::imGuiGl3 imGuiGl3
           :project project
           :view view)))

(defn imGuizmoPanel [w h]
  (ImGuizmo/beginFrame)

  (ImGui/setNextWindowPos 0.0 0.0)
  (ImGui/setNextWindowSize (float w) (float h))
  (when (ImGui/begin "imguizmo"
                     (bit-or ImGuiWindowFlags/NoTitleBar
                             ImGuiWindowFlags/NoMove
                             ImGuiWindowFlags/NoBringToFrontOnFocus))
    (let [manip-x (+ w -150.0)
          manip-y 16.0]
      (ImGuizmo/setOrthographic false)
      (ImGuizmo/enable true)
      (ImGuizmo/setDrawList)
      (ImGuizmo/setRect 0.0 0.0 w h)
      (ImGuizmo/drawGrid view project identity-mat 100)
      (ImGuizmo/setID 0)
      (ImGuizmo/viewManipulate view cam-distance manip-x manip-y 128.0 128.0 0x70707070))

    :-)

  (ImGui/end))

(defn frame [{::keys [imGuiGl3 imGuiglfw] :keys [config]}]
  (.newFrame imGuiglfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (let [{:keys [w h]} (:window-conf config)]
    (imGuizmoPanel w h))
  (let [{:keys [title text]} (:imgui config)]
    (fps-panel/render! title text))
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData)))

(defn destroy [{::keys [imGuiGl3 imGuiglfw]}]
  (println "destroy imgui context")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiglfw (.shutdown imGuiglfw))
  (ImGui/destroyContext))
