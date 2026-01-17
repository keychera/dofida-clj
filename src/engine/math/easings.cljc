(ns engine.math.easings 
  (:require
   [engine.math :as m-ext]))

(defn ease-out-sine [n]
  (Math/sin (/ (* Math/PI n) 2)))

(defn ease-out-expo [n]
  (- 1 (Math/pow 2 (* -10 n))))

(defn ease-out-back [n]
  (let [c1 1.70158
        c3 (+ c1 1.0)]
    (+ 1.0 (* c3 (Math/pow (- n 1.0) 3)) (* c1 (Math/pow (- n 1.0) 2)))))

(defn zero-to-one-ease
  ([ease-fn] (zero-to-one-ease ease-fn 0.0 1.0))
  ([ease-fn small-val] (zero-to-one-ease ease-fn small-val 1.0))
  ([ease-fn small-val big-val]
   (fn [n]
     (let [sv (ease-fn small-val)
           r (/ (- (ease-fn n) sv)
                (- (ease-fn big-val) sv))]
       (m-ext/clamp r 0.0 1.0)))))
