(ns assets.assimp-js
  (:require
   ["assimpjs" :as assimpjs]))

(comment
  (let [files ["assets/defaultcube.obj"
               "assets/dofida.mtl"]]
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
                    (def hmm (.ConvertFileList ajs ajs-file-list "assjson"))))))))))

  (js->clj (->> (.GetFile hmm 0) ;; file 1 will throw error
                (.GetContent)
                (.decode (js/TextDecoder.))
                (js/JSON.parse))
           :keywordize-keys true))
