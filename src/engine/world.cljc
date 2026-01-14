(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [odoyle.rules :as o]))

(s/def ::atom* (partial instance? #?(:clj clojure.lang.Atom :cljs Atom)))

;; game system
(s/def ::init-fn   fn? #_(fn [world game] world))
(s/def ::before-load-fn fn? #_(fn [world game] world)) ;; called before all rules are removed on code reload
(s/def ::after-load-fn fn? #_(fn [world game] world))  ;; called after  all rules are removed on code reload
(s/def ::render-fn fn? #_(fn [world game] world))

(s/def ::global any?)

(defn ->init []
  {::atom*       (atom nil)
   ::prev-rules* (atom nil)
   ::init-cnt*   (atom 0)})

;; need better logging mechanism for this
(def fact-id-with-frequent-updates
  #{:minustwo.systems.time/now
    :minustwo.systems.window/window
    :minustwo.systems.input/mouse
    :minustwo.systems.input/mouse-delta
    :minustwo.systems.view.firstperson/player})

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  (when false #_(not (fact-id-with-frequent-updates (:id new-fact)))
                        (println (:name rule) "is comparing" (dissoc old-fact :value) "=>" (dissoc new-fact :value)))
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "when" (:name rule)))
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "firing" (:name rule) "for" (keys match)))
                  (f session match))
                :then-finally
                (fn [f session]
                  ;; (println :then-finally (:name rule))
                  (f session))}))

;; dev-only
(defn first-init? [game]
  (= 0 @(::init-cnt* game)))

;; dev-only
(defn resolve-var [v]
  (if (instance? #?(:clj clojure.lang.Var :cljs Var) v) (deref v) v))

(defn build-systems [system-coll]
  (let [systems    (into [] (map resolve-var) system-coll)
        all-rules  (into []
                         (comp (distinct)
                               (mapcat resolve-var))
                         (sp/select [sp/ALL ::rules] systems))
        init-fns   (sp/select [sp/ALL ::init-fn some?] systems)
        before-fns (sp/select [sp/ALL ::before-load-fn some?] systems)
        after-fns  (sp/select [sp/ALL ::after-load-fn some?] systems)
        render-fns (sp/select [sp/ALL ::render-fn some?] systems)]
    (vars->map all-rules before-fns init-fns after-fns render-fns)))

(defn init-world [world game all-rules before-fns init-fns after-fns]
  (let [prev-rules*  (::prev-rules* game)
        first-init?  (first-init? game)
        before-world (if first-init?
                       (o/->session)
                       (let [world (reduce (fn [world before-fn] (before-fn world game)) world before-fns)]
                         (->> @prev-rules* ;; dev-only : refresh rules without resetting facts
                              (map :name)
                              (reduce o/remove-rule world))))
        _            (reset! prev-rules* all-rules)
        all-facts    (o/query-all before-world)
        init-world   (->> all-rules
                          (map #'rules-debugger-wrap-fn)
                          (reduce o/add-rule before-world)
                          ((fn [world]
                             (if first-init?
                               (reduce (fn [w' init-fn] (init-fn w' game)) world init-fns)
                               (reduce (fn [w' fact] (o/insert w' fact)) world all-facts)))))
        _            (swap! (::init-cnt* game) inc)
        after-world  (reduce (fn [world after-fn] (after-fn world game)) init-world after-fns)]
    after-world))

;; esse, short for 'essence', has similar connotation to entity in an entity-component-system
;; however, this game is built on top of a rules engine, it doesn't actually mean anything inherently
;; here, an esse is often referring to something that has the same id in the rules engine
;; (also, I've never used an ecs before so I am not sure if this is actually similar)

(defn esse 
  "insert an esse given the facts in the shape of maps of attr->value.
   this fn is merely sugar, spice, and everything nice"
  [world esse-id & facts]
  (o/insert world esse-id (apply utils/deep-merge facts)))
