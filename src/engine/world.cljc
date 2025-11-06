(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(s/def ::atom* (partial instance? #?(:clj clojure.lang.Atom :cljs Atom)))

;; game system
(s/def ::game map?)
(s/def ::init-fn   fn? #_(fn [world game] world))
(s/def ::before-load-fn fn? #_(fn [world game] world)) ;; called before all rules are removed on code reload
(s/def ::after-load-fn fn? #_(fn [world game] world))  ;; called after  all rules are removed on code reload
(s/def ::render-fn fn? #_(fn [world game] world))

(defn ->init []
  {::atom*       (atom nil)
   ::prev-rules* (atom nil)
   ::init-cnt*   (atom 0)})

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  (when (#{} (:name rule))
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
  (= 1 @(::init-cnt* game)))

(defn init-world [world game all-rules before-load-fns after-load-fns]
  (let [prev-rules*  (::prev-rules* game)
        before-world (if (first-init? game)
                       (o/->session)
                       (let [world
                             (reduce (fn [world before-fn] (before-fn world game)) world before-load-fns)]
                         (->> @prev-rules* ;; dev-only : refresh rules without resetting facts
                              (map :name)
                              (reduce o/remove-rule world))))
        _            (reset! prev-rules* all-rules)
        init-world   (->> all-rules
                          (map #'rules-debugger-wrap-fn)
                          (reduce o/add-rule before-world))
        after-world  (reduce (fn [world after-fn] (after-fn world game)) init-world after-load-fns)]
    (-> after-world
        (o/insert ::global ::game game))))
