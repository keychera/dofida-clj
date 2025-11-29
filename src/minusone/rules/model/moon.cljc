(ns minusone.rules.model.moon
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?@(:cljs [["geotiff" :as geotiff]
              [minusone.rules.model.assimp-js :as assimp-js]])
   [engine.macros :refer [s-> vars->map]]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gl.magic :as gl.magic :refer [gl-incantation]]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.model.assimp :as assimp]
   [minusone.rules.projection :as projection]
   [odoyle.rules :as o]
   [minusone.rules.view.firstperson :as firstperson]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(def moon-vert

  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 NORMAL     vec3
                 TEXCOORD_0 vec2}
   :outputs    '{vpoint vec3
                 normal vec3
                 uv vec2}
   :uniforms   '{u_mvp mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (= gl_Position (* u_mvp (vec4 POSITION "1.0")))
                       (= vpoint POSITION)
                       (= normal NORMAL)
                       (= uv TEXCOORD_0))}})

(def moon-frag
  {:precision  "mediump float"
   :inputs     '{vpoint vec3
                 normal vec3
                 uv vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_light_pos vec3
                 u_light_ambient float
                 u_light_diffuse float
                 u_resolution float
                 u_mat sampler2D
                 u_ldem sampler2D}
   :signatures '{ortho_vec ([vec3] vec3)
                 oriented_matrix ([vec3] mat3)
                 lonlat ([vec3] vec2)
                 color ([vec2] vec3)
                 elevation ([vec3] float)
                 normal_fn ([mat3 vec3] vec3)
                 main ([] void)}
   :functions  '{ortho_vec
                 ([n] (=vec3 b (vec3 0))
                      ("if (abs(n.x) <= abs(n.y))"
                       ("if (abs(n.x) <= abs(n.z))"
                        (= b (vec3 1 0 0)))
                       ("else"
                        (= b (vec3 0 0 1))))
                      ("else"
                       ("if (abs(n.y) <= abs(n.z))"
                        (= b (vec3 0 1 0)))
                       ("else"
                        (= b (vec3 0 0 1))))
                      (normalize (cross n b)))

                 oriented_matrix
                 ([n] (=vec3 o1 (ortho_vec n))
                      (=vec3 o2 (cross n o1))
                      (mat3 n o1 o2))

                 lonlat
                 ([p] (=float lon "atan(p.x, -p.z) / (2.0 * 3.1415926535897932384626433832795) + 0.5")
                      (=float lat "atan(p.y, length(p.xz)) / 3.1415926535897932384626433832795 + 0.5")
                      (vec2 lon lat))

                 color ([lonlat] (.rgb (texture u_mat lonlat)))

                 elevation ([p] (.r (texture u_ldem (lonlat p))))

                 normal_fn
                 ([horizon p]
                  (=vec3 pl (+ p (* horizon (vec3 0 -1 0) u_resolution)))
                  (=vec3 pr (+ p (* horizon (vec3 0 1 0) u_resolution)))
                  (=vec3 pu (+ p (* horizon (vec3 0 0 -1) u_resolution)))
                  (=vec3 pd (+ p (* horizon (vec3 0 0 1) u_resolution)))
                  (=vec3 u (* horizon (vec3 (- (elevation pr) (elevation pl)) (* "2.0" u_resolution) 0)))
                  (=vec3 v (* horizon (vec3 (- (elevation pd) (elevation pu)) 0 (* "2.0" u_resolution))))
                  (normalize (cross u v)))

                 main
                 ([]
                  (=mat3 horizon (oriented_matrix (normalize vpoint)))
                  (=float phong (+ u_light_ambient (* u_light_diffuse (max "0.0" (dot u_light_pos (normal_fn horizon vpoint))))))
                  (= o_color (vec4 (* (.rgb (color (lonlat vpoint))) phong) "1.0")))}})

