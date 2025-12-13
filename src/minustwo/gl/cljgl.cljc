(ns minustwo.gl.cljgl
  #_{:clj-kondo/ignore [:unused-referred-var]}
  (:require
   #?(:clj [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [iglu.core :as iglu]
   [minustwo.gl.gl :as gl]
   [minustwo.gl.constants :refer [GL_COMPILE_STATUS GL_FRAGMENT_SHADER
                                  GL_LINK_STATUS GL_TRUE GL_VERTEX_SHADER]]
   [minustwo.gl.shader :as shader]))

(def glsl-version #?(:clj "330" :cljs "300 es"))

(defn create-buffer [ctx] (gl ctx #?(:clj genBuffers :cljs createBuffer)))

(defn create-shader [ctx type source]
  (let [shader (gl ctx createShader type)]
    (gl ctx shaderSource shader source)
    (gl ctx compileShader shader)
    (if #?(:clj (= GL_TRUE (gl ctx getShaderi shader GL_COMPILE_STATUS))
           :cljs (gl ctx getShaderParameter shader GL_COMPILE_STATUS))
      shader
      (throw (ex-info (gl ctx getShaderInfoLog shader) {})))))

(s/fdef create-program
  :args (s/cat :ctx ::gl/context
               :vs-source string?
               :fs-source string?)
  :ret ::shader/program)
(defn create-program [ctx vs-source fs-source]
  (let [vertex-shader   (create-shader ctx GL_VERTEX_SHADER vs-source)
        fragment-shader (create-shader ctx GL_FRAGMENT_SHADER fs-source)
        program         (gl ctx createProgram)]
    (gl ctx attachShader program vertex-shader)
    (gl ctx attachShader program fragment-shader)
    (gl ctx linkProgram program)
    (gl ctx deleteShader vertex-shader)
    (gl ctx deleteShader fragment-shader)
    (if #?(:clj (= GL_TRUE (gl ctx getProgrami program GL_LINK_STATUS))
           :cljs (gl ctx getProgramParameter program GL_LINK_STATUS))
      program
      (throw (ex-info (gl ctx getProgramInfoLog program) {})))))

(s/fdef gather-locs
  :args (s/cat :ctx ::gl/context
               :program ::shader/program
               :iglu-vert-shader map?
               :iglu-frag-shader map?)
  :ret map?)
(defn gather-locs [ctx program iglu-vert-shader iglu-frag-shader]
  (let [both-shader [iglu-vert-shader iglu-frag-shader]
        attr-locs   (->> (transduce (map :inputs) merge both-shader)
                         (into {} (map (fn [[attr-name attr-type]]
                                         [attr-name {:type     attr-type
                                                     :attr-loc (gl ctx getAttribLocation program (str attr-name))}]))))
        uni-locs    (->> (transduce (map :uniforms) merge both-shader)
                         (into {} (map (fn [[uni-name uni-type]]
                                         [uni-name {:type    uni-type
                                                    :uni-loc (gl ctx getUniformLocation program (str uni-name))}]))))]
    (vars->map attr-locs uni-locs)))

(s/fdef create-program-info
  :args (s/cat :ctx ::gl/context
               :iglu-vert-shader map?
               :iglu-frag-shader map?)
  :ret ::shader/program-info)
(defn create-program-info
  ([ctx iglu-vert-shader iglu-frag-shader]
   (let [vs-source (iglu/iglu->glsl (merge {:version glsl-version} iglu-vert-shader))
         fs-source (iglu/iglu->glsl (merge {:version glsl-version} iglu-frag-shader))
         program   (create-program ctx vs-source fs-source)
         locs      (gather-locs ctx program iglu-vert-shader iglu-frag-shader)]
     (merge {:program program} locs))))

(s/fdef set-uniform
  :args (s/cat :ctx ::gl/context
               :program-info ::shader/program-info
               :loc-symbol symbol?
               :value any?))
(defn set-uniform [ctx program-info loc-symbol value]
  (if-let [{:keys [uni-loc type]} (get (:uni-locs program-info) loc-symbol)]
    (condp = type
      'float (gl ctx uniform1f uni-loc value)
      'vec2  (gl ctx uniform2fv uni-loc value)
      'vec3  (gl ctx uniform3fv uni-loc value)
      'vec4  (gl ctx uniform4fv uni-loc value)
      'mat2  (gl ctx uniformMatrix2fv uni-loc false value)
      'mat3  (gl ctx uniformMatrix3fv uni-loc false value)
      'mat4  (gl ctx uniformMatrix4fv uni-loc false value))
    (throw (ex-info (str "no " loc-symbol " found in program")
                    {:program-info program-info}))))
