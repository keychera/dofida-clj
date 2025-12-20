(ns minustwo.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   [clojure.string :as str]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.model.assimp :as assimp]
   [odoyle.rules :as o]))

;; https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs
;; drop core.async, error msg are not nice there
(defn then-load-model
  "promise to load model and pass the result via .then callback.
   callback will receive {:keys [gltf bins]}"
  [files export-format callback]
  (.then (assimpjs)
         (fn [ajs]
           (.then (->> files
                       (map (fn [f] (-> f js/fetch (.then (fn [res] (.arrayBuffer res))))))
                       js/Promise.all)
                  (fn [arr-bufs]
                    (let [ajs-file-list (new (.-FileList ajs))]
                      (dotimes [i (count files)]
                        (.AddFile ^js ajs-file-list (nth files i) (js/Uint8Array. (aget arr-bufs i))))
                      (let [result (.ConvertFileList ^js ajs ajs-file-list export-format)
                            gltf   (->> (.GetFile ^js result 0)
                                        (.GetContent)
                                        (.decode (js/TextDecoder.))
                                        (js/JSON.parse)
                                        ((fn [json] (-> json (js->clj :keywordize-keys true)))))
                            dir    (-> (first files) (str/split "/") butlast ((fn [p] (str/join "/" p))))
                            json   (assoc-in gltf [:asset :dir] dir)
                            ;; only handle one binary for now, (TODO use .FileCount)
                            bins   [(->> (.GetFile result 1) (.GetContent))]]
                        (callback (vars->map json bins)))))))))

(def rules
  (o/ruleset
   {::assimp/model-to-load
    [:what
     [esse-id ::assimp/model-to-load model-files]]}))

(defn load-models-from-world*
  "load models from world* and fire callback for each models loaded.
   this will retract the ::assimp/model-to-load facts"
  [models-to-load world*]
  (doseq [model-to-load models-to-load]
    (let [model-files (:model-files model-to-load)
          esse-id     (:esse-id model-to-load)]
      (println "[assimp-js] loading model" esse-id)
      (swap! world* o/retract esse-id ::assimp/model-to-load)
      (then-load-model
       model-files "gltf2"
       (fn [{:keys [json bins]}]
         (println "[assimp-js] loaded" esse-id)
         (swap! world* o/insert esse-id {::gltf/data json ::gltf/bins bins ::gl-magic/casted? :pending}))))))

(def system
  {::world/rules rules})
