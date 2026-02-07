(ns minusthree.engine.world
  (:require
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [odoyle.rules :as o]))

(s/def ::this ::o/session)

(s/def ::init-fn fn? #_(fn [world game] world))
(s/def ::rules   ::o/rules)

;; dev-only
(defn resolve-var [v]
  (if (instance? #?(:clj clojure.lang.Var :cljs Var) v) (deref v) v))

(defn prepare-world [world game all-rules init-fns]
  (let [new-world  (reduce o/add-rule (or world (o/->session)) all-rules)
        init-world (reduce (fn [w' init-fn] (init-fn w' game)) new-world init-fns)]
    init-world))

(defn init-world [game system-coll]
  (let [systems   (into [] (map resolve-var) system-coll)
        all-rules (into [] (comp (distinct) (mapcat resolve-var))
                        (sp/select [sp/ALL ::rules] systems))
        init-fns  (sp/select [sp/ALL ::init-fn some?] systems)]
    (update game ::this prepare-world game all-rules init-fns)))

;; esse, short for 'essence', has similar connotation to entity in an entity-component-system
;; however, this game is built on top of a rules engine, it doesn't actually mean anything inherently
;; here, an esse is often referring to something that has the same id in the rules engine
;; (also, I've never used an ecs before so I am not sure if this is actually similar)

(defn esse
  "insert an esse given the facts in the shape of maps of attr->value.
   this fn is merely sugar, spice, and everything nice"
  [world esse-id & facts]
  (o/insert world esse-id (apply merge facts)))