(defn init-fn [world game]
  (-> world
      (esse ::moon
            #::assimp{:model-to-load ["assets/models/moon.glb"]
                      :tex-unit-offset 3}
            #::shader{:program-data (shader/create-program game moon-vert moon-frag)})))

(def rules
  (o/ruleset
   {::I-cast-gltf-loading!
    [:what
     [esse-id ::assimp/gltf gltf-json]
     [esse-id ::assimp/bins bins]
     :then
     (let [gltf-spell #?(:clj  []
                         :cljs (assimp-js/gltf-magic esse-id gltf-json (first bins)))]
       (s-> session (o/insert esse-id #::gl.magic{:incantation gltf-spell})))]

    ::the-moon
    [:what
     [esse-id ::assimp/gltf gltf-json]
     ["Sphere" ::vao/vao vao]
     [esse-id ::texture/data texture-data]
     [esse-id ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     [:minusone.learnopengl/light-cube :minusone.rules.transform3d/position light-pos]
     :then
     (println esse-id "all set!")]}))

(defn render-fn [world game]
  (when-let [{:keys [gltf-json vao texture-data program-data] :as esse} (first (o/query-all world ::the-moon))]
    (let [indices         (let [mesh      (some-> gltf-json :meshes first)
                                accessors (some-> gltf-json :accessors)
                                indices   (some-> mesh :primitives first :indices)]
                            (get accessors indices))
          program         (-> program-data :program)
          uni-loc         (-> program-data :uni-locs)
          u_mvp           (get uni-loc 'u_mvp)

          u_light_pos     (get uni-loc 'u_light_pos)
          u_light_ambient (get uni-loc 'u_light_ambient)
          u_light_diffuse (get uni-loc 'u_light_diffuse)
          u_resolution    (get uni-loc 'u_resolution)
          u_mat           (get uni-loc 'u_mat)

          view            (:look-at esse)
          project         (:projection esse)
          [^float lx
           ^float ly
           ^float lz]     (m/normalize (:light-pos esse))
          trans-mat (m-ext/translation-mat 0.0 0.0 0.0)
          rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                  (v/vec3 0.0 1.0 0.0)
                                  (m/radians 0.0)))
          scale-mat (m-ext/scaling-mat 1.0)

          model     (reduce m/* [trans-mat rot-mat scale-mat])

          mvp       (reduce m/* [project view model])
          mvp       (f32-arr (vec mvp))]
      (gl game useProgram program)
      (gl game bindVertexArray vao)
      (gl game uniformMatrix4fv u_mvp false mvp)

      (gl game uniform3f u_light_pos lx ly lz)
      (gl game uniform1f u_light_ambient 0.0)
      (gl game uniform1f u_light_diffuse 1.6)
      (gl game uniform1f u_resolution (/ (* 2.0 Math/PI 1737.4) 1440)) ;; manual radius ldem-width    

      (let [{:keys [tex-unit texture]} texture-data]
        (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
        (gl game bindTexture (gl game TEXTURE_2D) texture)
        (gl game uniform1i u_mat tex-unit))

      (gl game drawElements
          (gl game TRIANGLES)
          (:count indices)
          (:componentType indices)
          0))))

(def system
  {::world/init-fn init-fn
   ::world/render-fn render-fn
   ::world/rules rules})

#?(:cljs
   (#_"playground purposes"
    do
    (defonce canvas (js/document.querySelector "canvas"))
    (defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
    (defonce width (-> canvas .-clientWidth))
    (defonce height (-> canvas .-clientHeight))
    (defonce game {:context gl-context})
    (defn limited-game-loop
      ([loop-fn time-data how-long]
       (if (> how-long 0)
         (js/requestAnimationFrame
          (fn [ts]
            (let [delta (- ts (:total time-data))
                  time-data (assoc time-data :total ts :delta delta)]
              (loop-fn time-data)
              (limited-game-loop loop-fn time-data (- how-long delta)))))
         (println "done"))))))

#?(:clj
   (comment 
     ;; just to remove unused warning in clj side 
     vars->map gl-incantation mat/matrix44)
   :cljs
   (comment
     ;; playground
     
     (do (gl game clearColor 0.02 0.02 0.04 1.0)
         (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

     (assimp-js/then-load-model
      ["assets/models/moon.glb"]
      #_{:clj-kondo/ignore [:inline-def]}
      (fn [{:keys [gltf bins]}]
        (def gltf-json gltf)
        (def result-bin (first bins))))

     (#_"all data (w/o images bc it's too big to print in repl)"
      -> gltf-json
      (update :images (fn [images] (map #(update % :uri (juxt type count)) images))))

     (#_"the texture byte array"
      -> gltf-json :images first :uri assimp-js/data-uri->header+Uint8Array
      ((juxt (comp (fn [data-header] (re-matches #"(.*):(.*);(.*)" data-header)) first)
             (comp type second)
             (comp (fn [arr] (.-length arr)) second))))

     (assimp-js/data-uri->ImageBitmap
      (-> gltf-json :images first :uri)
      (fn [{:keys [bitmap width height]}]
        #_{:clj-kondo/ignore [:inline-def]}
        (def the-texture (texture/texture-incantation game bitmap width height 0))))

     (.then (geotiff/fromUrl "assets/models/ldem_4.tif")
            (fn [tiff]
              (.then (.getImage tiff)
                     (fn [image]
                       (.then (.readRasters image #js {:samples #js [0]})
                              (fn [raster]
                                (let [raster (aget raster 0)
                                      width  (.getWidth image)
                                      height (.getHeight image)
                                      tex-unit 1]
                                  (println "ldem" (type raster) (.-length raster) width height)
                                  (let [texture (gl game createTexture)]
                                    (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
                                    (gl game bindTexture (gl game TEXTURE_2D) texture)

                                    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game LINEAR))
                                    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game LINEAR))
                                    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_WRAP_S) (gl game REPEAT))
                                    (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_WRAP_T) (gl game REPEAT))
                                    (gl game texImage2D (gl game TEXTURE_2D)
                                        #_:mip-level    0
                                        #_:internal-fmt (gl game R32F)
                                        (int width)
                                        (int height)
                                        #_:border       0
                                        #_:src-fmt      (gl game RED)
                                        #_:src-type     (gl game FLOAT)
                                        raster)
                                    (def the-ldem-tex (vars->map texture tex-unit))))))))))

     (def default-gltf-shader
       {:esse-id :DEFAULT-GLTF-SHADER
        :program-data (shader/create-program game moon-vert moon-frag)})

     (-> default-gltf-shader :program-data)

     (def summons
       (gl-incantation game
                       [default-gltf-shader]
                       (assimp-js/gltf-magic :DEFAULT-GLTF-SHADER gltf-json result-bin)))

     (let [indices         (let [mesh      (some-> gltf-json :meshes first)
                                 accessors (some-> gltf-json :accessors)
                                 indices   (some-> mesh :primitives first :indices)]
                             (get accessors indices))
           program         (-> default-gltf-shader :program-data :program)
           uni-loc         (-> default-gltf-shader :program-data :uni-locs)
           u_mvp           (get uni-loc 'u_mvp)

           u_light_pos     (get uni-loc 'u_light_pos)
           u_light_ambient (get uni-loc 'u_light_ambient)
           u_light_diffuse (get uni-loc 'u_light_diffuse)
           u_resolution    (get uni-loc 'u_resolution)
           u_mat           (get uni-loc 'u_mat)
           u_ldem          (get uni-loc 'u_ldem)

           vao             (nth (first summons) 2)

           position        (v/vec3 0.0 0.0 3.0)
           front           (v/vec3 0.0 0.0 -1.0)
           up              (v/vec3 0.0 1.0 0.0)
           view            (mat/look-at position (m/+ position front) up)

           fov             45.0
           aspect          (/ width height)
           project         (mat/perspective fov aspect 0.1 100)
           [^float lx
            ^float ly
            ^float lz]     (m/normalize (v/vec3 -1.0 0.0 -1.0))

           loop-fn         #_{:clj-kondo/ignore [:unused-binding]}
           (fn [{:keys [total]}]
             (let [trans-mat (m-ext/translation-mat 0.0 0.0 0.0)
                   rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                           (v/vec3 0.0 1.0 0.0)
                                           (m/radians (* total 0.18))))
                   scale-mat (m-ext/scaling-mat 1.0)

                   model     (reduce m/* [trans-mat rot-mat scale-mat])

                   mvp       (reduce m/* [project view model])
                   mvp       (f32-arr (vec mvp))]
               (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
               (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
               (gl game viewport 0 0 width height)

               (gl game useProgram program)
               (gl game bindVertexArray vao)
               (gl game uniformMatrix4fv u_mvp false mvp)

               (gl game uniform3f u_light_pos lx ly lz)
               (gl game uniform1f u_light_ambient 0.0)
               (gl game uniform1f u_light_diffuse 1.6)
               (gl game uniform1f u_resolution (/ (* 2.0 Math/PI 1737.4) 1440)) ;; manual radius ldem-width    
               
               (let [{:keys [tex-unit texture]} the-texture]
                 (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
                 (gl game bindTexture (gl game TEXTURE_2D) texture)
                 (gl game uniform1i u_mat tex-unit))

               (let [{:keys [tex-unit texture]} the-ldem-tex]
                 (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
                 (gl game bindTexture (gl game TEXTURE_2D) texture)
                 (gl game uniform1i u_ldem tex-unit))

               (gl game drawElements
                   (gl game TRIANGLES)
                   (:count indices)
                   (:componentType indices)
                   0)))]
       (gl game enable (gl game DEPTH_TEST)) ;; THIS HOLY MOLY, I THOUGHT MY UV IS ALL WRONG!!!!
       (limited-game-loop
        loop-fn
        {:total (js/performance.now)
         :delta 0}
        5000))))