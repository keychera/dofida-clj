(ns engine.world
  (:require
   [clojure.spec.alpha :as s] 
   [odoyle.rules :as o]))

(defonce world* (atom nil))

(s/def ::init fn?)
(s/def ::rules vector?)

(defn wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  (when (#{::compile-shader} (:name rule))
                    (println :what (:name rule) new-fact old-fact))
                  ;; (println :what (:name rule) new-fact old-fact)
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  ;; (println :when (:name rule) match)
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{::compile-shader ::compiling-shader} (:name rule))
                    (println "firing" (:name rule)))
                  ;; (println :then (:name rule) match)
                  (f session match))
                :then-finally
                (fn [f session]
                  ;; (println :then-finally (:name rule))
                  (f session))}))

(defonce ^:devonly previous-rules (atom nil))

(defn init-world [world all-rules]
  (let [init-only? (nil? world)
        session (if init-only?
                  (o/->session)
                  ;; devonly : refresh rules without resetting facts
                  (->> @previous-rules
                       (map :name)
                       (reduce o/remove-rule world)))]
    (reset! previous-rules all-rules)
    (->> all-rules
         (map #'wrap-fn)
         (reduce o/add-rule session))))
