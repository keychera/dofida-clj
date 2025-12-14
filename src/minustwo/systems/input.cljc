(ns minustwo.systems.input
  (:require
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.camera.arcball :as arcball]
   [minustwo.systems.view.firstperson :as firstperson]))

(s/def ::mode #{::arcball ::firstperson})
(s/def ::x number?)
(s/def ::y number?)
(s/def ::dx number?)
(s/def ::dy number?)
(s/def ::keystate #{::keydown ::keyup})

(defn keys-event [session mode keyname keystate]
  ;; match macro cannot be inside odoyle/ruleset apparently
  (match [mode keyname]
    [_ :r]
    (if (= keystate ::keyup)
      (-> session
          ;; firstperson/player-reset
          arcball/reset-rot)
      session)

    [::arcball ::mouse-left]
    (case keystate
      ::keydown (arcball/start-rotating session)
      ::keyup   (arcball/stop-rotating session)
      session)

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
  {::world/init-fn
   (fn [world _game]
     (o/insert world ::global ::mode ::arcball))

   ::world/rules
   (o/ruleset
    {::mode
     [:what [::global ::mode mode]]
     ;; kinda a gut feeling
     ;; having this query rules felt like bad rules engine design

     ::mouse
     [:what
      [::global ::mode mode]
      [::mouse ::x mouse-x]
      [::mouse ::y mouse-y]
      :then
      ;; complecting with query rules kinda not it, ignore for now
      (case mode
        ::arcball
        (s-> session (arcball/send-xy mouse-x mouse-y))

        #_else :noop)]

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
        (insert! ::firstperson/player #::firstperson{:view-dx 0.0 :view-dy 0.0}))]

     ::keys
     [:what
      [::global ::mode mode]
      [keyname ::keystate keystate]
      :then
      (s-> session
           (keys-event mode keyname keystate))
      :then-finally
      (when-not (seq (eduction
                      (filter
                       (fn [{:keys [keyname keystate]}]
                         (and (#{:w :a :s :d} keyname) (= keystate ::keydown))))
                      (o/query-all session ::keys)))
        (when (seq (o/query-all session ::firstperson/movement))
          (s-> session (o/retract ::firstperson/player ::firstperson/move-control))))]})})

(defn update-mouse-pos [world x y]
  (o/insert world ::mouse {::x x ::y y}))

(defn update-mouse-delta [world dx dy]
  (o/insert world ::mouse-delta {::dx dx ::dy dy}))

(defn key-on-keydown [world keyname]
  (o/insert world keyname ::keystate ::keydown))

(defn key-on-keyup [world keyname]
  (o/insert world keyname ::keystate ::keyup))

(defn cleanup-input [world]
  (reduce (fn [w' k] (o/retract w' (:keyname k) ::keystate))
          world (o/query-all world ::keys)))

(defn set-mode [world mode]
  (o/insert world ::global ::mode mode))
