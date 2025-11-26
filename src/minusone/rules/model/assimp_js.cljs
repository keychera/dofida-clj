(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]))

(defonce gl-context
  (-> (js/document.querySelector "canvas")
      (.getContext "webgl2" (clj->js {:premultipliedAlpha false}))))

(comment
  (let [files (eduction
               (map #(str "assets/models/" %))
               ["moon.glb"])]
    (-> (assimpjs)
        (.then
         (fn [ajs]
           (-> (->> files
                    (map (fn [file] (-> file js/fetch (.then (fn [res] (.arrayBuffer res))))))
                    js/Promise.all)
               (.then
                (fn [arrayBuffers]
                  (let [ajs-file-list (new (.-FileList ajs))]
                    (dotimes [i (count files)]
                      (.AddFile ajs-file-list (nth files i) (js/Uint8Array. (aget arrayBuffers i))))
                    (def hmm (.ConvertFileList ajs ajs-file-list "gltf2"))))))))))

  (->> (.GetFile hmm 0) ;; oh wait, those two file combines into one!
       (.GetContent)
       (.decode (js/TextDecoder.))
       (js/JSON.parse)
       ((fn [json] (-> json
                       (js->clj :keywordize-keys true)
                       (dissoc :images)))))

  (->> (.GetFile hmm 1) ;; this is the result.bin
       (.GetContent)) ;; is Uint8Array !!!

  (require '[play-cljc.macros-js :refer-macros [gl]])
  
  (let [game {:context gl-context}]
    (gl game clearColor 0.02 0.02 0.04 1.0)
    (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

  :-)
