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

(defn load-pmx-model [model-path]
  (let [res-path  (str "public" java.io.File/separator model-path)
        pmx-data  (time (parse-pmx res-path))
        POSITION  (float-array (into [] (mapcat :position) (:vertices pmx-data)))
        NORMAL    (float-array (into [] (mapcat :normal) (:vertices pmx-data)))
        TEXCOORD  (float-array (into [] (mapcat :uv) (:vertices pmx-data)))
        INDICES   (int-array (into [] (:faces pmx-data)))
        parent    (:parent-dir pmx-data)
        textures  (into [] (map #(str parent java.io.File/separator %)) (:textures pmx-data))
        materials (into [] accumulate-face-count (:materials pmx-data))]
    (vars->map pmx-data POSITION NORMAL TEXCOORD INDICES textures materials)))

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
        accumulate-face-count
        (-> @debug-data*
            :minustwo.stage.pmx-renderer/silverwolf-pmx
            :pmx-data
            :materials))
  :-)