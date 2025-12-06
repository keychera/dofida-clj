(ns minusone.moon
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gl.gl :refer [GL_TEXTURE0 GL_TEXTURE_2D GL_TRIANGLES]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.model.assimp :as assimp]
   [minusone.rules.projection :as projection]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [minusone.rules.gl.vao :as vao]))

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
      (esse ::moon-shader
            #::shader{:program-data (shader/create-program game moon-vert moon-frag)})
      (esse ::moon
            #::assimp{:model-to-load (into [] (map #(str "assets/models/" %)) ["moon.gltf" "moon.bin"])
                      :tex-unit-offset 2}
            #::shader{:use ::moon-shader})))

(def rules
  (o/ruleset
   {::the-moon
    [:what
     [::moon ::assimp/gltf gltf-json]
     [::moon ::gltf/primitives primitives]
     [::moon-shader ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     [:minusone.learnopengl/light-cube :minusone.rules.transform3d/position light-pos]
     :then
     (println ::moon "all set!")]}))

(defn render-fn [world game]
  (when-let [{:keys [primitives] :as esse} (first (o/query-all world ::the-moon))]
    (let [prim (first primitives)
          vao  (get @vao/db* (:vao-name prim))
          tex  (get @texture/db* (:tex-name prim))]
      (when (and vao tex)
        (let [gltf-json       (:gltf-json esse)
              program-data    (:program-data esse)
              accessors       (:accessors gltf-json)
              indices         (get accessors (:indices prim))
              program         (:program program-data)
              uni-loc         (:uni-locs program-data)
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
              trans-mat (m-ext/translation-mat 0.0 1.6 0.0)
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

          (let [{:keys [tex-unit texture]} tex]
            (gl game activeTexture (+ GL_TEXTURE0 tex-unit))
            (gl game bindTexture GL_TEXTURE_2D texture)
            (gl game uniform1i u_mat tex-unit))

          (gl game drawElements GL_TRIANGLES (:count indices) (:componentType indices) 0))))))

(def system
  {::world/init-fn init-fn
   ::world/render-fn render-fn
   ::world/rules rules})
