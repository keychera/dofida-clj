(ns minustwo.model.pmx-model
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.math :as m-ext]
   [engine.world :as world]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.texture :as texture]
   [minustwo.model.pmx-parser :refer [parse-pmx]]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::model-path string?)
(s/def ::config (s/keys :req-un [::texture/tex-unit-offset]))
(s/def ::bones vector?)
(s/def ::data (s/keys :req-un [::bones]))

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

(defn pmx-coord->opengl-coord [[x y z]] [x y (- z)])
(defn ccw-face [faces]
  (into [] (comp (partition-all 3) (map (fn [[i0 i1 i2]] [i0 i2 i1])) cat) faces))

(defn resolve-pmx-bones
  "current understanding: pmx bones only have global translation each bones
   this fn will resolve the value of local translation, rotation, invert bind matrix, etc.
   local transform need to be calculated from trans and rot (no scale for now).
   
   assumes bone have :idx denoting its own position in the vector"
  [rf]
  (let [parents-pos! (volatile! {})]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result {:keys [idx parent-bone-idx position] :as bone}]
       (let [position     (pmx-coord->opengl-coord position)
             parent       (get @parents-pos! parent-bone-idx)
             local-pos    (if parent
                            (v/vec3 (mapv - position (:position parent)))
                            (v/vec3))
             global-trans (m-ext/translation-mat position)
             inv-bind-mat (m/invert global-trans)
             updated-bone (-> bone
                              (update-keys (fn [k] (or ({:local-name :name} k) k)))
                              (dissoc :position)
                              (assoc :translation      local-pos
                                     :rotation         (q/quat)
                                     :inv-bind-mat     inv-bind-mat
                                     :global-transform global-trans
                                     :parent-transform (:transform parent)))]
         (vswap! parents-pos! assoc idx {:position  position
                                         :transform global-trans})
         (rf result updated-bone))))))

(defn calc-local-transform [{:keys [translation rotation]}]
  (let [trans-mat    (m-ext/translation-mat translation)
        rot-mat      (g/as-matrix rotation)
        local-trans  (m/* trans-mat rot-mat)]
    local-trans))

(defn global-transform-xf [rf]
  (let [parents! (volatile! {})]
    (fn
      ([] (rf))
      ([result]
       (rf result))
      ([result {:keys [idx parent-bone-idx] :as bone}]
       (let [local-trans  (calc-local-transform bone)
             parent       (get @parents! parent-bone-idx)
             global-trans (if parent
                            (m/* (:global-transform parent) local-trans)
                            local-trans)
             updated-bone (assoc bone :global-transform global-trans)]
         (vswap! parents! assoc idx updated-bone)
         (rf result updated-bone))))))

(defn load-pmx-model [model-path]
  (let [res-path  (str "public" java.io.File/separator model-path)
        pmx-data  (time (parse-pmx res-path))
        vertices  (:vertices pmx-data)
        POSITION  (float-array (into [] (comp (map :position) (map pmx-coord->opengl-coord) cat) vertices))
        NORMAL    (float-array (into [] (comp (map :normal) (map pmx-coord->opengl-coord) cat) vertices))
        TEXCOORD  (float-array (into [] (mapcat :uv) vertices))
        vert-wj   (transduce (map :weight)
                             (reduce-to-WEIGHTS-and-JOINTS (count vertices))
                             vertices)
        WEIGHTS   (:WEIGHTS vert-wj)
        JOINTS    (:JOINTS vert-wj)
        INDICES   (int-array (ccw-face (:faces pmx-data)))
        parent    (:parent-dir pmx-data)
        textures  (into [] (map #(str parent java.io.File/separator %)) (:textures pmx-data))
        materials (into [] accumulate-face-count (:materials pmx-data))
        bones     (into []
                        (comp (map-indexed (fn [idx bone] (assoc bone :idx idx)))
                              resolve-pmx-bones)
                        (:bones pmx-data))
        morphs    (:morphs pmx-data)]
    (vars->map POSITION NORMAL TEXCOORD WEIGHTS JOINTS INDICES
               textures materials bones morphs)))

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

  (viscous/inspect @debug-data*)

  (into []
        (comp (drop 160) (take 10))
        (-> @debug-data* first second
            :pmx-data :bones))

  :-)