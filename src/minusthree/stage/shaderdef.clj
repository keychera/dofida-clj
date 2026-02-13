(ns minusthree.stage.shaderdef
  (:require
   [minusthree.gl.cljgl :as cljgl]))

(def MAX_JOINTS 500)

(def gltf-vert
  (str cljgl/version-str
       "
   precision mediump float;
   
   in vec3 POSITION;
   in vec3 NORMAL;
   in vec2 TEXCOORD_0;
   in vec4 WEIGHTS_0;
   in uvec4 JOINTS_0;
   
   uniform mat4 u_model;
   uniform mat4 u_view;
   uniform mat4 u_projection;
   layout(std140) uniform Skinning {
      mat4[" MAX_JOINTS "] u_joint_mats;
   };
   
   out vec3 Normal;
   out vec2 TexCoord;
  
   void main() {
     vec4 pos = vec4(POSITION, 1.0);
     mat4 skin = (WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) 
               + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) 
               + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z])
               + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]);
     gl_Position = u_projection * u_view * u_model * skin * pos;
     Normal = NORMAL;
     TexCoord = TEXCOORD_0;
   }
  "))

(def gltf-frag
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 Normal;
 in vec2 TexCoord;
 
 uniform sampler2D u_mat_diffuse;
 
 out vec4 o_color;

 void main() {
   o_color = vec4(texture(u_mat_diffuse, TexCoord).rgb, 1.0); 
 }
"))

(def wirecube-vert (str cljgl/version-str
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

(def wirecube-frag (str cljgl/version-str
             "
precision mediump float;

in vec3 Normal;
in vec2 TexCoord;
      
out vec4 o_color;

void main() {
  o_color = vec4(0.9, 0.2, 0.2, 0.7);
}"))
