(ns minusthree.engine.rendering.playground
  (:require
   [com.phronemophobic.viscous :as viscous]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loader :as loader]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader]
   [minusthree.gl.texture :as texture])
  (:import
   [box2d b2d b2WorldDef b2Vec2 b2Rot b2WorldId b2BodyDef b2ShapeDef b2SurfaceMaterial]
   [java.lang.foreign Arena MemoryLayout]
   [org.lwjgl.opengl GL45]
   [thorvg tvg]))

(set! *warn-on-reflection* true)

(defonce _loadlib
  (do (loader/load-libs "box2dd")
      (loader/load-libs "libthorvg-1")))

;; https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/MemorySegment.html

;; redundant copied from offscreen.clj TODO refactor later
(def plane3d-vertices
  (float-array
   [-1.0 -1.0 0.0  1.0 -1.0 0.0 -1.0  1.0 0.0
    -1.0  1.0 0.0  1.0 -1.0 0.0  1.0  1.0 0.0]))

(def plane3d-uvs
  (float-array
   [0.0 0.0 1.0 0.0 0.0 1.0
    0.0 1.0 1.0 0.0 1.0 1.0]))

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

(defn b2dvec2 [arena x y]
  (doto (b2Vec2/allocate arena) (b2Vec2/x x) (b2Vec2/y y)))

(defn ub [v]
  (unchecked-byte v))

