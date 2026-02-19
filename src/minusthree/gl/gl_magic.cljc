(ns minusthree.gl.gl-magic
  (:require
   #?(:clj  [minusthree.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minusthree.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.core.match :as match]
   [clojure.spec.alpha :as s]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.texture :as texture]
   [minusthree.gl.constants :refer [GL_STATIC_DRAW GL_UNSIGNED_INT
                                  GL_UNSIGNED_SHORT]]
   [minusthree.gl.shader :as shader]))

(s/def ::casted? boolean?)
(s/def ::data some?)
(s/def ::facts some?)
(s/def ::vao some?)

(defn cast-spell [ctx esse-id spell-chants]
  (-> (reduce
       (fn [{:keys [state] :as magician} chant]
         (match/match [chant]
           [{:bind-vao _}] ;; entry: vao binding
           (let [vao (gl ctx #?(:clj genVertexArrays :cljs createVertexArray))]
             (gl ctx bindVertexArray vao)
             (assoc-in magician [::data ::vao (:bind-vao chant)] vao))

           [{:buffer-data _ :buffer-type _}] ;; entry: buffer binding
           (let [buffer (cljgl/create-buffer ctx)
                 buffer-type (:buffer-type chant)
                 buffer-data (:buffer-data chant)]
             (gl ctx bindBuffer buffer-type buffer)
             (gl ctx bufferData buffer-type buffer-data (or (:usage chant) GL_STATIC_DRAW))
             (cond-> magician
               (:buffer-name chant) (assoc-in [::data ::shader/buffer (:buffer-name chant)] buffer)
               true (update :state assoc :current-buffer buffer :buffer-type buffer-type)))

           [{:bind-current-buffer _}]
           (let [current-buffer (:current-buffer state)
                 buffer-type    (:buffer-type state)]
             (gl ctx bindBuffer buffer-type current-buffer)
             magician)

           ;; entry: attrib pointing, some keywords follows gltf accessor keys 
           [{:point-attr _ :count _ :component-type _ :use-shader _}]
           (try
             (s/assert ::shader/program-info (:use-shader chant))
             (if-let [attr-loc (get-in (:use-shader chant) [:attr-locs (:point-attr chant) :attr-loc])]
               (let [{:keys [count component-type stride offset] :or {stride 0 offset 0}} chant]
                 (condp = component-type
                   GL_UNSIGNED_SHORT (gl ctx vertexAttribIPointer attr-loc count component-type stride offset)
                   GL_UNSIGNED_INT   (gl ctx vertexAttribIPointer attr-loc count component-type stride offset)
                   (gl ctx vertexAttribPointer attr-loc count component-type false stride offset))
                 (gl ctx enableVertexAttribArray attr-loc)
                 magician)
               (update-in magician [::data ::err] conj (str "[error] attr-loc not found for chant:" chant)))
             (catch #?(:clj Exception :cljs js/Error) err
               (println "[error] in point-attr" (:point-attr chant) ", cause:" (:cause (Throwable->map err)))
               (update-in magician [::data ::err] conj err)))

           [{:bind-texture _ :image _}] ;; entry: texture binding
           (let [uri      (-> chant :image :uri)
                 tex-name (:bind-texture chant)]
             (update magician ::facts conj
                     [tex-name ::texture/uri-to-load uri]
                     [tex-name ::texture/for esse-id]))

           [{:unbind-vao _}]
           (do (gl ctx bindVertexArray #?(:clj 0 :cljs nil))
               magician)

           [{:insert-facts _}]
           (let [facts (:insert-facts chant)]
             (println "will insert" (count facts) "fact(s)")
             (update magician ::facts into facts))

           #_"no else handling. m/match will throw on faulty spell."))
       {::data {} ::facts [] :state {}}
       spell-chants)
      (dissoc :state)))
