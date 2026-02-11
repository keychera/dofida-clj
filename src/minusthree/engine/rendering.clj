(ns minusthree.engine.rendering
  (:require
   [fastmath.matrix :refer [mat->float-array]]
   [fastmath.vector :as v]
   [minusthree.engine.gui.fps-panel :as fps-panel]
   [minusthree.engine.math :refer [look-at perspective]]
   [minusthree.engine.world :as world]
   [minusthree.stage.model :as model]
   [minustwo.gl.constants :refer [GL_BLEND GL_COLOR_BUFFER_BIT GL_CULL_FACE
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_FRAMEBUFFER GL_MULTISAMPLE
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
   [minusthree.engine.offscreen :as offscreen]
   [odoyle.rules :as o])
  (:import
   (imgui ImGui)
   (imgui.extension.imguizmo ImGuizmo)
   (imgui.flag ImGuiConfigFlags ImGuiWindowFlags)
   (imgui.gl3 ImGuiImplGl3)
   (imgui.glfw ImGuiImplGlfw)))

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
  (let [ctx nil]
    (gl ctx enable GL_BLEND)
    (gl ctx enable GL_CULL_FACE)
    (gl ctx enable GL_MULTISAMPLE)
    (gl ctx enable GL_DEPTH_TEST)
    (let [{:keys [w h]} (-> game :config :window-conf)
          imGuiGlfw     (ImGuiImplGlfw.)
          imGuiGl3      (ImGuiImplGl3.)
          screen1       (offscreen/prep-offscreen-render ctx w h 1)]
      (ImGui/createContext)
      (doto (ImGui/getIO)
        (.addConfigFlags ImGuiConfigFlags/DockingEnable)
        (.setConfigWindowsMoveFromTitleBarOnly  true)
        (.setFontGlobalScale 1.0))
      (doto imGuiGlfw
        (.init glfw-window true)
        (.setCallbacksChainForAllWindows true))
      (.init imGuiGl3 "#version 300 es")
      (println "init game")
      #_{:clj-kondo/ignore [:inline-def]}
      (def debug-var screen1)
      (assoc game
             :imGuiGlfw imGuiGlfw
             :imGuiGl3 imGuiGl3
             :screen1 screen1
             :project project
             :view view))))

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

(defn imGuiFrame [{:keys [imGuiGl3 imGuiGlfw config]}]
  (.newFrame imGuiGlfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (let [{:keys [w h]} (:window-conf config)]
    (imGuizmoPanel w h))
  (let [{:keys [title text]} (:imgui config)]
    (fps-panel/render! title text))
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData)))

(defn rendering-zone [game]
  (let [ctx                      nil
        {:keys [config screen1]} game
        {:keys [w h]}            (:window-conf config)]
    (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl ctx clearColor 0.02 0.02 0.12 1.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))

    (imGuiFrame game)

    (gl ctx bindFramebuffer GL_FRAMEBUFFER (:fbo screen1))
    (gl ctx clearColor 0.0 0.0 0.0 0.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl ctx viewport 0 0 w h)

    (let [world   (::world/this game)
          renders (o/query-all world ::model/render-model-biasa)]
      (doseq [{:keys [render-fn] :as match} renders]
        (render-fn game match)))

    (offscreen/render-fbo ctx screen1 {:fbo 0 :width w :height h}
                          {:translation (v/vec3 0.0 0.0 0.0)
                           :scale       (v/vec3 1.0 1.0 1.0)}))
  game)

(defn destroy [{:keys [imGuiGl3 imGuiGlfw]}]
  (println "destroy game")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiGlfw (.shutdown imGuiGlfw))
  (ImGui/destroyContext))

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect debug-var))
