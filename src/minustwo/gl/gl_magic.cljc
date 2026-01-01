(ns minustwo.gl.gl-magic
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.core.match :as m]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_STATIC_DRAW GL_UNSIGNED_INT
                                  GL_UNSIGNED_SHORT]]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.model.assimp :as assimp]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]))

(s/def ::spell sequential?)
(s/def ::casted? #{:pending :loading true})
(s/def ::err nil?)

(def rules
  (o/ruleset
   {::to-cast
    [:what
     [esse-id ::spell spell]
     [esse-id ::casted? :pending]
     :then
     (println "casting gl-magic for" esse-id)]

    ::I-cast-gltf-loading!
    [:what
     [esse-id ::gltf/data gltf-data]
     [esse-id ::gltf/bins bins]
     [esse-id ::shader/use use-shader]
     [esse-id ::assimp/config assimp-config]
     [esse-id ::casted? :pending {:then false}]
     :then
     (println "[magic] gltf spell for" esse-id)
     (let [gltf-spell
           (gltf/gltf-spell gltf-data (first bins)
                            (merge {:model-id esse-id
                                    :use-shader use-shader}
                                   assimp-config))]
       (insert! esse-id ::spell gltf-spell))]}))

(def system
  {::world/rules rules})

(defn cast-spell [world spell-fact]
  (let [{:keys [esse-id spell]}  spell-fact
        {:keys [ctx all-shaders vao-db*]} (utils/query-one world ::room/gl-data)
        all-attr-locs             (update-vals all-shaders :attr-locs)]
    (loop [[chant & remaining] spell summons [] state {}]
      (if chant
        (m/match [chant]
          [{:bind-vao _}] ;; entry: vao binding
          (let [vao (gl ctx #?(:clj genVertexArrays :cljs createVertexArray))]
            (gl ctx bindVertexArray vao)
            (swap! vao-db* assoc (:bind-vao chant) vao)
            (recur remaining summons state))

          [{:buffer-data _ :buffer-type _}] ;; entry: buffer binding
          (let [buffer (cljgl/create-buffer ctx)
                buffer-type (:buffer-type chant)
                buffer-data (:buffer-data chant)]
            (gl ctx bindBuffer buffer-type buffer)
            (gl ctx bufferData buffer-type buffer-data GL_STATIC_DRAW)
            (recur remaining
                   (if (:buffer-name chant)
                     (conj summons [(:buffer-name chant) ::shader/buffer buffer])
                     summons)
                   (assoc state :current-buffer buffer :buffer-type buffer-type)))

          [{:bind-current-buffer _}]
          (let [current-buffer (:current-buffer state)
                buffer-type    (:buffer-type state)]
            (gl ctx bindBuffer buffer-type current-buffer)
            (recur remaining summons state))

          ;; entry: attrib pointing, some keywords follows gltf accessor keys 
          [{:point-attr _ :count _ :component-type _ :use-shader _}]
          (if-let [attr-loc (->> (get-in all-attr-locs [(:use-shader chant) (:point-attr chant) :attr-loc])
                                 (s/conform ::shader/attr-loc))]
            (let [{:keys [count component-type stride offset] :or {stride 0 offset 0}} chant]
              (condp = component-type
                GL_UNSIGNED_SHORT (gl ctx vertexAttribIPointer attr-loc count component-type stride offset)
                GL_UNSIGNED_INT   (gl ctx vertexAttribIPointer attr-loc count component-type stride offset)
                (gl ctx vertexAttribPointer attr-loc count component-type false stride offset))
              (gl ctx enableVertexAttribArray attr-loc)
              (recur remaining summons state))
            (recur remaining
                   (conj summons [:err-fact ::err (str "[ for " esse-id " ] " (:point-attr chant) " is not found in any shader program")])
                   state))

          [{:bind-texture _ :image _ :tex-unit _}] ;; entry: texture binding
          (let [uri      (-> chant :image :uri)
                tex-unit (:tex-unit chant)
                tex-name (:bind-texture chant)]
            (recur remaining
                   (conj summons
                         [tex-name ::texture/uri-to-load uri]
                         [tex-name ::texture/tex-unit tex-unit]
                         [tex-name ::texture/loaded? :pending])
                   state))

          [{:unbind-vao _}]
          (do (gl ctx bindVertexArray #?(:clj 0 :cljs nil))
              (recur remaining summons state))

          [{:insert-facts _}]
          (let [facts (:insert-facts chant)]
            (println "will insert" (count facts) "fact(s)")
            (recur remaining (into summons facts) state))

          #_"no else handling. m/match will throw on faulty spell.")
        (vars->map esse-id summons)))))

(defn summons->world* [world summons-map]
  (let [{:keys [esse-id summons]} summons-map]
    (-> world
        (o/retract esse-id ::spell)
        (o/insert esse-id ::casted? true)
        ((fn [s'] (reduce o/insert s' summons))))))
