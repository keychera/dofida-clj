(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minusone.rules.model.assimp :as assimp]
   [odoyle.rules :as o]
   [clojure.string :as str]))

(defn data-uri->header+Uint8Array [data-uri]
  (let [[header base64-str] (.split data-uri ",")]
    [header (js/Uint8Array.fromBase64 base64-str)]))

;; following this on loading image to bindTexture with blob
;; https://webglfundamentals.org/webgl/lessons/webgl-qna-how-to-load-images-in-the-background-with-no-jank.html
(defn data-uri->ImageBitmap
  "parse data-uri and pass the resulting bitmap to callback.
   callback will receive {:keys [bitmap width height]}"
  [data-uri callback]
  (let [[header uint8-arr] (data-uri->header+Uint8Array data-uri)
        data-type          (second (re-matches #"data:(.*);.*" header))
        blob               (js/Blob. #js [uint8-arr] #js {:type data-type})]
    (.then (js/createImageBitmap blob)
           (fn [bitmap]
             (let [width  (.-width bitmap)
                   height (.-height bitmap)]
               (println "blob:" data-type "count" (.-length uint8-arr))
               (callback (vars->map bitmap width height)))))))

;; https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs
;; drop core.async, error msg are not nice there
(defn then-load-model
  "promise to load model and pass the result via .then callback.
   callback will receive {:keys [gltf bins]}"
  [files callback]
  (.then (assimpjs)
         (fn [ajs]
           (.then (->> files
                       (map (fn [f] (-> f js/fetch (.then (fn [res] (.arrayBuffer res))))))
                       js/Promise.all)
                  (fn [arr-bufs]
                    (let [ajs-file-list (new (.-FileList ajs))]
                      (dotimes [i (count files)]
                        (.AddFile ^js ajs-file-list (nth files i) (js/Uint8Array. (aget arr-bufs i))))
                      (let [result (.ConvertFileList ^js ajs ajs-file-list "gltf2")
                            gltf   (->> (.GetFile ^js result 0)
                                        (.GetContent)
                                        (.decode (js/TextDecoder.))
                                        (js/JSON.parse)
                                        ((fn [json] (-> json (js->clj :keywordize-keys true)))))
                            dir    (-> (first files) (str/split "/") butlast ((fn [p] (str/join "/" p))))
                            gltf   (assoc-in gltf [:asset :dir] dir)
                            ;; only handle one binary for now, (TODO use .FileCount)
                            bins   [(->> (.GetFile result 1) (.GetContent))]]
                        (callback (vars->map gltf bins)))))))))

(def rules
  (o/ruleset
   {::assimp/load-with-assimp
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
       model-files
       (fn [{:keys [gltf bins]}]
         (println "[assimp-js] loaded" esse-id)
         (swap! world* o/insert esse-id
                {::assimp/gltf gltf
                 ::assimp/bins bins}))))))

(def system
  {::world/rules rules})

;; REPL playground starts here
