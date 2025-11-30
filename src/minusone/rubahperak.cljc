(ns minusone.rubahperak
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?@(:cljs [[minusone.rules.model.assimp-js :as assimp-js]])
   [clojure.string :as str]
   [engine.macros :refer [s-> vars->map]]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.utils :as utils]
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

(def pmx-vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 NORMAL     vec3
                 TEXCOORD_0 vec2}
   :outputs    '{FragPos vec3
                 Normal vec3
                 TexCoords vec2}
   :uniforms   '{u_mvp mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (= gl_Position (* u_mvp (vec4 POSITION "1.0")))
                       (= FragPos POSITION)
                       (= Normal NORMAL)
                       (= TexCoords TEXCOORD_0))}})

(def pmx-frag
  {:precision  "mediump float"
   :inputs     '{FragPos vec3
                 Normal vec3
                 TexCoords vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_mat_diffuse sampler2D}
   :functions
   "
void main() 
{
    vec3 result = texture(u_mat_diffuse, TexCoords).rgb;
    o_color = vec4(result, 1.0);
}"})

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

#?(:cljs
   (assimp-js/then-load-model
    (eduction (map #(str "assets/models/SilverWolf/" %))
              #_["sw.glb"]
              ["银狼.pmx"
               #_#_#_#_#_"外套.png"
                       "头发.png"
                     "脸.png"
                   "衣服.png"
                 "表情.png"])
    #_{:clj-kondo/ignore [:inline-def]}
    (fn [{:keys [gltf bins]}]
      (def gltf-json gltf)
      (def result-bin (first bins)))))

(def gl-array-type assimp-js/gl-array-type)
(def gltf-type->size assimp-js/gltf-type->size)

#?(:clj
   (comment
     ;; just to remove unused warning in clj side 
     vars->map gl-incantation mat/matrix44)
   :cljs
   (comment
     ;; playground

     (do (gl game clearColor 0.2 0.2 0.4 1.0)
         (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

     (-> gltf-json :meshes)

     ((juxt (comp #(get % 2) :nodes)) gltf-json)

     (.-length result-bin)

     (let [byte-offset 0 float-count 16
           start byte-offset
           end   (+ byte-offset (* float-count 4))
           sub   (.subarray result-bin start end)]
       (js/Float32Array. (.-buffer sub) (.-byteOffset sub) float-count))

     (def pmx-gltf-shader
       {:esse-id :PMX-SHADER
        :program-data (shader/create-program game pmx-vert pmx-frag)})

     (-> pmx-gltf-shader :program-data)

     (def gltf-spell
       (assimp-js/gltf-magic gltf-json result-bin
                             {:from-shader :PMX-SHADER
                              :tex-unit-offset 0}))

     (let [get-fn #(nth % 1)
           summons
           (gl-incantation game
                           [pmx-gltf-shader]
                           (-> gltf-spell get-fn))]
       (let [{:minusone.rules.gl.texture/keys [uri-to-load tex-unit]}
             (into {} (comp (filter #(string? (first %))) (filter #(str/starts-with? (first %) "tex")) (map #(drop 1 %)) (map vec)) summons)]
         (println "load tex from img" uri-to-load tex-unit)
         (utils/get-image
          uri-to-load
          (fn [{:keys [data width height]}]
            (println width height)
            #_{:clj-kondo/ignore [:inline-def]}
            (let [the-texture (texture/texture-incantation game data width height tex-unit)
                  indices          (let [mesh      (some-> gltf-json :meshes first)
                                         accessors (some-> gltf-json :accessors)
                                         indices   (some-> mesh :primitives get-fn :indices)]
                                     (get accessors indices))
                  program          (-> pmx-gltf-shader :program-data :program)
                  uni-loc          (-> pmx-gltf-shader :program-data :uni-locs)
                  u_mvp            (get uni-loc 'u_mvp)

                  u_mat_diffuse    (get uni-loc 'u_mat_diffuse)

                  vao              (nth (first summons) 2)

                  position        (v/vec3 0.0 0.0 12.0)
                  front           (v/vec3 0.0 0.0 -1.0)
                  up              (v/vec3 0.0 1.0 0.0)
                  view            (mat/look-at position (m/+ position front) up)

                  fov             45.0
                  aspect          (/ width height)
                  project         (mat/perspective fov aspect 0.1 100)

                  loop-fn          #_{:clj-kondo/ignore [:unused-binding]}
                  (fn [{:keys [total]}]
                    (let [trans-mat (m-ext/translation-mat -1.5 -5.5 0.0)
                          rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                                  (v/vec3 0.0 1.0 0.0)
                                                  (m/radians (* total 0.18))))
                          scale-mat (m-ext/scaling-mat 0.22)

                          model     (reduce m/* [trans-mat rot-mat scale-mat])

                          mvp       (reduce m/* [project view model])
                          mvp       (f32-arr (vec mvp))]
                      (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
                      (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
                      (gl game viewport 0 0 width height)

                      (gl game useProgram program)
                      (gl game bindVertexArray vao)
                      (gl game uniformMatrix4fv u_mvp false mvp)

                      (let [{:keys [tex-unit texture]} the-texture]
                        (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
                        (gl game bindTexture (gl game TEXTURE_2D) texture)
                        (gl game uniform1i u_mat_diffuse tex-unit))

                      (gl game drawElements
                          (gl game TRIANGLES)
                          (:count indices)
                          (:componentType indices)
                          0)))]
              (gl game enable (gl game DEPTH_TEST))
              (limited-game-loop
               loop-fn
               {:total (js/performance.now)
                :delta 0}
               5000))))))))