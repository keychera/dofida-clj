(ns playground
  (:require
   [clojure.walk :as walk]
   [engine.world :as world]
   [engine.engine :as engine]
   [engine.game :as game]
   [minustwo.gl.gltf :as gltf]
   [odoyle.rules :as o]
   [minustwo.systems :as systems]))

(defn limited-game-loop
  ([loop-fn end-fn how-long]
   (limited-game-loop loop-fn end-fn {:total (js/performance.now) :delta 0} how-long))
  ([loop-fn end-fn time-data how-long]
   (if (> how-long 0)
     (js/requestAnimationFrame
      (fn [ts]
        (let [delta (- ts (:total time-data))
              time-data (assoc time-data :total ts :delta delta)]
          (loop-fn time-data)
          (limited-game-loop loop-fn end-fn time-data (- how-long delta)))))
     (end-fn))))

(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))

(comment
  (do
    (assert systems/all)
    (-> (game/->game {:webgl-context ctx
                      :total-time 0
                      :delta-time 0})
        (engine/init)
        ((fn [game]
           #_{:clj-kondo/ignore [:inline-def]}
           (def hmm game)
           (limited-game-loop
            (fn [{:keys [total delta]}]
              (engine/tick (assoc game
                                  :total-time total
                                  :delta-time delta)))
            (fn []
              (println "done!"))
            5000)))))

  (o/query-all @(::world/atom* hmm))

  (let [{:keys [gltf-data bin]} (-> @gltf/debug-data* ::rubahperak)
        gltf-spell (gltf/gltf-spell gltf-data bin {:model-id :hmm
                                                   :use-shader :hmm
                                                   :tex-unit-offset 0})]
    (->> (walk/postwalk (fn [x] (if (instance? js/Uint8Array x) ['uint-arr (.-length x)] x)) gltf-spell)
         (drop 310)))

  :-)
