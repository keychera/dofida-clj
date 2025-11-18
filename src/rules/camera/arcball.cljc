(ns rules.camera.arcball
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.window :as window]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q :refer [Quat4]]
   [thi.ng.geom.vector :as v :refer [Vec3]]
   [thi.ng.math.core :as m]))

;; https://github.com/Samson-Mano/Quaternion_Arcball_3D_Rotation
;; https://eater.net/quaternions/video/intro this one is game changing, 3Blue1Brown the goats

(defn start-rotating [world]
  (o/insert world ::camera ::state ::rotation-in-progress))

(defn send-xy [world x y] 
  (o/insert world ::camera {::x-on-plane x ::y-on-plane y}))

(defn stop-rotating [world]
  (o/insert world ::camera ::state ::rotation-stops))

(s/def ::x-on-plane number?)
(s/def ::y-on-plane number?)
(s/def ::start-vec3 #(instance? Vec3 %))
(s/def ::end-vec3 #(instance? Vec3 %))
(s/def ::rot-quat #(instance? Quat4 %))
(s/def ::state #{::rotation-in-progress ::rotation-stops})

(defn v3-on-arcball [dimension x y]
  (let [half-w (/ (:width dimension) 2)
        x      (/ (- x half-w) half-w)
        y      (/ (- y half-w) half-w)
        z      (Math/sqrt (- 1 (* x x) (* y y)))]
    (v/vec3 x y z)))

(def system
  {::world/rules
   (o/ruleset
    {::rotation-in-progress
     [:what
      [::camera ::state ::rotation-in-progress]
      [::camera ::x-on-plane x]
      [::camera ::y-on-plane y]
      [::window/window ::window/dimension dimension]
      :then
      (if-not (seq (o/query-all session ::start-vec3))
        (s-> session (o/insert ::camera ::start-vec3 (v3-on-arcball dimension x y)))
        (s-> session (o/insert ::camera ::end-vec3   (v3-on-arcball dimension x y))))]

     ::start-vec3
     [:what [::camera ::start-vec3 start]]

     ::calculate-quaternion
     [:what
      [::camera ::start-vec3 start]
      [::camera ::end-vec3   end]
      :then
      (let [axis     (m/cross start end)
            angle    (m/dot start end)
            rot-quat (q/quat-from-axis-angle axis angle)] 
        (insert! ::camera ::rot-quat rot-quat))]

     ::rot-quat
     [:what [::camera ::rot-quat rot-quat]]

     ::rotation-stops
     [:what
      [::camera ::state control]
      [::camera ::start-vec3 _]
      [::camera ::end-vec3 _]
      [::camera ::rot-quat _]
      :then
      (when (= control ::rotation-stops)
        (s-> session
             (o/retract ::camera ::start-vec3)
             (o/retract ::camera ::end-vec3)
             (o/retract ::camera ::rot-quat)
             (o/retract ::camera ::state)))]})})

(comment
  (let [start (v3-on-arcball {:width 1} 0.5 0.5)
        end   (v3-on-arcball {:width 1} -0.3 -1.0)
        axis  (m/cross start end)
        angle (m/dot start end)]
    (-> (q/quat-from-axis-angle axis angle)
        (g/as-matrix))))