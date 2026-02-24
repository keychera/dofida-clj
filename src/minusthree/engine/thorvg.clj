(ns minusthree.engine.thorvg
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.ffm.memory :as memory]
   [minusthree.engine.loader :as loader]
   [minusthree.engine.sugar :refer [ub]]
   [minusthree.engine.utils :as utils :refer [raw-from]]
   [minusthree.engine.world :as world]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture]
   [odoyle.rules :as o])
  (:import
   [java.lang.foreign Arena MemoryLayout]
   [org.lwjgl.opengl GL45]
   [thorvg tvg]))

(defonce _loadlib
  (loader/load-libs "libthorvg-1"))

(s/def ::tvg-arena ::arena/arena)
(s/def ::buffer|| ::memory/segment)
(s/def ::canvas|| ::memory/segment)

(s/def ::texture some?)
(s/def ::width number?)
(s/def ::height number?)
(s/def ::vao ::gl-magic/vao)
(s/def ::program-info ::shader/program-info)
(s/def ::render-data
  (s/keys :req [::program-info ::vao ::texture
                ::width ::height]))

(def plane3d-vertices
  (float-array
   [-1.0 -1.0 0.0  1.0 -1.0 0.0 -1.0  1.0 0.0
    -1.0  1.0 0.0  1.0 -1.0 0.0  1.0  1.0 0.0]))

(def plane3d-uvs
  (float-array
   [0.0 0.0 1.0 0.0 0.0 1.0
    0.0 1.0 1.0 0.0 1.0 1.0]))

(def fbo-vs (raw-from "minusthree/engine/rendering/playground.vert"))
(def fbo-fs (raw-from "minusthree/engine/rendering/playground.frag"))

(defn create-thor-gl []
  (let [program-info (cljgl/create-program-info-from-source fbo-vs fbo-fs)
        gl-summons     (gl-magic/cast-spell
                        [{:bind-vao "thor"}
                         {:buffer-data plane3d-vertices :buffer-type GL45/GL_ARRAY_BUFFER}
                         {:point-attr :a_pos :use-shader program-info :count 3 :component-type GL45/GL_FLOAT}
                         {:buffer-data plane3d-uvs :buffer-type GL45/GL_ARRAY_BUFFER}
                         {:point-attr :a_uv :use-shader program-info :count 2 :component-type GL45/GL_FLOAT}
                         {:unbind-vao true}])
        vao          (-> gl-summons ::gl-magic/data ::gl-magic/vao (get "thor"))
        id->buffer   (-> gl-summons ::gl-magic/data ::shader/buffer)]
    {::program-info program-info ::vao vao ::id->buffer id->buffer}))

(declare stuff-to-draw-to)

(defn after-refresh
  [new-world {config :config}]
  (tvg/tvg_engine_init 4)
  (let [tvg-arena (Arena/ofAuto)
        window   (-> config :window-conf)
        width    (or (:w window) 400)
        height   (or (:h window) 400)
        buffer|| (.allocate tvg-arena (MemoryLayout/sequenceLayout (* width height) tvg/C_INT))
        canvas|| (doto (tvg/tvg_swcanvas_create (tvg/TVG_ENGINE_OPTION_DEFAULT))
                   (tvg/tvg_swcanvas_set_target buffer|| width width height (tvg/TVG_COLORSPACE_ABGR8888S)))
        texture  (texture/create-texture (.asByteBuffer buffer||) width height)
        gl-data  (assoc (create-thor-gl)
                        ::texture texture
                        ::width width
                        ::height height)]
    (stuff-to-draw-to canvas||)
    (o/insert new-world
              ::tvg {::tvg-arena tvg-arena
                     ::buffer|| buffer||
                     ::canvas|| canvas||
                     ::render-data gl-data})))

(defn stuff-to-draw-to [canvas||]
  (let [rect|| (doto (tvg/tvg_shape_new)
                 (tvg/tvg_shape_append_rect #_x-y 100 100 #_w-h 100 100 #_rx-ry 15 15 #_cw? false)
                 (tvg/tvg_shape_set_fill_color #_rgba (ub 13) (ub 10) (ub 10) (ub 255)))
        path|| (doto (tvg/tvg_shape_new)
                 (tvg/tvg_shape_move_to 20 20)
                 (tvg/tvg_shape_line_to 45 45)
                 (tvg/tvg_shape_line_to 70 20)
                 (tvg/tvg_shape_close)
                 (tvg/tvg_shape_set_fill_color #_rgba (ub 13) (ub 10) (ub 10) (ub 255)))]
    (doto canvas||
      (tvg/tvg_canvas_add rect||)
      (tvg/tvg_canvas_add path||))))

(def rules
  (o/ruleset
   {::thorvg-canvas
    [:what
     [::tvg ::tvg-arena tvg-arena]
     [::tvg ::buffer|| buffer||]
     [::tvg ::canvas|| canvas||]
     [::tvg ::render-data render-data]]}))

;; I haven't been able to use thorvg glcanvas yet
(defn render-buffer-to-gl
  [buffer|| {::keys [program-info vao texture width height]}]
  (GL45/glUseProgram (:program program-info))
  (GL45/glBindVertexArray vao)

  (GL45/glActiveTexture GL45/GL_TEXTURE0)
  (GL45/glBindTexture GL45/GL_TEXTURE_2D texture)
  (GL45/glTexSubImage2D GL45/GL_TEXTURE_2D 0 0 0 width height GL45/GL_RGBA GL45/GL_UNSIGNED_BYTE (.asByteBuffer buffer||))

  (cljgl/set-uniform program-info :u_tex 0)
  (GL45/glDrawArrays GL45/GL_TRIANGLES 0 6))

(defn render [game]
  (let [{:keys [canvas|| buffer|| render-data]}
        (utils/query-one (::world/this game) ::thorvg-canvas)]
    #_{:clj-kondo/ignore [:inline-def]}
    (def debug-var (o/query-all (::world/this game) ::thorvg-canvas))
    (when canvas||
      (doto canvas||
        (tvg/tvg_canvas_update)
        (tvg/tvg_canvas_draw #_clear false)
        (tvg/tvg_canvas_sync))
      (render-buffer-to-gl buffer|| render-data)))
  game)

;; hmm I don't have guarantee yet that this will be called...
(defn before-refresh [old-world _old-game]
  (tvg/tvg_engine_term)
  (-> old-world
      (dissoc ::tvg-arena ::buffer|| ::canvas||)))

(def system
  {::world/after-refresh #'after-refresh
   ::world/rules #'rules
   ::world/before-refresh #'before-refresh})

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect debug-var)

  :-)
