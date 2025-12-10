(ns minusone.rules.gl.magic
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]]) 
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minusone.rules.gl.gl :as gl :refer [GL_STATIC_DRAW GL_UNSIGNED_SHORT]] 
   [minusone.rules.gl.gltf :as gltf :refer [gltf-magic]]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.model.assimp :as assimp]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]))


(s/def ::incantation sequential?)
(s/def ::all-shader vector?)

(s/def ::err nil?)

(defn gl-incantation
  "chant some gl magic. incantations are order-sensitive.
   some incantations will produce fact as `summons`.
   this fn will return the summoned facts"
  [game all-shader incantations]
  (let [all-attr-locs (into {} (map (juxt :esse-id (comp :attr-locs :program-data))) all-shader)]
    (loop [[entry & remaining] incantations summons [] state {}]
      (if entry
        (match [entry]
          [{:bind-vao _}] ;; entry: vao binding
          (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
            (gl game bindVertexArray vao)
            (swap! vao/db* assoc (:bind-vao entry) vao)
            (recur remaining summons state))
          
          [{:buffer-data _ :buffer-type _}] ;; entry: buffer binding
          (let [buffer (gl-utils/create-buffer game)
                buffer-type (:buffer-type entry)
                buffer-data (:buffer-data entry)]
            (gl game bindBuffer buffer-type buffer)
            (gl game bufferData buffer-type buffer-data GL_STATIC_DRAW)
            (recur remaining summons (assoc state :current-buffer buffer :buffer-type buffer-type)))
          
          [{:bind-current-buffer _}]
          (let [current-buffer (:current-buffer state)
                buffer-type    (:buffer-type state)]
            (gl game bindBuffer buffer-type current-buffer)
            (recur remaining summons state))

          [{:point-attr _ :attr-size _ :attr-type _ :use-shader _}] ;; entry: attrib pointing 
          (if-let [attr-loc (get-in all-attr-locs [(:use-shader entry) (:point-attr entry)])]
            (let [{:keys [attr-size attr-type stride offset] :or {stride 0 offset 0}} entry]
              (condp = attr-type 
                GL_UNSIGNED_SHORT (gl game vertexAttribIPointer attr-loc attr-size attr-type stride offset)
                (gl game vertexAttribPointer attr-loc attr-size attr-type false stride offset))
              (gl game enableVertexAttribArray attr-loc)
              (recur remaining summons state))
            (recur remaining 
                   (conj summons [:err-fact ::err (str (:point-attr entry) " is not found in any shader program")])
                   state))

          [{:bind-texture _ :image _ :tex-unit _}] ;; entry: texture binding
          (let [uri      (-> entry :image :uri)
                tex-unit (:tex-unit entry)
                tex-name (:bind-texture entry)]
            (println "[assimp-js] binding" tex-name "to" tex-unit)
            (recur remaining
                   (conj summons
                         [tex-name ::texture/uri-to-load uri]
                         [tex-name ::texture/tex-unit tex-unit]
                         [tex-name ::texture/loaded? :pending])
                   state))

          [{:unbind-vao _}]
          (do (gl game bindVertexArray #?(:clj 0 :cljs nil))
              (recur remaining summons state))

          [{:insert-facts _}]
          (let [facts (:insert-facts entry)]
            (println "will insert" (count facts) "fact(s)")
            (recur remaining (concat summons facts) state))

          #_"no else handling. match will throw on faulty incantation.")
        summons))))

(def rules
  (o/ruleset
   {::all-shader
    [:what
     [esse-id ::shader/program-data program-data]
     :then
     (println "collecting shader:" esse-id)
     (let [all-shader (o/query-all session ::all-shader)]
       (s-> session
            (o/insert ::shader/global ::all-shader all-shader)))]
    
    ::I-cast-gltf-loading!
    [:what
     [esse-id ::gltf/data gltf-data]
     [esse-id ::shader/use shader]
     [esse-id ::gltf/bins bins]
     [esse-id ::assimp/tex-unit-offset tex-unit-offset]
     [esse-id ::gl/loaded? :pending]
     :then
     (println "[magic] gltf spell for" esse-id)
     (let [gltf-spell (gltf-magic gltf-data (first bins)
                                  {:model-id esse-id
                                   :use-shader shader
                                   :tex-unit-offset tex-unit-offset})]
       (s-> session 
            
            (o/insert esse-id {::gl/loaded? :loading
                               ::incantation gltf-spell})))]
    
     ::do-some-magic!
    [:what
     [::shader/global ::shader/context context]
     [::shader/global ::all-shader all-shader]
     [esse-id ::incantation incantation]
     :then
     (let [summons (gl-incantation context all-shader incantation)]
       (s-> session
            (o/retract esse-id ::incantation)
            (o/insert esse-id ::gl/loaded? true)
            ((fn [s'] (reduce o/insert s' summons)))))]}))

(def system
  {::world/rules rules})