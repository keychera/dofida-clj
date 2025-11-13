(ns rules.interface.input
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.firstperson :as firstperson]))

(s/def ::mode #{::blende ::firstperson})
(s/def ::x number?)
(s/def ::y number?)
(s/def ::keystate any?)
(s/def ::keydown any?)

(def system
  {::world/rules
   (o/ruleset
    {::mouse
     [:what
      [::mouse ::x mouse-x]
      [::mouse ::y mouse-y]]

     ::mouse-delta
     [:what
      [::global ::mode mode]
      [::mouse-delta ::x mouse-dx]
      [::mouse-delta ::y mouse-dy]
      :then
      (case mode
        ::firstperson
        (insert! ::firstperson/player #::firstperson{:view-dx mouse-dx :view-dy mouse-dy})

        :noop)]

     ::keys
     [:what
      [::global ::mode mode]
      [keyname ::keystate keystate]
      :then
      (case mode
        ::firstperson
        (when-let [move (case keyname
                          :w     ::firstperson/forward
                          :a     ::firstperson/strafe-l
                          :s     ::firstperson/backward
                          :d     ::firstperson/strafe-r
                          :shift ::firstperson/ascend
                          :ctrl  ::firstperson/descend
                          nil)]
          (insert! ::firstperson/player ::firstperson/move-control move))

        :noop)
      :then-finally
      (when-not (seq (o/query-all session ::keys))
        (s-> session (o/retract ::firstperson/player ::firstperson/move-control)))]})})

(defn update-mouse-pos [world x y]
  (o/insert world ::mouse {::x x ::y y}))

(defn update-mouse-delta [world dx dy]
  (o/insert world ::mouse-delta {::x dx ::y dy}))

(defn key-on-keydown [world keyname]
  (o/insert world keyname ::keystate ::keydown))

(defn key-on-keyup [world keyname]
  (o/retract world keyname ::keystate))

(defn cleanup-input [world]
  (reduce
   (fn [w' k] (o/retract w' (:keyname k) ::keystate))
   world (o/query-all world ::keys)))

(defn set-mode [world mode]
  (o/insert world ::global ::mode mode))
