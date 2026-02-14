(ns minusthree.stage.sankyuu
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.macros :refer [s->]]
   [fastmath.core :as m]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.anime :as anime]
   [minusthree.engine.loading :as loading]
   [minusthree.model.assimp-lwjgl :refer [load-gltf-fn]]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.gltf :as gltf]
   [minusthree.stage.model :as model]
   [minusthree.stage.shaderdef :as shaderdef]
   [minustwo.gl.constants :refer [GL_DYNAMIC_DRAW GL_UNIFORM_BUFFER]]
   [minustwo.gl.shader :as shader]
   [odoyle.rules :as o]))

;; 39

(defn create-ubo [ctx size to-index]
  (let [ubo (gl ctx genBuffers)]
    (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
    (gl ctx bufferData GL_UNIFORM_BUFFER size GL_DYNAMIC_DRAW)
    (gl ctx bindBufferBase GL_UNIFORM_BUFFER to-index ubo)
    (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
    ubo))

(defn init-fn [world _game]
  (let [ctx nil]
    (-> world
        (esse ::skinning-ubo {::model/ubo (create-ubo ctx (* shaderdef/MAX_JOINTS 16 4) 0)})
        (esse ::miku
              (loading/push (load-gltf-fn ::miku "assets/models/HatsuneMiku/Hatsune Miku.pmx"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/gltf-vert shaderdef/gltf-frag)})
        (esse ::wolfie model/biasa
              (loading/push (load-gltf-fn ::wolfie "assets/models/SilverWolf/SilverWolf.pmx"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/gltf-vert shaderdef/gltf-frag)})
        (esse ::wirebeing model/biasa
              (loading/push (load-gltf-fn ::wirebeing "assets/wirebeing.glb"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/wirecube-vert shaderdef/wirecube-frag)}))))

(defn post-fn [world _game]
  (-> world
      (esse ::be-cute
            {::anime/duration 1600
             ::anime/bone-animes
             [{"右腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}
              ;; we can actually make this in one map, but I am currently hammock-ing about
              ;; how to compose this in a way that time is defined first
              {"左腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.25 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.75 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}]})
      (esse ::wolfie {::anime/use ::be-cute})
      (esse ::wirebeing {::t3d/translation (v/vec3 -5.0 8.0 0.0)})))

(def rules
  (o/ruleset
   {::wolfie
    [:what
     [esse-id ::gltf/data gltf-data]
     [esse-id ::gltf/bins bins]
     [esse-id ::loading/state :success]
     [esse-id ::shader/program-info program-info]
     :then
     (let [ctx          nil ; for now, since jvm doesn't need it 
           gltf-chant   (gltf/gltf-spell gltf-data (first bins) {:model-id esse-id :use-shader program-info})
           summons      (gl-magic/cast-spell ctx esse-id gltf-chant)
           gl-facts     (::gl-magic/facts summons)
           gl-data      (::gl-magic/data summons)]
       #_{:clj-kondo/ignore [:inline-def]}
       (def debug-var summons)
       (println esse-id "is loaded!")
       (s-> (reduce o/insert session gl-facts)
            (o/insert esse-id {::gl-magic/casted? true
                               ::gl-magic/data gl-data})))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/post-fn #'post-fn
   ::world/rules #'rules})

#?(:clj
   (comment
     (require '[com.phronemophobic.viscous :as viscous])

     (-> debug-var ffirst)

     (viscous/inspect debug-var)))
