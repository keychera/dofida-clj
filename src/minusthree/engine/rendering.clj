(ns minusthree.engine.rendering
  (:require
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_TRIANGLES]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v])
  (:import
   (imgui ImGui ImVec2)
   (imgui.extension.imguizmo ImGuizmo)
   (imgui.flag ImGuiConfigFlags)
   (imgui.gl3 ImGuiImplGl3)
   (imgui.glfw ImGuiImplGlfw)
   (org.lwjgl.glfw GLFW)))

(def project
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (mat/perspective fov aspect 0.1 1000)))

(def view
  (let [position       (v/vec3 0.0 1.0 18.0)
        look-at-target (v/vec3 0.0 0.0 -1.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (mat/look-at position look-at-target up)))

(def model (m-ext/vec3->scaling-mat (v/vec3 0.5)))

(def fps-buf (atom (float-array 40)))
(def fps-idx (atom 0))
(def last-sample-time (atom 0))

(defn init [{:keys [glfw-window] :as game}]
  (let [imGuiGlfw (ImGuiImplGlfw.)
        imGuiGl3  (ImGuiImplGl3.)]
    (ImGui/createContext)
    (doto (ImGui/getIO)
      (.addConfigFlags ImGuiConfigFlags/DockingEnable)
      (.setFontGlobalScale 1.0))
    (doto imGuiGlfw
      (.init glfw-window true)
      (.setCallbacksChainForAllWindows true))
    (.init imGuiGl3 "#version 300 es")
    (println "init game")
    (assoc game :imGuiGlfw imGuiGlfw :imGuiGl3 imGuiGl3)))

(defn sample-fps [fps]
  (let [now (System/currentTimeMillis)]
    (when (>= (- now @last-sample-time) 1000)
      (let [buf @fps-buf]
        (aset-float buf @fps-idx (float fps))
        (swap! fps-idx #(mod (inc %) (alength buf)))
        (reset! last-sample-time now)))))

(defn fps-panel!
  []
  (let [fps (.getFramerate (ImGui/getIO))]
    (ImGui/text (format "FPS: %.1f (%.2f ms)" fps (if (pos? fps) (/ 1000.0 fps) 0.0)))
    (sample-fps fps)
    (let [buf  @fps-buf size (ImVec2. (float 240) (float 120))]
      ;; label values count offset overlay scaleMin scaleMax size  
      (ImGui/plotLines "" buf (alength buf) @fps-idx "FPS graph" (float 0.0) (float 180.0) size))))

(def camera-view (float-array
                  [0.25881903   0.5117449  -0.81915265  0.0
                   0.0          0.8478537   0.5299193   0.0
                   0.9659258   -0.137949    0.21984629  0.0
                   -0.003452   0.00165     -8.0         1.0]))

(def projection (float-array
                 [1.9626105 0.0 0.0 0.0
                  0.0 1.9626105 0.0 0.0
                  0.0 0.0 -1.002002 -1.0
                  0.0 0.0 -0.2002002 0.0]))

(def identity-mat (float-array
                   [1.0 0.0 0.0 0.0
                    0.0 1.0 0.0 0.0
                    0.0 0.0 1.0 0.0
                    0.0 0.0 0.0 1.0]))

(defn imGuizmoPanel []
  (ImGuizmo/beginFrame)

  (when (ImGui/begin "imguizmo")

    (let [win-x (ImGui/getWindowPosX)
          win-y (ImGui/getWindowPosY)
          w (ImGui/getWindowWidth)
          h (ImGui/getWindowHeight)]
      (ImGuizmo/setOrthographic false)
      (ImGuizmo/enable true)
      (ImGuizmo/setDrawList)
      (ImGuizmo/setRect win-x win-y w h)
      (ImGuizmo/drawGrid camera-view projection identity-mat 100)
      (ImGuizmo/setID 0)
      (ImGuizmo/drawCubes camera-view projection identity-mat))

    :-)

  (if (ImGuizmo/isUsing)
    (do (ImGui/text "using gizmo")
        (when (ImGuizmo/isOver)
          (ImGui/text "over a gizmo")))
    (ImGui/text "not using gizmo"))

  (ImGui/end))

(defn layer [title text]
  (ImGui/begin title)
  (ImGui/text text)
  (fps-panel!)
  (imGuizmoPanel)
  (ImGui/end))

(defn imGuiFrame [{:keys [imGuiGl3 imGuiGlfw config]}]
  (.newFrame imGuiGlfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (let [{:keys [title text]} (:imgui config)]
    (layer title text))
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData))
  (let [backupWindowPtr (GLFW/glfwGetCurrentContext)]
    (ImGui/updatePlatformWindows)
    (ImGui/renderPlatformWindowsDefault)
    (GLFW/glfwMakeContextCurrent backupWindowPtr)))

(defn rendering-zone [game]
  (let [ctx nil
        {:keys [w h]} (-> game :config :window-conf)]
    (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl ctx clearColor 0.02 0.02 0.12 1.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl ctx viewport 0 0 w h)
    (let [world   (::world/this game)
          renders (o/query-all world :minusthree.stage.sankyuu/render-data)]
      (doseq [{:keys [program-info gl-data primitives]} renders]
        (let [vaos (::gl-magic/vao gl-data)]
          (gl ctx useProgram (:program program-info))
          (cljgl/set-uniform ctx program-info :u_projection (vec->f32-arr (vec project)))
          (cljgl/set-uniform ctx program-info :u_view (vec->f32-arr (vec view)))
          (cljgl/set-uniform ctx program-info :u_model (vec->f32-arr (vec model)))

          (doseq [{:keys [indices vao-name]} primitives]
            (let [vert-count     (:count indices)
                  component-type (:componentType indices)
                  vao            (get vaos vao-name)]
              (when vao
                (gl ctx bindVertexArray vao)
                (gl ctx drawElements GL_TRIANGLES vert-count component-type 0)
                (gl ctx bindVertexArray 0)))))))
    (imGuiFrame game))
  game)

(defn destroy [{:keys [imGuiGl3 imGuiGlfw]}]
  (println "destroy game")
  (when imGuiGl3 (.shutdown imGuiGl3))
  (when imGuiGlfw (.shutdown imGuiGlfw))
  (ImGui/destroyContext))
