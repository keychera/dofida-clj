(ns minustwo.stage.pseudo.offscreen
  (:require
   #?(:clj [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.macros :refer [vars->map]]
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_ATTACHMENT0
                                  GL_FLOAT GL_FRAMEBUFFER
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_STATIC_DRAW GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TRIANGLES]]
   [minustwo.gl.texture :as texture]
   [rules.primitives :refer [plane3d-uvs plane3d-vertices]]
   [thi.ng.math.core :as m]))

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
  ([ctx width height tex-unit] (prep-offscreen-render ctx width height tex-unit {}))
  ([ctx width height tex-unit conf]
   (let [fbo-data        (texture/cast-fbo-spell ctx width height tex-unit conf)
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
  [{source-vao :offscreen-vao
    source-program :program-info
    source-tex-unit :tex-unit
    source-fbo-tex :fbo-tex}
   {target-width :width 
    target-height :height
    target-fbo :fbo
    target-color-attachment :color-attachment
    :or {target-color-attachment GL_COLOR_ATTACHMENT0}}
   {:keys [translation scale]}]
  (fn [_world ctx]
    (let [model (m/* (m-ext/vec3->trans-mat translation)
                     (m-ext/vec3->scaling-mat scale))]
      (gl ctx bindFramebuffer GL_FRAMEBUFFER target-fbo)
      (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)

      (gl ctx viewport 0 0 target-width target-height)

      (gl ctx bindVertexArray source-vao)
      (gl ctx useProgram (:program source-program))

      (gl ctx activeTexture (+ GL_TEXTURE0 source-tex-unit))
      (gl ctx bindTexture GL_TEXTURE_2D source-fbo-tex)
      (cljgl/set-uniform ctx source-program :u_model (vec->f32-arr (vec model)))
      (cljgl/set-uniform ctx source-program :u_tex source-tex-unit)

      (gl ctx drawBuffers (int-array [target-color-attachment]))
      (gl ctx drawArrays GL_TRIANGLES 0 6))))
