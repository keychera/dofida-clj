(ns minustwo.gl.shader
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::type #{'float 'vec2 'vec3 'vec4 'mat2 'mat3 'mat4})
(s/def ::attr-loc int?)
(s/def ::uni-loc #?(:clj int? :cljs #(instance? js/WebGLUniformLocation %)))
(s/def ::program #?(:clj int? :cljs #(instance? js/WebGLProgram %)))

(s/def ::attr (s/keys :req-un [::type ::attr-loc]))
(s/def ::attr-locs (s/map-of symbol? ::attr))
(s/def ::uni (s/keys :req-un [::type ::uni-loc]))
(s/def ::uni-locs (s/map-of symbol? ::uni))

(s/def ::program-info (s/keys :req-un [::program ::attr-locs ::uni-locs]))
(s/def ::all (s/map-of some? ::program-info))