(ns engine.session
  (:require
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.esse :as esse]
   [odoyle.rules :as o]))

(defonce *session (atom {}))

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
     :then
     (o/insert! :engine.engine/dofida2
                {::esse/x (* x 6553) ::esse/y (* y 6553)})]

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
                     (sp/setval [:uniforms 'u_sky_color] [(/ r 255) (/ g 255) (/ b 255)])))]}))

(def initial-session
  (->> rules
       (map (fn [rule]
              (o/wrap-rule rule
                           {:what
                            (fn [f session new-fact old-fact]
                              (when (#{::leva-color ::leva-point} (:name rule))
                                (println :what (:name rule) new-fact old-fact))
                              ;; (println :what (:name rule) new-fact old-fact)
                              (f session new-fact old-fact))
                            :when
                            (fn [f session match]
                              ;; (println :when (:name rule) match)
                              (f session match))
                            :then
                            (fn [f session match]
                              ;; (println :then (:name rule) match)
                              (f session match))
                            :then-finally
                            (fn [f session]
                              ;; (println :then-finally (:name rule))
                              (f session))})))
       (reduce o/add-rule (o/->session))))

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
