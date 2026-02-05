(ns minusthree.stage.sankyuu
  (:require
   [engine.macros :refer [s->]]
   [engine.math.primitives :as primitives]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp-lwjgl :as assimp-lwjgl]
   [minustwo.stage.wirecube :as wirecube]
   [odoyle.rules :as o]))

;; 39

(defn load-model-fn [esse-id model-path]
  (fn []
    ;; initially we tried do gl stuff inside load-model-fn but it turns out opengl context only works in one thread
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
     [esse-id ::gltf/data gltf-data]
     [esse-id ::gltf/bins bins]
     [esse-id ::loading/state :success]
     :then
     (let [ctx          nil ; for now, since jvm doesn't need it
           program-info (cljgl/create-program-info-from-iglu ctx wirecube/the-vertex-shader wirecube/the-fragment-shader)
           gltf-chant   (gltf/gltf-spell gltf-data (first bins) {:model-id esse-id :use-shader program-info})
           summons      (gl-magic/cast-spell ctx gltf-chant)
           gl-facts     (::gl-magic/facts summons)
           gl-data      (::gl-magic/data summons)]
       #_{:clj-kondo/ignore [:inline-def]}
       (def debug-var summons)
       (println esse-id "is loaded!")
       (s-> (reduce o/insert session gl-facts)
            (o/insert esse-id {::gl-magic/casted? true
                               ::gl-magic/data gl-data
                               ::shader/program-info program-info})))]

    ::render-data
    [:what
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gltf/primitives primitives]
     :then
     (println esse-id "ready to render!")]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

#?(:clj
   (comment
     (require '[com.phronemophobic.viscous :as viscous])

     (-> debug-var ffirst)

     (viscous/inspect debug-var)))
