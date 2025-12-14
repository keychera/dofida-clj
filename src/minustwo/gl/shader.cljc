(ns minustwo.gl.shader
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::glsl-type #{'float 'int 'uint 'bool
                     'vec2 'vec3 'vec4
                     'ivec2 'ivec3 'ivec4
                     'uvec2 'uvec3 'uvec4
                     'bvec2 'bvec3 'bvec4
                     'mat2 'mat3 'mat4
                     'sampler2D})
(s/def ::type (s/or :raw-type ::glsl-type
                    :arr-type (s/cat :type ::glsl-type :dimension int?)))
(s/def ::attr-loc int?)
(s/def ::uni-loc #?(:clj int? :cljs #(instance? js/WebGLUniformLocation %)))
(s/def ::program #?(:clj int? :cljs #(instance? js/WebGLProgram %)))

(s/def ::attr (s/keys :req-un [::type ::attr-loc]))
(s/def ::attr-locs (s/map-of symbol? ::attr))
(s/def ::uni (s/keys :req-un [::type ::uni-loc]))
(s/def ::uni-locs (s/map-of symbol? ::uni))

(s/def ::program-info (s/keys :req-un [::program ::attr-locs ::uni-locs]))
(s/def ::all (s/map-of some? ::program-info))
(s/def ::use some?)