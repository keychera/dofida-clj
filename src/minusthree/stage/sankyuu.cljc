(ns minusthree.stage.sankyuu
  (:require
   [minusthree.engine.loading :as loading]
   [minusthree.engine.world :as world]
   [minustwo.gl.gltf :as gltf]
   [minustwo.model.assimp-lwjgl :as assimp-lwjgl]
   [odoyle.rules :as o]))

;; 39

(defn load-model-fn [esse-id model-path]
  (fn []
    (let [[gltf bin] #?(:clj  (assimp-lwjgl/load-model model-path "gltf2")
                        :cljs [model-path :todo])]
      [[esse-id ::gltf/data gltf]
       [esse-id ::gltf/bins [bin]]])))

(defn init-fn [world _game]
  (-> world
      ;; miku is error for now (current behaviour = assert exception only prints, game doesn't crash)
      (loading/insert-load-fn ::miku (load-model-fn ::miku "assets/models/default-miku/HatsuneMiku.pmx"))
      (loading/insert-load-fn ::wirebeing (load-model-fn ::wirebeing "assets/wirebeing.glb"))))

(def rules
  (o/ruleset
   {::miku
    [:what
     [esse-id ::gltf/data gltf]
     [esse-id ::gltf/bins bins]
     [esse-id ::loading/state :success]
     :then
     (println esse-id "loaded!")
     #_{:clj-kondo/ignore [:inline-def]}
     (def debug-gltf gltf)]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

#?(:clj
   (comment
     (import [java.nio.file Files] [java.nio.file LinkOption])
     (require '[engine.macros :refer [public-resource-path]])
     (Files/exists (.resolve public-resource-path "assets/models/default-miku/HatsuneMiku.pmx") (into-array LinkOption []))))
