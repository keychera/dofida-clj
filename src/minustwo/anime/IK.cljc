(ns minustwo.anime.IK
  "inverse kinematic"
  (:require
   [engine.math :as m-ext]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.math.core :as m]))

;; https://stackoverflow.com/a/11741520/8812880
(defn rotatation-of-u->v [u v]
  (let [k-cosθ (m/dot u v)
        k      (Math/sqrt (* (m/mag-squared u) (m/mag-squared v)))]
    (if (= (/ k-cosθ k) -1.0)
      (q/quat (m/normalize (m-ext/orthogonal u)) 0.0)
      (m/normalize (q/quat (m/cross u v) (+ k-cosθ k))))))

;; man Idk if this is entirely correct
(defn solve-IK1 [root-t mid-t end-t target-t]
  (let [origin-v     (m/- end-t root-t)
        target-v     (m/- (m/+ target-t end-t) root-t)
        root-angle   (rotatation-of-u->v origin-v target-v)
        [axis angle] (q/as-axis-angle root-angle)

        target-r     (g/rotate-around-axis target-t axis angle)
        AB           (m/mag (m/+ root-t mid-t))
        BC           (m/mag (m/+ mid-t end-t))
        AC           (m/mag (m/+ root-t target-r))
        cos-alpha    (/ (+ (* AB AB) (* AC AC) (* -1.0 BC BC))
                        (* 2.0 AB AC))
        alpha        (m-ext/clamped-acos cos-alpha)
        cos-beta     (/ (+ (* AB AB) (* BC BC) (* -1.0 AC AC))
                        (* 2.0 AB BC))
        beta         (m-ext/clamped-acos cos-beta)
        root-IK      (q/quat-from-axis-angle axis (- alpha))
        mid-IK       (q/quat-from-axis-angle axis (- Math/PI beta))]
    [(m/* root-angle root-IK) mid-IK]))

(defn IK-transducer1
  "transducer assumes input conform to :minustwo.gl.geom/node+transform"
  [a b c target]
  (fn [rf]
    (let [fa (volatile! nil)
          fb (volatile! nil)
          fc (volatile! nil)]
      (fn
        ([] (rf))
        ([result]
         (let [fa @fa fb @fb fc @fc
               root-t (g/transform-vector (:parent-transform fa) (:translation fa))
               mid-t  (g/transform-vector (:parent-transform fb) (:translation fb))
               end-t  (g/transform-vector (:parent-transform fc) (:translation fc))
               [root-IK mid-IK] (solve-IK1 root-t mid-t end-t target)
               result (-> result
                          (assoc! (:idx fa) (update fa :rotation m/* root-IK))
                          (assoc! (:idx fb) (update fb :rotation m/* mid-IK)))]
           (rf result)))
        ([result input]
         (cond
           (= a (:name input)) (vreset! fa input)
           (= b (:name input)) (vreset! fb input)
           (= c (:name input)) (vreset! fc input))
         (rf result input))))))
