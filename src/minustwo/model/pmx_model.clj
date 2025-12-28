(ns minustwo.model.pmx-model
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.model.pmx-parser :refer [parse-pmx]]
   [odoyle.rules :as o]))

(s/def ::model-path string?)
(s/def ::data map?)

(def rules
  (o/ruleset
   {::models-to-load
    [:what [esse-id ::model-path model-path]]}))

(defonce debug-data* (atom {}))

(defn accumulate-face-count [rf]
  (let [acc (volatile! 0)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (let [input (assoc input :face-offset @acc)]
         (vswap! acc + (:face-count input))
         (rf result input))))))

(defn reduce-to-WEIGHTS-and-JOINTS
  "assuming all bones weights are :BDEF1, :BDEF2, or :BDEF4"
  [len]
  (let [WEIGHTS (float-array (* 4 len))
        JOINTS  (int-array (* 4 len))]
    (fn
      ([] 0)
      ([_counter] (vars->map WEIGHTS JOINTS))
      ([counter weight]
       (let [i (* counter 4)
             b1 (or (:bone-index1 weight) 0)
             b2 (or (:bone-index2 weight) 0)
             b3 (or (:bone-index3 weight) 0)
             b4 (or (:bone-index4 weight) 0)]
         (case (:weight-type weight)
           :BDEF1 (aset WEIGHTS i 1.0)
           :BDEF2
           (let [w1 (:weight1 weight) w2 (- 1.0 w1)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2)))
           :BDEF4
           (let [w1 (:weight1 weight) w2 (:weight2 weight) w3 (:weight3 weight) w4 (:weight4 weight)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2) (aset (+ 2 i) w3) (aset (+ 3 i) w4)))
           :noop)
         (doto JOINTS (aset i b1) (aset (+ 1 i) b2) (aset (+ 2 i) b3) (aset (+ 3 i) b4)))
       (inc counter)))))

(defn load-pmx-model [model-path]
  (let [res-path  (str "public" java.io.File/separator model-path)
        pmx-data  (time (parse-pmx res-path))
        vertices  (:vertices pmx-data)
        POSITION  (float-array (into [] (mapcat :position) vertices))
        NORMAL    (float-array (into [] (mapcat :normal) vertices))
        TEXCOORD  (float-array (into [] (mapcat :uv) vertices))
        vert-wj   (transduce (map :weight)
                             (reduce-to-WEIGHTS-and-JOINTS (count vertices))
                             vertices)
        WEIGHTS   (:WEIGHTS vert-wj)
        JOINTS    (:JOINTS vert-wj)
        INDICES   (int-array (into [] (:faces pmx-data)))
        parent    (:parent-dir pmx-data)
        textures  (into [] (map #(str parent java.io.File/separator %)) (:textures pmx-data))
        materials (into [] accumulate-face-count (:materials pmx-data))]
    (vars->map pmx-data
               POSITION NORMAL TEXCOORD WEIGHTS JOINTS INDICES
               textures materials)))

(defn load-models-from-world*
  [models-to-load world*]
  (doseq [{:keys [esse-id model-path]} models-to-load]
    (println "[minustwo-pmx] loading pmx model" esse-id)
    (swap! world* o/retract esse-id ::model-path)
    (let [data (load-pmx-model model-path)]
      (swap! debug-data* assoc esse-id data)
      (println "[minustwo-pmx]" esse-id "=> loaded!")
      (swap! world* o/insert esse-id {::data data ::gl-magic/casted? :pending}))))

(def system
  {::world/rules rules})

(comment
  (require '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect (-> @debug-data*
                       :minustwo.stage.pmx-renderer/silverwolf-pmx
                       :pmx-data))

  :-)