(ns minustwo.gl.shader
  (:require
   [clojure.spec.alpha :as s]
   [clojure.walk :as walk]
   [instaparse.core :as insta]))

(s/def ::glsl-type #{'float 'int 'uint 'bool
                     'vec2 'vec3 'vec4
                     'ivec2 'ivec3 'ivec4
                     'uvec2 'uvec3 'uvec4
                     'bvec2 'bvec3 'bvec4
                     'mat2 'mat3 'mat4
                     'sampler2D})
(s/def ::type (s/or :raw-type ::glsl-type
                    :arr-type (s/cat :type ::glsl-type :dimension int?)))

(s/def ::program #?(:clj int? :cljs #(instance? js/WebGLProgram %)))

(s/def ::attr-loc int?)
(s/def ::attr (s/keys :req-un [::type ::attr-loc]))
(s/def ::attr-locs (s/map-of symbol? ::attr))

(s/def ::uni-loc #?(:clj int? :cljs #(instance? js/WebGLUniformLocation %)))
(s/def ::uni (s/keys :req-un [::type ::uni-loc]))
(s/def ::uni-locs (s/map-of symbol? ::uni))

(s/def ::uniform-block-spec some?)

(s/def ::program-info (s/keys :req-un [::program ::attr-locs ::uni-locs]
                              :opt-un [::uniform-block-spec]))
(s/def ::all (s/map-of some? ::program-info))
(s/def ::use some?)

(def shader-header-grammar
  "
Shader = StartParse Header Body
StartParse = #'(.|\\s)*precision mediump float;\\s*'
Header = Block+
Body = #'(void|(u*(vec|mat)[234]))\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*\\((.|\\s)*' 

Block = InDecl
      | OutDecl
      | UniformDecl
      | InterfaceBlockDecl

MemberDecl = TypeDecl MemberName <';'>
MemberName = Ident
InDecl = 'in' MemberDecl
OutDecl = 'out' MemberDecl
UniformDecl = 'uniform' MemberDecl

InterfaceBlockDecl = 'layout' <'('> Ident <')'> StorageQualifier BlockName
  <'{'> MemberDecl+ <'}'> InstanceName? <';'>
StorageQualifier = 'uniform'
BlockName = Ident
InstanceName = Ident

TypeDecl = TypeSpec ArraySpec?

TypeSpec = #'u*(vec|mat)[234]'
ArraySpec = <'['> Number <']'>
Ident = #'[a-zA-Z_][a-zA-Z0-9_]*'
Number = #'[0-9]+'
")

(def ^:private whitespace (insta/parser "whitespace = #'\\s+'"))
(def ^:private parser (insta/parser shader-header-grammar :auto-whitespace whitespace))

(defn parse-header [source]
  (let [tree (parser source)
        tree (insta/transform
              (letfn [(qualify [qualifier member]
                        (merge {:qualifier (keyword qualifier)} member))]
                {:TypeSpec    symbol
                 :MemberName  (fn [[_ member-name]] [:member-name member-name])
                 :ArraySpec   (comp #?(:clj Integer/parseInt :cljs js/parseInt) second)
                 :TypeDecl    (fn [& nodes]
                                (let [member-type (into [] nodes)
                                      member-type (if (= (count member-type) 1) (first member-type) member-type)]
                                  (vector :member-type member-type)))
                 :MemberDecl  (fn [& nodes] (into {} nodes))
                 :UniformDecl qualify
                 :InDecl      qualify
                 :OutDecl     qualify
                 :Header      (fn [& blocks] [:Header (into [] (map second) blocks)])
                 :Shader      (fn [& blocks] (into {} blocks))})
              tree)]
    (:Header tree)))

(comment
  (let [shader
        "#version 300 es
precision mediump float;
uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
uniform mat4[500] u_joint_mats;
layout(std140) uniform matrix {
    mat4 mvp;
} mat;
layout(std140) uniform Skinning {
    mat4[500] u_joint_mats;
};
in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;
in vec4 WEIGHTS_0;
in uvec4 JOINTS_0;
out vec3 Normal;
out vec2 TexCoords;
void main()
{
  vec4 pos = (vec4(POSITION, 1.0));
  mat4 skin_mat = ((WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z]) + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]));
  vec4 world_pos = (u_model * skin_mat * pos);
  vec4 cam_pos = (u_view * world_pos);
  gl_Position = (u_projection * cam_pos);
  Normal = NORMAL;
  TexCoords = TEXCOORD_0;
}"]
    (parse-header shader))

  :-)