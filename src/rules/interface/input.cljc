(ns rules.interface.input
  (:require
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.firstperson :as firstperson]))

(s/def ::mode #{::blender ::firstperson})
(s/def ::x number?)
(s/def ::y number?)
(s/def ::dx number?)
(s/def ::dy number?)
(s/def ::keystate any?)
(s/def ::keydown any?)

(defn keys-event [session mode keyname _keystate]
  ;; match macro cannot be inside odoyle/ruleset apparently
  (match [mode keyname]
    [_ :r]
    (firstperson/player-reset session)

    [::firstperson _]
    (if-let [move (case keyname
                    :w     ::firstperson/forward
                    :a     ::firstperson/strafe-l
                    :s     ::firstperson/backward
                    :d     ::firstperson/strafe-r
                    :shift ::firstperson/ascend
                    :ctrl  ::firstperson/descend
                    nil)]
      (o/insert session ::firstperson/player ::firstperson/move-control move)
      session)

    :else session))

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
      [::mouse-delta ::dx mouse-dx]
      [::mouse-delta ::dy mouse-dy]
      :then
      (case mode
        ::firstperson
        (insert! ::firstperson/player #::firstperson{:view-dx mouse-dx :view-dy mouse-dy})

        #_else
        (insert! ::firstperson/player #::firstperson{:view-dx 0 :view-dy 0}))]

     ::keys
     [:what
      [::global ::mode mode]
      [keyname ::keystate keystate]
      :then
      (s-> session
           (keys-event mode keyname keystate))
      :then-finally
      (when-not (seq (o/query-all session ::keys))
        (when (seq (o/query-all session ::firstperson/movement))
          (s-> session (o/retract ::firstperson/player ::firstperson/move-control))))]})})

(defn update-mouse-pos [world x y]
  (o/insert world ::mouse {::x x ::y y}))

(defn update-mouse-delta [world dx dy]
  (o/insert world ::mouse-delta {::dx dx ::dy dy}))

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
