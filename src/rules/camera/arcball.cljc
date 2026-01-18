(ns rules.camera.arcball
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q #?@(:cljs [:refer [Quat4]])]
   [thi.ng.geom.vector :as v #?@(:cljs [:refer [Vec3]])]
   [thi.ng.math.core :as m])
  #?(:clj
     (:import
      [thi.ng.geom.quaternion Quat4]
      [thi.ng.geom.vector Vec3])))

;; https://github.com/Samson-Mano/Quaternion_Arcball_3D_Rotation
;; https://eater.net/quaternions/video/intro this one is game changing, 3Blue1Brown the goats

(defn reset-rot [world]
  (-> world
      (o/insert ::camera ::rot-quat (q/quat))
      (o/insert ::camera ::new-quat (q/quat))))

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
(s/def ::new-quat #(instance? Quat4 %))
(s/def ::state #{::rotation-in-progress ::rotation-stops})

(defn v3-on-arcball [{:keys [width height]} x y]
  (let [radius (max width height)
        cx     (/ width 2)
        cy     (/ height 2)
        x      (/ (- x cx) (/ radius 2))
        y      (/ (- cy y) (/ radius 2))
        z      (Math/sqrt (- 1 (min 1 (* x x) (* y y))))]
    (v/vec3 x y z)))

(def system
  {::world/init-fn
   (fn [world _game] (reset-rot world))

   ::world/rules
   (o/ruleset
    {::rotation-in-progress
     [:what
      [::camera ::state ::rotation-in-progress]
      [::camera ::x-on-plane x]
      [::camera ::y-on-plane y]
      [:window/window :window/dimension dimension]
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
      [::camera ::rot-quat   rot-quat]
      :then
      (let [new-quat (q/alignment-quat start end)]
        (insert! ::camera ::new-quat (m/* rot-quat new-quat)))]

     ::rot-quat
     [:what
      [::camera ::rot-quat rot-quat]
      [::camera ::new-quat new-quat]]

     ::rotation-stops
     [:what
      [::camera ::state ::rotation-stops]
      [::camera ::rot-quat rot-quat {:then false}]
      [::camera attr value {:then false}]
      :when (#{::start-vec3 ::end-vec3 ::new-quat} attr)
      :then
      (cond
        (= ::new-quat attr) (s-> session
                                 (o/insert ::camera ::rot-quat value)
                                 (o/insert ::camera ::new-quat (q/quat)))
        :else (s-> session (o/retract ::camera attr)))]})})

(comment
  (let [start (v3-on-arcball {:width 1} 0.5 0.5)
        end   (v3-on-arcball {:width 1} -0.3 -1.0)
        axis  (m/cross start end)
        angle (m/dot start end)]
    (-> (q/quat-from-axis-angle axis angle)
        (g/as-matrix))))