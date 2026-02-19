(ns minusthree.gl.cljgl
  (:require
   [clojure.spec.alpha :as s]
   [iglu.core :as iglu]
   [minusthree.engine.macros :refer [vars->map]]
   [minusthree.gl.shader :as shader])
  (:import
   [org.lwjgl.opengl GL45]))

(s/def ::context any?)
(def glsl-version "330")
(def version-str (str "#version " glsl-version))

(defn create-buffer [ctx] (GL45/glGenBuffers))

(defn create-shader [ctx type source]
  (let [shader (GL45/glCreateShader type)]
    (GL45/glShaderSource shader source)
    (GL45/glCompileShader shader)
    (if (= GL45/GL_TRUE (GL45/glGetShaderi shader GL45/GL_COMPILE_STATUS))
      shader
      (throw (ex-info (GL45/glGetShaderInfoLog shader) {})))))

(s/fdef create-program
  :args (s/cat :ctx ::context
               :vs-source string?
               :fs-source string?)
  :ret ::shader/program)
(defn create-program [ctx vs-source fs-source]
  (let [vertex-shader   (create-shader ctx GL45/GL_VERTEX_SHADER vs-source)
        fragment-shader (create-shader ctx GL45/GL_FRAGMENT_SHADER fs-source)
        program         (GL45/glCreateProgram)]
    (GL45/glAttachShader program vertex-shader)
    (GL45/glAttachShader program fragment-shader)
    (GL45/glLinkProgram program)
    (GL45/glDeleteShader vertex-shader)
    (GL45/glDeleteShader fragment-shader)
    (if (= GL45/GL_TRUE (GL45/glGetProgrami program GL45/GL_LINK_STATUS))
      program
      (throw (ex-info (GL45/glGetProgramInfoLog program) {})))))

;; inspired by twgl.js interface
(s/fdef create-program-info-from-source
  :ret ::shader/program-info)
(defn create-program-info-from-source [ctx vs-source fs-source]
  (let [program    (create-program ctx vs-source fs-source)
        vs-members (-> vs-source shader/get-header-source shader/parse-header)
        fs-members (-> fs-source shader/get-header-source shader/parse-header)
        members    (concat vs-members fs-members)
        attr-locs  (->> members
                        (into {}
                              (comp (filter :in)
                                    (map (fn [{:keys [member-type member-name]}]
                                           [member-name {:type     member-type
                                                         :attr-loc (GL45/glGetAttribLocation program (name member-name))}])))))
        uni-locs   (->> members
                        (into {}
                              (comp (filter :uniform)
                                    (map (fn [{:keys [member-type member-name]}]
                                           [member-name {:type    member-type
                                                         :uni-loc (GL45/glGetUniformLocation program (name member-name))}])))))]
    (doseq [uni-block (filter :uniform-block members)]
      (let [{:keys [member-name]} uni-block
            block-index (GL45/glGetUniformBlockIndex program (name member-name))]
        ;; not sure why zero
        (GL45/glUniformBlockBinding program block-index 0)))
    {:program    program
     :attr-locs  attr-locs
     :uni-locs   uni-locs}))

(s/fdef gather-locs
  :args (s/cat :ctx ::context
               :program ::shader/program
               :iglu-vert-shader map?
               :iglu-frag-shader map?)
  :ret map?)
(defn gather-locs-from-iglu [ctx program iglu-vert-shader iglu-frag-shader]
  (let [both-shader [iglu-vert-shader iglu-frag-shader]
        attr-locs   (->> (transduce (map :inputs) merge both-shader)
                         (into {} (map (fn [[attr-name attr-type]]
                                         [(keyword attr-name)
                                          {:type     (keyword attr-type)
                                           :attr-loc (GL45/glGetAttribLocation program (str attr-name))}]))))
        uni-locs    (->> (transduce (map :uniforms) merge both-shader)
                         (into {} (map (fn [[uni-name uni-type]]
                                         [(keyword uni-name)
                                          {:type    (keyword uni-type)
                                           :uni-loc (GL45/glGetUniformLocation program (str uni-name))}]))))]
    (vars->map attr-locs uni-locs)))

(s/fdef create-program-info-from-iglu
  :args (s/cat :ctx any?
               :iglu-vert-shader map?
               :iglu-frag-shader map?)
  :ret ::shader/program-info)
(defn create-program-info-from-iglu
  ([ctx iglu-vert-shader iglu-frag-shader]
   (let [vs-source (or (:raw iglu-vert-shader)
                       (iglu/iglu->glsl (merge {:version glsl-version} iglu-vert-shader)))
         fs-source (or (:raw iglu-frag-shader)
                       (iglu/iglu->glsl (merge {:version glsl-version} iglu-frag-shader)))
         program   (create-program ctx vs-source fs-source)
         locs      (gather-locs-from-iglu ctx program iglu-vert-shader iglu-frag-shader)]
     (merge {:program program} locs))))

(s/fdef set-uniform
  :args (s/cat :ctx ::context
               :program-info ::shader/program-info
               :loc-keyword keyword?
               :value any?))
(defn set-uniform [ctx program-info loc-keyword value]
  (if-let [{:keys [uni-loc type]} (get (:uni-locs program-info) loc-keyword)]
    (case type
      :float     (GL45/glUniform1f uni-loc value)
      :int       (GL45/glUniform1i uni-loc value)
      :uint      (GL45/glUniform1ui uni-loc value)
      :bool      (GL45/glUniform1i uni-loc (if value 1 0))
      :vec2      (GL45/glUniform2fv uni-loc value)
      :vec3      (GL45/glUniform3fv uni-loc value)
      :vec4      (GL45/glUniform4fv uni-loc value)
      :ivec2     (GL45/glUniform2iv uni-loc value)
      :ivec3     (GL45/glUniform3iv uni-loc value)
      :ivec4     (GL45/glUniform4iv uni-loc value)
      :uvec2     (GL45/glUniform2uiv uni-loc value)
      :uvec3     (GL45/glUniform3uiv uni-loc value)
      :uvec4     (GL45/glUniform4uiv uni-loc value)
      :bvec2     (GL45/glUniform2iv uni-loc (mapv #(if % 1 0) value))
      :bvec3     (GL45/glUniform3iv uni-loc (mapv #(if % 1 0) value))
      :bvec4     (GL45/glUniform4iv uni-loc (mapv #(if % 1 0) value))
      :mat2      (GL45/glUniformMatrix2fv uni-loc false value)
      :mat3      (GL45/glUniformMatrix3fv uni-loc false value)
      :mat4      (GL45/glUniformMatrix4fv uni-loc false value)
      :sampler2D (GL45/glUniform1i uni-loc value)
      (throw (ex-info (str "Unsupported uniform type: " type) {:type type :loc-keyword loc-keyword})))
    (throw (ex-info (str "no " loc-keyword " found in program")
                    {:program-info program-info}))))
