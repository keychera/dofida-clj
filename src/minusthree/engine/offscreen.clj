(ns minusthree.engine.offscreen
  (:require
   [engine.macros :refer [vars->map]]
   [engine.math.primitives :refer [plane3d-uvs plane3d-vertices]]
   [fastmath.matrix :as mat :refer [mat->float-array]]
   [minusthree.engine.math :refer [scaling-mat translation-mat]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.texture :as texture]
   [minusthree.gl.constants :refer [GL_ARRAY_BUFFER GL_FLOAT GL_FRAMEBUFFER
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_STATIC_DRAW GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TRIANGLES]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]))

(def fbo-vs
  (str cljgl/version-str
       "
precision mediump float;

in vec3 a_pos;
in vec2 a_uv;

uniform mat4 u_model;

out vec2 uv;

void main() {
  gl_Position = u_model * vec4(a_pos, 1.0);
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

(defn prep-offscreen-render
  ([ctx width height] (prep-offscreen-render ctx width height {}))
  ([ctx width height conf]
   (let [fbo-data        (texture/cast-fbo-spell ctx width height conf)
         program-info    (cljgl/create-program-info-from-source ctx fbo-vs fbo-fs)
         fbo-program     (:program program-info)
         fbo-attr-loc    (gl ctx getAttribLocation fbo-program "a_pos")
         fbo-uv-attr-loc (gl ctx getAttribLocation fbo-program "a_uv")
         offscreen-vao   (gl ctx genVertexArrays)
         _               (gl ctx bindVertexArray offscreen-vao)
         offscreen-vbo   (cljgl/create-buffer ctx)
         _               (gl ctx bindBuffer GL_ARRAY_BUFFER offscreen-vbo)
         _               (gl ctx bufferData GL_ARRAY_BUFFER plane3d-vertices GL_STATIC_DRAW)

         off-uv-buffer   (cljgl/create-buffer ctx)
         _               (gl ctx bindBuffer GL_ARRAY_BUFFER off-uv-buffer)
         _               (gl ctx bufferData GL_ARRAY_BUFFER plane3d-uvs GL_STATIC_DRAW)]
     (gl ctx enableVertexAttribArray fbo-attr-loc)
     (gl ctx bindBuffer GL_ARRAY_BUFFER offscreen-vbo)
     (gl ctx vertexAttribPointer fbo-attr-loc 3 GL_FLOAT false 0 0)

     (gl ctx enableVertexAttribArray fbo-uv-attr-loc)
     (gl ctx bindBuffer GL_ARRAY_BUFFER off-uv-buffer)
     (gl ctx vertexAttribPointer fbo-uv-attr-loc 2 GL_FLOAT false 0 0)
     (merge fbo-data (vars->map program-info offscreen-vao offscreen-vbo)))))

(defn render-fbo
  [ctx
   {source-vao :offscreen-vao
    source-program :program-info
    source-fbo-tex :fbo-tex}
   {target-width :width
    target-height :height
    target-fbo :fbo
    target-color-attachment :color-attachment}
   {:keys [translation scale]}]
  (let [model (mat/mulm (scaling-mat scale)
                        (translation-mat translation))]
    (gl ctx bindFramebuffer GL_FRAMEBUFFER target-fbo)
    (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)

    (gl ctx viewport 0 0 target-width target-height)

    (gl ctx bindVertexArray source-vao)
    (gl ctx useProgram (:program source-program))

    (gl ctx activeTexture GL_TEXTURE0)
    (gl ctx bindTexture GL_TEXTURE_2D source-fbo-tex)
    (cljgl/set-uniform ctx source-program :u_model (mat->float-array model))
    (cljgl/set-uniform ctx source-program :u_tex 0)

    (when target-color-attachment
      (gl ctx drawBuffers (int-array [target-color-attachment])))
    (gl ctx drawArrays GL_TRIANGLES 0 6)))
