(ns minusone.rules.gl.magic
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.gl.vao :as vao]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]))

(s/def ::incantation vector?)
(s/def ::all-shader vector?)

(s/def ::err nil?)

(defn gl-incantation
  "chant some gl magic. incantations are order-sensitive.
   some incantations will produce fact as `summons`.
   this fn will return the summoned facts"
  [game all-shader incantation]
  (let [all-attr-locs (into {} (map (juxt :esse-id (comp :attr-locs :program-data))) all-shader)]
    (loop [[entry & remaining] incantation
           summons []]
      (if entry
        (match [entry]
          [{:bind-vao _}] ;; entry: vao binding
          (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
            (gl game bindVertexArray vao)
            (recur remaining (conj summons [(:bind-vao entry) ::vao/vao vao])))

          [{:bind-buffer _ :buffer-type _}] ;; entry: buffer binding
          (let [buffer      (gl-utils/create-buffer game)
                buffer-type (:buffer-type entry)]
            (gl game bindBuffer buffer-type buffer)
            (when-let [buffer-data (:buffer-data entry)]
              (gl game bufferData buffer-type buffer-data (gl game STATIC_DRAW)))
            (recur remaining summons))

          [{:point-attr _ :attr-size _ :attr-type _ :from-shader _}] ;; entry: attrib pointing
          (if-let [attr-loc (get-in all-attr-locs [(:from-shader entry) (:point-attr entry)])]
            (let [{:keys [attr-size attr-type stride offset] :or {stride 0 offset 0}} entry]
              (gl game enableVertexAttribArray attr-loc)
              (gl game vertexAttribPointer attr-loc attr-size attr-type false stride offset)
              (recur remaining summons))
            (recur remaining (conj summons [:err-fact ::err (str (:point-attr entry) " is not found in any shader program")])))

          [{:bind-texture _ :image _ :tex-unit _}] ;; entry: texture binding
          (let [uri      (-> entry :image :uri)
                tex-unit (:tex-unit entry)
                tex-id   (:bind-texture entry)]
            (println "[assimp-js] binding" tex-id "to" tex-unit)
            (cond
              (str/starts-with? uri "data:")
              (recur remaining
                     (conj summons
                           [tex-id ::texture/data-uri-to-load uri]
                           [tex-id ::texture/tex-unit tex-unit]
                           [tex-id ::texture/texture-loaded? :pending]))

              :else
              (do (println "not handled yet")
                  (recur remaining summons))))

          [{:unbind-vao _}]
          (do (gl game bindVertexArray #?(:clj 0 :cljs nil))
              (recur remaining summons))

          #_"no else handling. match will throw on faulty incantation.")
        summons))))

(def rules
  (o/ruleset
   {::all-shader
    [:what
     [esse-id ::shader/program-data program-data]
     :then
     (let [all-shader (o/query-all session ::all-shader)]
       (s-> session
            (o/insert ::shader/global ::all-shader all-shader)))]

    ::do-some-magic!
    [:what
     [::shader/global ::shader/context context]
     [::shader/global ::all-shader all-shader]
     [esse-id ::incantation incantation]
     :then
     (let [summons (gl-incantation context all-shader incantation)]
       (s-> session
            (o/retract esse-id ::incantation)
            ((fn [s'] (reduce o/insert s' summons)))))]}))

(def system
  {::world/rules rules})