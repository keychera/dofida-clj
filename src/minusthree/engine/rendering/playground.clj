(ns minusthree.engine.rendering.playground
  (:require
   [com.phronemophobic.viscous :as viscous]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loader :as loader]
   [minusthree.engine.rendering.par-streamlines :refer [parsl-context||]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.texture :as texture]
   [minusthree.gl.gl-magic :as gl-magic])
  (:import
   [box2d b2d]
   [java.lang.foreign Arena MemoryLayout]
   [org.lwjgl.opengl GL45]
   [thorvg tvg]))

(defonce _loadlib
  (do (loader/load-libs "box2dd")
      (loader/load-libs "libthorvg-1")))

;; https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/MemorySegment.html
;; https://www.thorvg.org/native-tutorial
;; thorvg capi https://github.com/thorvg/thorvg.example/blob/main/src/Capi.cpp

;; redundant, copied from offscreen.clj, TODO refactor later
(def plane3d-vertices
  (float-array
   [-1.0 -1.0 0.0,  1.0 -1.0 0.0, -1.0  1.0 0.0,
    -1.0  1.0 0.0,  1.0 -1.0 0.0,  1.0  1.0 0.0]))

(def plane3d-uvs
  (float-array
   [0.0 0.0, 1.0 0.0, 0.0 1.0,
    0.0 1.0, 1.0 0.0, 1.0 1.0]))

(def fbo-vs
  (str cljgl/version-str
       "
precision mediump float;
in vec3 a_pos;
in vec2 a_uv;
out vec2 uv;

void main() {
  gl_Position = vec4(a_pos, 1.0);
  uv = a_uv;
}"))

(def fbo-fs
  (str cljgl/version-str
       "
precision mediump float;
in vec2 uv;
uniform sampler2D u_tex;
out vec4 o_color;

void main() {
  o_color = texture(u_tex, uv);
}"))

(defn init [{:keys [::arena/game-arena] :as game}]
  (tvg/tvg_engine_init 4)
  (let [WIDTH        400
        HEIGHT       400
        buffer||     (.allocate ^Arena game-arena (MemoryLayout/sequenceLayout (* WIDTH HEIGHT) tvg/C_INT))
        canvas||     (doto (tvg/tvg_swcanvas_create (tvg/TVG_ENGINE_OPTION_DEFAULT))
                       (tvg/tvg_swcanvas_set_target buffer|| WIDTH WIDTH HEIGHT (tvg/TVG_COLORSPACE_ABGR8888)))
        rect||       (doto (tvg/tvg_shape_new)
                       (tvg/tvg_shape_append_rect #_x-y 100 100 #_w-h 100 100 #_rx-ry 15 15 #_cw? false)
                       ;; unsure why the color is dimmed rn
                       (tvg/tvg_shape_set_fill_color #_rgba 90 127 90 110))  ;; not 255 since java use unsigned bytes,  -128 .. 127 
        _            (doto canvas||
                       (tvg/tvg_canvas_add rect||)
                       (tvg/tvg_canvas_update)
                       (tvg/tvg_canvas_draw #_clear true)
                       (tvg/tvg_canvas_sync))
        thor-tex     (texture/create-texture (.asByteBuffer buffer||) WIDTH HEIGHT)
        program-info (cljgl/create-program-info-from-source fbo-vs fbo-fs)
        gl-summons   (gl-magic/cast-spell
                      [{:bind-vao "thor"}
                       {:buffer-data plane3d-vertices :buffer-type GL45/GL_ARRAY_BUFFER}
                       {:point-attr :a_pos :use-shader program-info :count 3 :component-type GL45/GL_FLOAT}
                       {:buffer-data plane3d-uvs :buffer-type GL45/GL_ARRAY_BUFFER}
                       {:point-attr :a_uv :use-shader program-info :count 2 :component-type GL45/GL_FLOAT}
                       {:unbind-vao true}])
        vao          (-> gl-summons ::gl-magic/data ::gl-magic/vao (get "thor"))]
    (println "playground ready!")
    #_{:clj-kondo/ignore [:inline-def]}
    (def debug-var (vec (.toArray buffer|| tvg/C_INT)))
    (assoc game ::program-info program-info ::vao vao ::thor-tex thor-tex)))

(defn render [{::keys [program-info vao thor-tex]}]
  (let [program (:program program-info)]
    (GL45/glUseProgram program)
    (GL45/glBindVertexArray vao)
    
    (GL45/glActiveTexture GL45/GL_TEXTURE0)
    (GL45/glBindTexture GL45/GL_TEXTURE_2D thor-tex)
    (cljgl/set-uniform program-info :u_tex 0)
    (GL45/glDrawArrays GL45/GL_TRIANGLES 0 6)))

(defn destroy [{::keys [thor-tex]}]
  (println "destroy playground")
  (GL45/glDeleteTextures thor-tex)
  (tvg/tvg_engine_term))

(comment
  (viscous/inspect debug-var)

  (eduction
   (filter (comp not zero?))
   (take 64)
   (map (fn [rgba] (map #(bit-and 0xff %)
                        [rgba
                         (bit-shift-right rgba 8)
                         (bit-shift-right rgba 16)
                         (bit-shift-right rgba 24)])))
   debug-var)

  (loader/load-libs "par_streamlines")
  (with-open [arena (Arena/ofConfined)]
    (let [a (parsl-context|| arena)]
      (println (type a))))

  (loader/load-libs "box2dd")
  (type b2d)
  (with-open [arena (Arena/ofConfined)]
    (let [a (b2d/b2DefaultWorldDef arena)]
      (println (type a))))

  (type tvg)
  (loader/load-libs "libthorvg-1")
  (tvg/tvg_engine_init 4)
  (tvg/tvg_engine_term)

  :-)
