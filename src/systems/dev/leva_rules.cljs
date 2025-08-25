(ns systems.dev.leva-rules
  (:require
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.esse :as esse]
   [engine.world :as world]
   [odoyle.rules :as o]
   [systems.input :as input]
   [systems.time :as time]))

(s/def ::x number?)
(s/def ::y number?)

(s/def ::r (s/and number? #(<= 0 % 255)))
(s/def ::g (s/and number? #(<= 0 % 255)))
(s/def ::b (s/and number? #(<= 0 % 255)))

(def system
  {::world/rules
   (o/ruleset
    {::leva-color
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
     
     ::shader-update
     [:what
      [::time/now ::time/total total-time]
      [::input/mouse ::input/x mouse-x]
      [::input/mouse ::input/y mouse-y]
      [::leva-color ::r r]
      [::leva-color ::g g]
      [::leva-color ::b b]
      [esse-id ::esse/compiled-shader compiled-shader {:then false}]
      :then
      (o/insert! esse-id ::esse/compiled-shader
                 (->> compiled-shader
                      (sp/setval [:uniforms 'u_time] total-time)
                      (sp/setval [:uniforms 'u_mouse] [mouse-x mouse-x])
                      (sp/setval [:uniforms 'u_sky_color] [(/ r 255) (/ g 255) (/ b 255)])))]})})


