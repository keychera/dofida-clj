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