(defn init [{:keys [::arena/game-arena] :as game}]
  (with-open [ia #_init-arena (Arena/ofConfined)]
    (tvg/tvg_engine_init 4)
    (let [WIDTH        400
          HEIGHT       400

          ;; box2d https://box2d.org/documentation/hello.html
          ;; some use init-arena because, following box2d tutorial, the values are copied and not needed anymore
          ;; therefore safe to dealloc, which is done automatically after exiting ia with-open scope
          b2d-world-id|| (let [b2-vec2||   (b2dvec2 ia 0.0 -10.0)
                               world-def|| (doto (b2d/b2DefaultWorldDef ia)
                                             (b2WorldDef/gravity b2-vec2||))]
                           (b2d/b2CreateWorld game-arena world-def||))

          ground-id||    (let [b2-vec2||     (b2dvec2 ia 0.0 -20.0)
                               b2-body-def|| (doto (b2d/b2DefaultBodyDef ia)
                                               (b2BodyDef/position b2-vec2||))]
                           (b2d/b2CreateBody game-arena b2d-world-id|| b2-body-def||))
          _               (let [ground-box||       (b2d/b2MakeBox ia 50.0 10.0)
                                ground-shape-def|| (b2d/b2DefaultShapeDef ia)]
                            (b2d/b2CreatePolygonShape game-arena ground-id|| ground-shape-def|| ground-box||))

          body-id||      (let [b2-body-def|| (doto (b2d/b2DefaultBodyDef ia)
                                               (b2BodyDef/type (b2d/b2_dynamicBody))
                                               (b2BodyDef/position (b2dvec2 ia 0.0 4.0)))]
                           (b2d/b2CreateBody game-arena b2d-world-id|| b2-body-def||))
          _              (let [dynamic-box|| (b2d/b2MakeBox ia 1.0 1.0)
                               shape-def||   (doto (b2d/b2DefaultShapeDef ia)
                                               (b2ShapeDef/density 1.0)
                                               (-> (b2ShapeDef/material) (b2SurfaceMaterial/friction 0.3)))]
                           (b2d/b2CreatePolygonShape game-arena body-id|| shape-def|| dynamic-box||))

          ;; thorvg https://www.thorvg.org/native-tutorial
          buffer||       (.allocate ^Arena game-arena (MemoryLayout/sequenceLayout (* WIDTH HEIGHT) tvg/C_INT))
          canvas||       (doto (tvg/tvg_swcanvas_create (tvg/TVG_ENGINE_OPTION_DEFAULT))
                           (tvg/tvg_swcanvas_set_target buffer|| WIDTH WIDTH HEIGHT (tvg/TVG_COLORSPACE_ABGR8888S)))
          rect||         (doto (tvg/tvg_shape_new)
                           (tvg/tvg_shape_append_rect #_x-y 100 100 #_w-h 100 100 #_rx-ry 15 15 #_cw? false)
                           ;; unsure why the color is dimmed rn
                           (tvg/tvg_shape_set_fill_color #_rgba (ub 255) (ub 255) (ub 255) (ub 255)))
          _              (doto canvas||
                           (tvg/tvg_canvas_add rect||)
                           (tvg/tvg_canvas_update)
                           (tvg/tvg_canvas_draw #_clear true)
                           (tvg/tvg_canvas_sync))
          thor-tex       (texture/create-texture (.asByteBuffer buffer||) WIDTH HEIGHT)

          ;; gl stuff  
          program-info   (cljgl/create-program-info-from-source fbo-vs fbo-fs)
          gl-summons     (gl-magic/cast-spell
                          [{:bind-vao "thor"}
                           {:buffer-data plane3d-vertices :buffer-type GL45/GL_ARRAY_BUFFER}
                           {:point-attr :a_pos :use-shader program-info :count 3 :component-type GL45/GL_FLOAT}
                           {:buffer-data plane3d-uvs :buffer-type GL45/GL_ARRAY_BUFFER}
                           {:point-attr :a_uv :use-shader program-info :count 2 :component-type GL45/GL_FLOAT}
                           {:unbind-vao true}])
          vao          (-> gl-summons ::gl-magic/data ::gl-magic/vao (get "thor"))
          id->buffer   (-> gl-summons ::gl-magic/data ::shader/buffer)]
      (println "box2d index:" (b2WorldId/index1 b2d-world-id||) ", generation:" (b2WorldId/generation b2d-world-id||))
      (println "playground ready!")
      #_{:clj-kondo/ignore [:inline-def]}
      (def debug-var (vec (.toArray buffer|| tvg/C_INT)))
      (assoc game
             ;; box2d
             ::b2d-world-id|| b2d-world-id|| ::body-id|| body-id||
             ;; thorvg
             ::buffer|| buffer|| ::canvas|| canvas|| ::rect|| rect||
             ;; gl
             ::program-info program-info ::vao vao ::thor-tex thor-tex ::id->buffer id->buffer
             ::WIDTH WIDTH ::HEIGHT HEIGHT))))

(defn b2Rot->angle [segment]
  ;; https://github.com/manuelbl/JavaDoesUSB/tree/main/java-does-usb/jextract
  ;; inline function not extracted ---> we inline them ourself!
  ;; return b2Atan2( q.s, q.c );
  (Math/atan2 (b2Rot/s segment) (b2Rot/c segment)))

(defn render [{::keys [program-info vao thor-tex
                       b2d-world-id|| body-id||
                       buffer|| canvas|| rect||
                       WIDTH HEIGHT]
               game-arena ::arena/game-arena}]
  (let [program (:program program-info)]
    (b2d/b2World_Step b2d-world-id|| 1/60 4)

    (let [b2vec2|| (b2d/b2Body_GetPosition game-arena body-id||)
          b2rot||  (b2d/b2Body_GetRotation game-arena body-id||)
          x        (b2Vec2/x b2vec2||)
          y        (b2Vec2/y b2vec2||)
          rot      (b2Rot->angle b2rot||)]
      (doto rect||
        (tvg/tvg_paint_rotate rot)
        (tvg/tvg_paint_translate (+ 100 (* 10 x)) (+ 100 (* 10 y)))))

    (doto canvas||
      (tvg/tvg_canvas_update)
      (tvg/tvg_canvas_draw #_clear false)
      (tvg/tvg_canvas_sync))

    (GL45/glUseProgram program)
    (GL45/glBindVertexArray vao)

    (GL45/glActiveTexture GL45/GL_TEXTURE0)
    (GL45/glBindTexture GL45/GL_TEXTURE_2D thor-tex)
    (GL45/glTexSubImage2D GL45/GL_TEXTURE_2D 0 0 0 WIDTH HEIGHT GL45/GL_RGBA GL45/GL_UNSIGNED_BYTE (.asByteBuffer buffer||))

    (cljgl/set-uniform program-info :u_tex 0)
    (GL45/glDrawArrays GL45/GL_TRIANGLES 0 6)))

(defn destroy [{::keys [id->buffer program-info vao thor-tex
                        b2d-world-id||]}]
  (println "destroy playground")
  (tvg/tvg_engine_term)
  (b2d/b2DestroyWorld b2d-world-id||)
  (doseq [buf (vals id->buffer)]
    (GL45/glDeleteBuffers buf))
  (GL45/glDeleteTextures thor-tex)
  (GL45/glDeleteBuffers vao)
  (GL45/glDeleteProgram (:program program-info)))

(comment
  (viscous/inspect debug-var)

  (eduction
   (filter (comp not zero?))
   (drop 128)
   (take 64)
   (map (fn [rgba] (map #(bit-and 0xff %)
                        [rgba
                         (bit-shift-right rgba 8)
                         (bit-shift-right rgba 16)
                         (bit-shift-right rgba 24)])))
   debug-var)

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
