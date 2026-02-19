(ns minusthree.engine.rendering
  (:require
   [fastmath.vector :as v]
   [minusthree.engine.offscreen :as offscreen]
   [minusthree.engine.rendering.imgui :as imgui]
   [minusthree.engine.world :as world]
   [minusthree.model.model-rendering :as model-rendering]
   [minusthree.gl.constants :refer [GL_BLEND GL_COLOR_BUFFER_BIT GL_CULL_FACE
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_FRAMEBUFFER GL_MULTISAMPLE
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA]]
   [minusthree.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.stb STBImage]))

(defn init [game]
  (let [ctx nil]
    (gl ctx enable GL_BLEND)
    (gl ctx enable GL_CULL_FACE)
    (gl ctx enable GL_MULTISAMPLE)
    (gl ctx enable GL_DEPTH_TEST)
    (STBImage/stbi_set_flip_vertically_on_load false)
    (let [{:keys [w h]} (-> game :config :window-conf)
          screen1       (offscreen/prep-offscreen-render ctx w h)]
      (println "init game")
      (-> game
          (assoc :screen1 screen1)
          (imgui/init)))))

(defn rendering-zone [game]
  (let [ctx                      nil
        {:keys [config screen1]} game
        {:keys [w h]}            (:window-conf config)]
    (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl ctx clearColor 0.02 0.02 0.12 1.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))

    (imgui/frame game)

    (gl ctx bindFramebuffer GL_FRAMEBUFFER (:fbo screen1))
    (gl ctx clearColor 0.0 0.0 0.0 0.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl ctx viewport 0 0 w h)

    (let [world   (::world/this game)
          renders (o/query-all world ::model-rendering/render-model-biasa)]
      (doseq [{:keys [render-fn] :as match} renders]
        (render-fn game match)))
    

    (offscreen/render-fbo ctx screen1 {:fbo 0 :width w :height h}
                          {:translation (v/vec3 0.0 0.0 0.0)
                           :scale       (v/vec3 1.0 1.0 1.0)}))
  game)

(defn destroy [game]
  (println "destroy game")
  (imgui/destroy game))
