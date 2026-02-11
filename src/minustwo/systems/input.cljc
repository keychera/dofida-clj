(ns minustwo.systems.input
  (:require
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [minustwo.systems.view.camera :as camera]
   [minustwo.systems.view.firstperson :as firstperson]
   [odoyle.rules :as o]))

(s/def ::mode #{::default ::firstperson})
(s/def ::x number?)
(s/def ::y number?)
(s/def ::dx number?)
(s/def ::dy number?)
(s/def ::keystate #{::keydown ::keyup})

;; input need hammocks
(defn keys-event [session mode keyname keystate]
  ;; match macro cannot be inside odoyle/ruleset apparently
  (match [mode keyname]
    [_ :r]
    (if (= keystate ::keyup)
      (-> session
          firstperson/reset-fps-cam)
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

(defn init-fn [world _game]
  (o/insert world ::global ::mode ::default))

(def rules
  (o/ruleset
   {::mode
    [:what [::global ::mode mode]]
       ;; kinda a gut feeling
       ;; having this query rules felt like bad rules engine design

    ::mouse
    [:what
     [::global ::mode mode]
     [::mouse ::x mouse-x {:then not=}]
     [::mouse ::y mouse-y {:then not=}]]

    ::mouse-delta
    [:what
     [::global ::mode mode]
     [::mouse-delta ::dx mouse-dx {:then not=}]
     [::mouse-delta ::dy mouse-dy {:then not=}]
     :then
     (case mode
       ::firstperson
       (insert! ::firstperson/player #::firstperson{:view-dx mouse-dx :view-dy mouse-dy})

       #_else
       (insert! ::firstperson/player #::firstperson{:view-dx 0.0 :view-dy 0.0}))]

    ::active-camera
    [:what
     [::global ::mode mode]
     [::world/global ::camera/active active-cam {:then false}]
     :then
     (println mode (= mode ::firstperson) "change!" active-cam)
     (s-> (condp = mode
            ::firstperson (camera/activate-cam session ::firstperson/player)
            #_else (camera/activate-cam session ::world/global)))]

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
         (s-> session (o/retract ::firstperson/player ::firstperson/move-control))))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

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
