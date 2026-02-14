(ns minusthree.anime.bones
  (:require
   [clojure.spec.alpha :as s]
   [engine.sugar :refer [f32-arr]]
   [fastmath.matrix :as mat]))

(s/def ::data vector?)

(defn create-joint-mats-arr [bones]
  (let [f32s (f32-arr (* 16 (count bones)))]
    (doseq [{:keys [joint-id inv-bind-mat global-transform]} bones]
      (let [joint-mat (mat/mulm inv-bind-mat global-transform)
            i         (* joint-id 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (float (get (mat/row joint-mat (quot j 4)) (mod j 4)))))))
    f32s))
