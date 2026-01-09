(ns minustwo.systems.view.firstperson
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.types :as types]
   [engine.world :as world]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.camera :as camera]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::front    ::types/vec3)
(s/def ::move-control keyword?)
(s/def ::view-dx float?)
(s/def ::view-dy float?)
(s/def ::yaw float?)   ;; radians
(s/def ::pitch float?) ;; radians

(def up (v/vec3 0.0 1.0 0.0)) ; y points towards the sky
(defonce reset-pos* (atom []))

(defn insert-fps-cam
  ([world position front] (insert-fps-cam world position front (* -0.5 Math/PI) 0.0))
  ([world position front yaw pitch]
   (reset! reset-pos* [position front yaw pitch])
   (-> world
       (o/insert ::player {::camera/position position})
       (o/insert ::player {::front front ::yaw yaw ::pitch pitch}))))

(defn reset-fps-cam [world]
  (let [[position front yaw pitch] @reset-pos*]
    (insert-fps-cam world position front yaw pitch)))

;; you wrapped me completely, you surrounded me with your warmth
;; a little bit of motion of love is all you need to conquer me

(def rules
  (o/ruleset
   {::firstperson-view
    [:what
     [::player ::camera/position position]
     [::player ::front    front]
     :then
     (insert! ::player ::camera/view-matrix (mat/look-at position (m/+ position front) up))]

    ::movement
    [:what
     [::time/now ::time/raw-delta delta-time]
     [::player ::camera/position position {:then false}]
     [::player ::front front {:then false}]
     [::player ::move-control control {:then false}]
     :then 
     (let [speed   0.05
           right   (m/normalize (m/cross front up))
           [x _ z] (case control
                     ::forward  (m/* front (* delta-time speed))
                     ::backward (m/* front (* delta-time speed -1))
                     ::strafe-l (m/* right (* delta-time speed -1))
                     ::strafe-r (m/* right (* delta-time speed))
                     nil)
           move    (v/vec3 x 0.0 z)] 
       (insert! ::player ::camera/position (m/+ position move)))]

    ::mouse-camera
    [:what
     [::time/now ::time/delta delta-time]
     [::time/now ::time/slice 1]
     [::player ::view-dx view-dx]
     [::player ::view-dy view-dy]
     [::player ::yaw yaw {:then false}]
     [::player ::pitch pitch {:then false}]
     :then
     (let [speed 0.0033
           yaw   (+ yaw (* speed (or view-dx 0)))
           pitch (-> (+ pitch (* speed (or (- view-dy) 0)))
                     (max (- (/ Math/PI 2)))
                     (min (/ Math/PI 2)))
           front (v/vec3 (* (Math/cos yaw) (Math/cos pitch))
                         (Math/sin pitch)
                         (* (Math/sin yaw) (Math/cos pitch)))]
       (insert! ::player
                {::yaw yaw
                 ::pitch pitch
                 ::front (m/normalize front)}))]}))

(def system
  {::world/rules #'rules})
