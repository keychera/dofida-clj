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

    ::shader-esse
    [:what
     [any-esse ::esse/compiled-shader compiled-shader]]

    ::shader-update
    [:what
     [::time ::total total-time]
     [any-esse ::esse/compiled-shader compiled-shader {:then false}]
     :then
     (o/insert! any-esse ::esse/compiled-shader
                (->> compiled-shader (sp/setval [:uniforms 'u_time] total-time)))]}))

(def initial-session
  (reduce o/add-rule (o/->session) rules))

;; specs

(s/def ::total number?)
(s/def ::delta number?)

(s/def ::width number?)
(s/def ::height number?)