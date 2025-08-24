(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [dofida.dofida :as dofida]
   [engine.esse :as esse]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]))

(defonce world* (atom {}))

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

(def rules
  (o/ruleset
   {::window
    [:what
     [::window ::width width]
     [::window ::height height]]

    ::mouse
    [:what
     [::mouse ::x x]
     [::mouse ::y y]]
    ::leva-color
    [:what
     [::leva-color ::r r]
     [::leva-color ::g g]
     [::leva-color ::b b]]

    ::leva-point
    [:what
     [::leva-point ::x x]
     [::leva-point ::y y]
     [esse-id ::esse/x esse-x {:then false}]
     [esse-id ::esse/y esse-y {:then false}]
     :then
     (o/insert! esse-id {::esse/x (+ esse-x (* x 25)) ::esse/y (+ esse-y (* y 25))})]

    ::sprite-esse
    [:what
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::esse/current-sprite current-sprite]]

    ::shader-esse
    [:what
     [esse-id ::esse/compiled-shader compiled-shader]]

    ::shader-update
    [:what
     [::time ::total total-time]
     [::mouse ::x mouse-x]
     [::mouse ::y mouse-y]
     [::leva-color ::r r]
     [::leva-color ::g g]
     [::leva-color ::b b]
     [esse-id ::esse/compiled-shader compiled-shader {:then false}]
     :then
     (o/insert! esse-id ::esse/compiled-shader
                (->> compiled-shader
                     (sp/setval [:uniforms 'u_time] total-time)
                     (sp/setval [:uniforms 'u_mouse] [mouse-x mouse-x])
                     (sp/setval [:uniforms 'u_sky_color] [(/ r 255) (/ g 255) (/ b 255)])))]

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

(def dofida-session
  (-> initial-session
      (o/insert :dofida ::esse/shader-compile-fn
                (fn [game] (c/compile game (dofida/->dofida game))))
      (o/insert ::dofida2 (esse/->sprite 100 100 "dofida2.png"))
      (o/insert ::dofida3 (esse/->sprite 222 200 "dofida2.png"))
      (o/insert ::dofida4 (esse/->sprite 450 300 "dofida2.png"))))

;; specs
(s/def ::total number?)
(s/def ::delta number?)

(s/def ::width number?)
(s/def ::height number?)

(s/def ::x number?)
(s/def ::y number?)

(s/def ::r (s/and number? #(<= 0 % 255)))
(s/def ::g (s/and number? #(<= 0 % 255)))
(s/def ::b (s/and number? #(<= 0 % 255)))


(comment
  (def testrule
    (o/ruleset
     {::happening
      [:what
       [esse-id ::esse/shader-compile-fn compile-fn]
       [esse-id ::esse/compiling-shader true]
       :then
       (o/retract! esse-id ::esse/shader-compile-fn)]}))

  (def testsession
    (->> testrule
         (map #'wrap-fn)
         (reduce o/add-rule (o/->session))))

  (-> testsession
      (o/insert :dofida ::esse/shader-compile-fn (fn [] (println "hello")))
      (o/fire-rules)
      (o/query-all ::happening)))