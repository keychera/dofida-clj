(ns engine.math.easings)

(defn ease-out-sine [n]
  (Math/sin (/ (* Math/PI n) 2)))

(defn ease-out-expo [n]
  (- 1 (Math/pow 2 (* -10 n))))
