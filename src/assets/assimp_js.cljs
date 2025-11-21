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
                  (js/console.log arrayBuffers)
                  [ajs])))))))

  hmm)
