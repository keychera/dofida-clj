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

(comment
  (let [shader
        "uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
uniform mat4[500] u_joint_mats;
layout(std140) uniform matrix {
    mat4 mvp;
} mat;


in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;
in vec4 WEIGHTS_0;
in uvec4 JOINTS_0;
out vec3 Normal;
out vec2 TexCoords;"
        ^:vibe shader-grammar
        "
Blocks = (Block WS*)+

Block = InDecl
      | OutDecl
      | UniformDecl
      | InterfaceBlockDecl

MemberDecl = Type WS Ident WS* <';'>
InDecl = 'in' WS MemberDecl
OutDecl = 'out' WS MemberDecl
UniformDecl = 'uniform' WS MemberDecl

InterfaceBlockDecl = 'layout' WS* <'('> Ident <')'> WS StorageQualifier WS BlockName WS
         <'{'> WS*
         (MemberDecl WS*)+
         <'}'> WS InstanceName <';'>
         
         StorageQualifier = 'uniform'
         BlockName = Ident
         InstanceName = Ident

WS = #'\\s+'
Type = 'u'* ('vec'|'mat') ('2'|'3'|'4') ArraySpec*
ArraySpec = <'['> Number <']'>

Ident = #'[a-zA-Z_][a-zA-Z0-9_]*'
Number = #'[0-9]+'
"
        parser (insta/parser shader-grammar)
        tree (parser shader)]
    (walk/postwalk
     (fn [node] (when-not (and (vector? node) (= (first node) :WS)) node))
     tree))

  :-)