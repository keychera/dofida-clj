#version 330
precision mediump float;
in vec2 uv;
uniform sampler2D u_tex;
out vec4 o_color;

void main() {
  o_color = texture(u_tex, uv);
}