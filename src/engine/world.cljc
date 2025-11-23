(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(s/def ::atom* (partial instance? #?(:clj clojure.lang.Atom :cljs Atom)))

;; game system
(s/def ::init-fn   fn? #_(fn [world game] world))
(s/def ::before-load-fn fn? #_(fn [world game] world)) ;; called before all rules are removed on code reload
(s/def ::after-load-fn fn? #_(fn [world game] world))  ;; called after  all rules are removed on code reload
(s/def ::render-fn fn? #_(fn [world game] world))

(defn ->init []
  {::atom*       (atom nil)
   ::prev-rules* (atom nil)
   ::init-cnt*   (atom 0)})

(def fact-id-with-frequent-updates
  #{:rules.time/now
    :rules.firstperson/player
    :rules.window/window
    :rules.interface.input/mouse
    :rules.interface.input/mouse-delta})

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

(defn init-world [world game all-rules before-load-fns init-fns after-load-fns]
  (let [prev-rules*  (::prev-rules* game)
        first-init?  (first-init? game)
        before-world (if first-init?
                       (o/->session)
                       (let [world
                             (reduce (fn [world before-fn] (before-fn world game)) world before-load-fns)]
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
        after-world  (reduce (fn [w' after-fn] (after-fn w' game)) init-world after-load-fns)]
    after-world))
