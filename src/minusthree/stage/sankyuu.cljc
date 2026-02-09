(ns minusthree.stage.sankyuu
  (:require
   [engine.macros :refer [s->]]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.stage.model :as model]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp-lwjgl :as assimp-lwjgl]
   [minustwo.stage.wirecube :as wirecube]
   [minustwo.systems.transform3d :as t3d]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]))

;; 39

(defn load-model-fn [esse-id model-path]
  (fn []
    ;; initially we tried do gl stuff inside load-model-fn but it turns out opengl context only works in one thread
    (let [[gltf bin] #?(:clj  (assimp-lwjgl/load-model model-path "gltf2")
                        :cljs [:TODO assimp-lwjgl/load-model model-path])]
      [[esse-id ::gltf/data gltf]
       [esse-id ::gltf/bins [bin]]])))

(def gltf-vert
  (str cljgl/version-str
       "
   precision mediump float;
   
   in vec3 POSITION;
   in vec3 NORMAL;
   in vec2 TEXCOORD_0;
   
   uniform mat4 u_model;
   uniform mat4 u_view;
   uniform mat4 u_projection;
   
   out vec3 Normal;
   out vec2 TexCoord;
  
   void main() {
     gl_Position = u_projection * u_view * u_model * vec4(POSITION, 1.0);
     Normal = NORMAL;
     TexCoord = TEXCOORD_0;
   }
  "))

(def gltf-frag
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 Normal;
 in vec2 TexCoord;
 
 uniform sampler2D u_mat_diffuse;
 
 out vec4 o_color;

 void main() {
   o_color = vec4(texture(u_mat_diffuse, TexCoord).rgb, 1.0); 
 }
"))

(defn init-fn [world _game]
  (let [ctx nil]
    (-> world
      ;; miku is error for now (current behaviour = assert exception only prints, game doesn't crash)
        (esse ::wolfie model/biasa
              (loading/push (load-model-fn ::wolfie "assets/models/SilverWolf/SilverWolf.pmx"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx gltf-vert gltf-frag)})
        (esse ::wirebeing model/biasa
              (loading/push (load-model-fn ::wirebeing "assets/wirebeing.glb"))
              {::shader/program-info (cljgl/create-program-info-from-iglu ctx wirecube/the-vertex-shader wirecube/the-fragment-shader)
               ::t3d/translation (v/vec3 -2.0 8.0 0.0)}))))

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
   ::world/rules #'rules})

#?(:clj
   (comment
     (require '[com.phronemophobic.viscous :as viscous])

     (-> debug-var ffirst)

     (viscous/inspect debug-var)))
