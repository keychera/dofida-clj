(ns rules.firstperson
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.math :as m]
   [rules.time :as time]
   [rules.window :as window]))

(s/def ::mvp any?)

(s/def ::position  vector?)
(s/def ::direction vector?)
(s/def ::right     vector?)

(s/def ::horiz-angle float?)
(s/def ::verti-angle float?)

(s/def ::move-control keyword?)
(s/def ::view-mode #{::direct ::moving})
(s/def ::frame-counter int?)
(s/def ::view-dx float?)
(s/def ::view-dy float?)

(defn calc-mvp [dimension position horiz-angle verti-angle]
  (let [initial-fov  (m/deg->rad 45)
        direction    [(* (Math/cos verti-angle) (Math/sin horiz-angle))
                      (Math/sin verti-angle)
                      (* (Math/cos verti-angle) (Math/cos horiz-angle))]

        right        [(Math/sin (- horiz-angle (/ Math/PI 2)))
                      0
                      (Math/cos (- horiz-angle (/ Math/PI 2)))]

        up           (#'m/cross right direction)

        aspect-ratio (/ (:width dimension) (:height dimension))
        projection   (m/perspective-matrix-3d initial-fov aspect-ratio 0.1 100)

        camera       (m/look-at-matrix-3d position (mapv + position direction) up)
        view         (m/inverse-matrix-3d camera)
        p*v          (m/multiply-matrices-3d view projection)
        mvp          (#?(:clj float-array :cljs #(js/Float32Array. %)) p*v)]
    {::mvp mvp
     ::horiz-angle horiz-angle
     ::verti-angle verti-angle
     ::direction direction
     ::right right}))

(defn player-reset [world]
  (let [dimension (:dimension (first (o/query-all world ::window/window)))]
    (-> world
        (o/insert ::player
                  (merge (calc-mvp dimension [0 0 5] Math/PI 0.0)
                         {::position [0 0 5]
                          ::view-mode ::direct
                          ::frame-counter 1})))))

(def system
  {::world/init-fn
   (fn [world _game]
     (-> world
         (o/insert ::player
                   {::mvp (#?(:clj float-array :cljs #(js/Float32Array. %)) (m/identity-matrix 4))
                    ::position [0 0 5]
                    ::horiz-angle Math/PI
                    ::verti-angle 0.0
                    ::view-dx 0
                    ::view-dy 0
                    ::view-mode ::moving})))

   ::world/rules
   (o/ruleset
    {::state
     [:what
      [::player ::mvp mvp]]

     ::movement
     [:what
      [::time/now ::time/delta delta-time]
      [::player ::view-mode ::moving]
      [::player ::position position {:then false}]
      [::player ::direction direction {:then false}]
      [::player ::right right {:then false}]
      [::player ::move-control control {:then false}]
      :then
      (let [speed 0.01
            move  (case control
                    ::forward  (mapv #(* % speed delta-time) (-> direction (assoc 1 0)))
                    ::backward (mapv #(* % speed delta-time -1) (-> direction (assoc 1 0)))
                    ::strafe-l (mapv #(* % speed delta-time -1) (-> right (assoc 1 0)))
                    ::strafe-r (mapv #(* % speed delta-time) (-> right (assoc 1 0)))
                    nil)]
        (when move
          (insert! ::player ::position (mapv + position move))))]

     ::direct-for-n-frame
     [:what
      [::player ::view-mode ::direct]
      [::window/window ::window/dimension dimension]
      [::player ::frame-counter frame {:then false}]
      :then
      (if (> frame 0)
        (insert! ::player ::frame-counter (dec frame))
        (s-> session
             (o/insert ::player ::view-mode ::moving)
             (o/retract ::player ::frame-counter)))]

     ::mouse-camera
     [:what
      [::time/now ::time/delta delta-time]
      [::window/window ::window/dimension dimension]
      [::player ::view-mode ::moving]
      [::player ::view-dx view-dx]
      [::player ::view-dy view-dy]
      [::player ::position position {:then false}]
      [::player ::horiz-angle horiz-angle {:then false}]
      [::player ::verti-angle verti-angle {:then false}]
      :then
      (let [mouse-speed  0.001
            horiz-angle  (+ horiz-angle (* mouse-speed (or (- view-dx) 0)))
            verti-angle  (-> (+ verti-angle (* mouse-speed (or (- view-dy) 0)))
                             (max (- (/ Math/PI 2)))
                             (min (/ Math/PI 2)))]
        (insert! ::player (calc-mvp dimension position horiz-angle verti-angle)))]})})
