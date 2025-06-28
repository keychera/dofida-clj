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
     [esse-id ::esse/compiled-shader compiled-shader {:then false}]
     :then
     (o/insert! esse-id ::esse/compiled-shader
                (->> compiled-shader
                     (sp/setval [:uniforms 'u_time] total-time)
                     (sp/setval [:uniforms 'u_mouse] [mouse-x mouse-x])))]}))

(def initial-session
  (reduce o/add-rule (o/->session) rules))

;; specs

(s/def ::total number?)
(s/def ::delta number?)

(s/def ::width number?)
(s/def ::height number?)

(s/def ::x number?)
(s/def ::y number?)