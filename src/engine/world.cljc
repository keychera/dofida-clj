(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [dofida.dofida :as dofida]
   [engine.esse :as esse]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]))

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

(s/def ::x number?)
(s/def ::y number?)

(def rules
  (o/ruleset
   {::sprite-esse
    [:what
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::esse/current-sprite current-sprite]]

    ::shader-esse
    [:what
     [esse-id ::esse/compiled-shader compiled-shader]]

    ::compile-shader
    [:what
     [esse-id ::esse/shader-compile-fn compile-fn]]

    ::compiling-shader
    [:what
     [esse-id ::esse/shader-compile-fn compile-fn]
     [esse-id ::esse/compiling-shader true]
     :then
     (o/retract! esse-id ::esse/shader-compile-fn)]

    ::load-image
    [:what
     [esse-id ::esse/image-to-load image-path]]

    ::loading-image
    [:what
     [esse-id ::esse/image-to-load image-path]
     [esse-id ::esse/loading-image true]
     :then
     (o/retract! esse-id ::esse/image-to-load)]}))


(def initial-session (->> rules (map #'wrap-fn) (reduce o/add-rule (o/->session))))

(defn init-dofida [session]
  (-> session
      (o/insert :dofida ::esse/shader-compile-fn
                (fn [game] (c/compile game (dofida/->dofida game))))
      (o/insert ::dofida2 (esse/->sprite 100 100 "dofida2.png"))
      (o/insert ::dofida3 (esse/->sprite 222 200 "dofida2.png"))
      (o/insert ::dofida4 (esse/->sprite 450 300 "dofida2.png"))))

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
