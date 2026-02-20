(ns minusthree.engine.offscreen
  (:require
   [fastmath.matrix :as mat :refer [mat->float-array]]
   [minusthree.engine.macros :refer [vars->map]]
   [minusthree.engine.math :refer [scaling-mat translation-mat]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.texture :as texture])
  (:import
   [org.lwjgl.opengl GL45]))

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
  ([width height] (prep-offscreen-render width height {}))
  ([width height conf]
   (let [fbo-data        (texture/cast-fbo-spell width height conf)
         program-info    (cljgl/create-program-info-from-source fbo-vs fbo-fs)
         fbo-program     (:program program-info)
         fbo-attr-loc    (GL45/glGetAttribLocation fbo-program "a_pos")
         fbo-uv-attr-loc (GL45/glGetAttribLocation fbo-program "a_uv")
         offscreen-vao   (GL45/glGenVertexArrays)
         _               (GL45/glBindVertexArray offscreen-vao)
         offscreen-vbo   (GL45/glGenBuffers)
         _               (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER offscreen-vbo)
         _               (GL45/glBufferData GL45/GL_ARRAY_BUFFER plane3d-vertices GL45/GL_STATIC_DRAW)

         off-uv-buffer   (GL45/glGenBuffers)
         _               (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER off-uv-buffer)
         _               (GL45/glBufferData GL45/GL_ARRAY_BUFFER plane3d-uvs GL45/GL_STATIC_DRAW)]
     (GL45/glEnableVertexAttribArray fbo-attr-loc)
     (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER offscreen-vbo)
     (GL45/glVertexAttribPointer fbo-attr-loc 3 GL45/GL_FLOAT false 0 0)

     (GL45/glEnableVertexAttribArray fbo-uv-attr-loc)
     (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER off-uv-buffer)
     (GL45/glVertexAttribPointer fbo-uv-attr-loc 2 GL45/GL_FLOAT false 0 0)
     (merge fbo-data (vars->map program-info offscreen-vao offscreen-vbo)))))

(defn render-fbo
  [{source-vao :offscreen-vao
    source-program :program-info
    source-fbo-tex :fbo-tex}
   {target-width :width
    target-height :height
    target-fbo :fbo
    target-color-attachment :color-attachment}
   {:keys [translation scale]}]
  (let [model (mat/mulm (scaling-mat scale)
                        (translation-mat translation))]
    (GL45/glBindFramebuffer GL45/GL_FRAMEBUFFER target-fbo)
    (GL45/glBlendFunc GL45/GL_SRC_ALPHA GL45/GL_ONE_MINUS_SRC_ALPHA)

    (GL45/glViewport 0 0 target-width target-height)

    (GL45/glBindVertexArray source-vao)
    (GL45/glUseProgram (:program source-program))

    (GL45/glActiveTexture GL45/GL_TEXTURE0)
    (GL45/glBindTexture GL45/GL_TEXTURE_2D source-fbo-tex)
    (cljgl/set-uniform source-program :u_model (mat->float-array model))
    (cljgl/set-uniform source-program :u_tex 0)

    (when target-color-attachment
      (GL45/glDrawBuffers (int-array [target-color-attachment])))
    (GL45/glDrawArrays GL45/GL_TRIANGLES 0 6)))
