(ns minusthree.stage.wirecube
  (:require
   [minusthree.gl.cljgl :as cljgl]))

(def vs (str cljgl/version-str
             "
precision mediump float;

in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;

uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;

out vec3 Normal;
out vec2 TexCoord;

void main() {
  gl_Position = u_projection * u_view * u_model * vec4(POSITION, 1.0);
  Normal = NORMAL;
  TexCoord = TEXCOORD_0;
}"))

(def fs (str cljgl/version-str
             "
precision mediump float;

in vec3 Normal;
in vec2 TexCoord;
      
out vec4 o_color;

void main() {
  o_color = vec4(0.9, 0.2, 0.2, 0.7);
}"))
