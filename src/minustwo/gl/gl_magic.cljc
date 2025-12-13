(ns minustwo.gl.gl-magic
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.core.match :as m]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! vars->map]]
   [engine.world :as world]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.gl.vao :as vao]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_STATIC_DRAW GL_UNSIGNED_SHORT]]
   [minustwo.gl.gl :as gl]
   [minustwo.utils :as utils]
   [odoyle.rules :as o]))

(s/def ::spells sequential?)
(s/def ::casted? #{:pending :loading true})
(s/def ::err nil?)

(def rules
  (o/ruleset
   {::prepate-spell
    [:what [esse-id ::spells spells]
     :then (insert! esse-id ::casted? :pending)]

    ::to-cast
    [:what
     [esse-id ::spells spells]
     [esse-id ::casted? :pending]]}))

(def system
  {::world/rules rules})

(defn cast-spell [world spells-fact]
  (let [{:keys [esse-id spells]}  spells-fact
        {:keys [ctx all-shaders]} (utils/query-one world ::gl/data)
        all-attr-locs             (update-vals all-shaders :attr-locs)]
    (loop [[spell & remaining] spells summons [] state {}]
      (if spell
        (m/match [spell]
          [{:bind-vao _}] ;; entry: vao binding
          (let [vao (gl ctx #?(:clj genVertexArrays :cljs createVertexArray))]
            (gl ctx bindVertexArray vao)
            (swap! vao/db* assoc (:bind-vao spell) vao)
            (recur remaining summons state))

          [{:buffer-data _ :buffer-type _}] ;; entry: buffer binding
          (let [buffer (cljgl/create-buffer ctx)
                buffer-type (:buffer-type spell)
                buffer-data (:buffer-data spell)]
            (gl ctx bindBuffer buffer-type buffer)
            (gl ctx bufferData buffer-type buffer-data GL_STATIC_DRAW)
            (recur remaining summons (assoc state :current-buffer buffer :buffer-type buffer-type)))

          [{:bind-current-buffer _}]
          (let [current-buffer (:current-buffer state)
                buffer-type    (:buffer-type state)]
            (gl ctx bindBuffer buffer-type current-buffer)
            (recur remaining summons state))

          ;; entry: attrib pointing, some keywords follows gltf accessor keys 
          [{:point-attr _ :count _ :componentType _ :use-shader _}] 
          (if-let [attr-loc (get-in all-attr-locs [(:use-shader spell) (:point-attr spell)])]
            (let [{:keys [count componentType stride offset] :or {stride 0 offset 0}} spell]
              (condp = componentType
                GL_UNSIGNED_SHORT (gl ctx vertexAttribIPointer attr-loc count componentType stride offset)
                (gl ctx vertexAttribPointer attr-loc count componentType false stride offset))
              (gl ctx enableVertexAttribArray attr-loc)
              (recur remaining summons state))
            (recur remaining
                   (conj summons [:err-fact ::err (str (:point-attr spell) " is not found in any shader program")])
                   state))

          [{:bind-texture _ :image _ :tex-unit _}] ;; entry: texture binding
          (let [uri      (-> spell :image :uri)
                tex-unit (:tex-unit spell)
                tex-name (:bind-texture spell)]
            (println "[assimp-js] binding" tex-name "to" tex-unit)
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
          (let [facts (:insert-facts spell)]
            (println "will insert" (count facts) "fact(s)")
            (recur remaining (concat summons facts) state))

          #_"no else handling. m/match will throw on faulty spell.")
        (vars->map esse-id summons)))))

(defn summons->world* [world summons-map]
  (let [{:keys [esse-id summons]} summons-map]
    (-> world
        (o/retract esse-id ::spells)
        (o/insert esse-id ::casted? true)
        ((fn [s'] (reduce o/insert s' summons))))))