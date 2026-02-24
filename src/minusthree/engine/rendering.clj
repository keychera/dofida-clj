(ns minusthree.engine.rendering
  (:require
   [fastmath.vector :as v]
   [minusthree.engine.offscreen :as offscreen]
   [minusthree.engine.rendering.imgui :as imgui]
   [minusthree.engine.world :as world]
   [minusthree.model.model-rendering :as model-rendering]
   [odoyle.rules :as o]
   [minusthree.engine.thorvg :as thorvg])
  (:import
   [org.lwjgl.opengl GL45]
   [org.lwjgl.stb STBImage]))

(defn init [game]
  (GL45/glEnable GL45/GL_BLEND)
  (GL45/glEnable GL45/GL_BLEND)
  (GL45/glEnable GL45/GL_CULL_FACE)
  (GL45/glEnable GL45/GL_MULTISAMPLE)
  (GL45/glEnable GL45/GL_DEPTH_TEST)
  (STBImage/stbi_set_flip_vertically_on_load false)
  (let [{:keys [w h]} (-> game :config :window-conf)
        screen1       (offscreen/prep-offscreen-render w h)]
    (println "init game")
    (-> game
        (assoc :screen1 screen1)
        (imgui/init))))

(defn rendering-zone [game]
  (let [{:keys [config screen1]} game
        {:keys [w h]}            (:window-conf config)]
    (GL45/glBlendFunc GL45/GL_SRC_ALPHA GL45/GL_ONE_MINUS_SRC_ALPHA)
    (GL45/glClearColor 1.0 1.0 1.0 1.0)
    (GL45/glClear (bit-or GL45/GL_COLOR_BUFFER_BIT GL45/GL_DEPTH_BUFFER_BIT))

    (imgui/frame game)
    (GL45/glBindFramebuffer GL45/GL_FRAMEBUFFER (:fbo screen1))

    (GL45/glClearColor 0.0 0.0 0.0 0.0)
    (GL45/glClear (bit-or GL45/GL_COLOR_BUFFER_BIT GL45/GL_DEPTH_BUFFER_BIT))
    (GL45/glViewport 0 0 w h)
    (let [world   (::world/this game)
          renders (o/query-all world ::model-rendering/render-model-biasa)]
      (doseq [{:keys [render-fn] :as match} renders]
        (render-fn game match)))

    (thorvg/render game)
    (offscreen/render-fbo screen1 {:fbo 0 :width w :height h}
                          {:translation (v/vec3 0.0 0.0 0.0)
                           :scale       (v/vec3 1.0 1.0 1.0)}))
  game)

(defn destroy [game]
  (println "destroy game")
  (imgui/destroy game))
