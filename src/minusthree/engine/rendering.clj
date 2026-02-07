(ns minusthree.engine.rendering
  (:require
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [minusthree.engine.gui.fps-panel :as fps-panel]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_BLEND GL_COLOR_BUFFER_BIT GL_CULL_FACE
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_FRAMEBUFFER GL_MULTISAMPLE
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_TEXTURE0 GL_TEXTURE_2D GL_TRIANGLES]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
   [minustwo.stage.pseudo.offscreen :as offscreen]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v])
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
    (-> (mat/perspective fov aspect 0.1 1000) vec vec->f32-arr)))

(def cam-distance 24.0)

(def view
  (let [position       (v/vec3 0.0 12.0 cam-distance)
        look-at-target (v/vec3 0.0 12.0 0.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (-> (mat/look-at position look-at-target up) vec vec->f32-arr)))

(def model (-> (m-ext/vec3->scaling-mat (v/vec3 1.0)) vec vec->f32-arr))

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
             :screen1 screen1))))

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
          renders (o/query-all world :minusthree.stage.sankyuu/render-data)]
      (doseq [{:keys [program-info gl-data tex-data primitives]} renders]
        (let [vaos (::gl-magic/vao gl-data)]
          (gl ctx useProgram (:program program-info))
          (cljgl/set-uniform ctx program-info :u_projection project)
          (cljgl/set-uniform ctx program-info :u_view view)
          (cljgl/set-uniform ctx program-info :u_model model)

          (doseq [{:keys [indices vao-name tex-name]} primitives]
            (let [vert-count     (:count indices)
                  component-type (:componentType indices)
                  vao            (get vaos vao-name)
                  tex            (get tex-data tex-name)]
              (when vao
                (when-let [{:keys [tex-unit gl-texture]} tex]
                  (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
                  (gl ctx bindTexture GL_TEXTURE_2D gl-texture)
                  (cljgl/set-uniform ctx program-info :u_mat_diffuse tex-unit))

                (gl ctx bindVertexArray vao)
                (gl ctx drawElements GL_TRIANGLES vert-count component-type 0)
                (gl ctx bindVertexArray 0)))))))

    ;; this is an fn producing fn 💀 of course it won't render anything if it's just called once, need hammock
    ((offscreen/render-fbo
      screen1 {:fbo 0 :width w :height h}
      {:translation (v/vec3 0.0 0.0 0.0)
       :scale       (v/vec3 1.0)}) nil ctx))
  game)

(defn destroy [{:keys [imGuiGl3 imGuiGlfw]}]
  (println "destroy game")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiGlfw (.shutdown imGuiGlfw))
  (ImGui/destroyContext))

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect debug-var))
