(ns engine.math.easings)

(defn ease-out-sine [n]
  (Math/sin (/ (* Math/PI n) 2)))

(defn ease-out-expo [n]
  (- 1 (Math/pow 2 (* -10 n))))

(defn ease-out-back [n]
  (let [c1 1.70158
        c3 (+ c1 1.0)]
    (+ 1.0 (* c3 (Math/pow (- n 1.0) 3)) (* c1 (Math/pow (- n 1.0) 2)))))
