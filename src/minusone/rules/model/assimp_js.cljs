(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   ["geotiff" :as geotiff]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [engine.macros :refer [vars->map]]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [minusone.rules.gl.magic :as gl.magic :refer [gl-incantation]]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.model.assimp :as assimp]
   [odoyle.rules :as o]
   [play-cljc.macros-js :refer-macros [gl]]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [engine.world :as world]))

(defn data-uri->header+Uint8Array [data-uri]
  (let [[header base64-str] (.split data-uri ",")]
    [header (js/Uint8Array.fromBase64 base64-str)]))

;; following this on loading image to bindTexture with blob
;; https://webglfundamentals.org/webgl/lessons/webgl-qna-how-to-load-images-in-the-background-with-no-jank.html
(defn data-uri->ImageBitmap [data-uri callback]
  (let [[header uint8-arr] (data-uri->header+Uint8Array data-uri)
        data-type          (second (re-matches #".*:(.*);.*" header))
        blob               (js/Blob. #js [uint8-arr] #js {:type data-type})]
    (.then (js/createImageBitmap blob)
           (fn [bitmap]
             (let [width  (.-width bitmap)
                   height (.-height bitmap)]
               (callback bitmap width height)
               (println "blob:" data-type "count" (.-length uint8-arr)))))))

(def gltf-type->size
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(defn gltf-magic [game gltf-json result-bin]
  (let [mesh        (some-> gltf-json :meshes first)
        accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)
        attributes  (some-> mesh :primitives first :attributes)
        indices     (some-> mesh :primitives first :indices)]
    (flatten
     [{:bind-buffer (:name mesh) :buffer-data result-bin :buffer-type (gl game ARRAY_BUFFER)}
      {:bind-vao (:name mesh)}

      (eduction
       (map (fn [[attr-name accessor]]
              (merge {:attr-name attr-name}
                     (get accessors accessor))))
       (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
              (let [bufferView (get bufferViews bufferView)]
                {:point-attr (symbol attr-name)
                 :from-shader :DEFAULT-GLTF-SHADER
                 :attr-size (gltf-type->size type)
                 :attr-type componentType
                 :offset (+ (:byteOffset bufferView) byteOffset)})))
       attributes)

      (let [id-accessor   (get accessors indices)
            id-bufferView (get bufferViews (:bufferView id-accessor))
            id-byteOffset (:byteOffset id-bufferView)
            id-byteLength (:byteLength id-bufferView)]
        {:bind-buffer (str (:name mesh) "IBO")
         :buffer-data (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset))
         :buffer-type (gl game ELEMENT_ARRAY_BUFFER)})

      {:unbind-vao true}])))

;; https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs
;; drop core.async, error msg are not nice there
(defn then-load-model
  "promise to load model and pass the result via .then callback.
   callback will receive {:keys [gltf bins]}"
  [files callback]
  (-> (assimpjs)
      (.then
       (fn [ajs]
         (-> (->> files
                  (map (fn [f] (-> f js/fetch (.then (fn [res] (.arrayBuffer res))))))
                  js/Promise.all)
             (.then
              (fn [arr-bufs]
                (let [ajs-file-list (new (.-FileList ajs))]
                  (dotimes [i (count files)]
                    (.AddFile ^js ajs-file-list (nth files i) (js/Uint8Array. (aget arr-bufs i))))
                  (let [result (.ConvertFileList ^js ajs ajs-file-list "gltf2")
                        gltf   (->> (.GetFile ^js result 0)
                                    (.GetContent)
                                    (.decode (js/TextDecoder.))
                                    (js/JSON.parse)
                                    ((fn [json] (-> json (js->clj :keywordize-keys true)))))
                                     ;; only handle one binary for now, (TODO use .FileCount)
                        bins   [(->> (.GetFile result 1) (.GetContent))]]
                    (callback (vars->map gltf bins)))))))))))

(def rules
  (o/ruleset
   {::load-with-assimpjs
    [:what
     [esse-id ::assimp/model-to-load model-files]]}))

(defn load-models-from-world*
  "load models from world* and fire callback for each models loaded.
   this will retract the ::assimp/model-to-load facts"
  [world*]
  (doseq [model-to-load (o/query-all @world* ::load-with-assimpjs)]
    (let [model-files (:model-files model-to-load)
          esse-id     (:esse-id model-to-load)]
      (swap! world* o/retract esse-id ::assimp/model-to-load)
      (then-load-model
       model-files
       (fn [{:keys [gltf bins]}]
         (println "[assimp-js] loaded" esse-id)
         (swap! world* o/insert esse-id {::assimp/gltf gltf ::assimp/bins bins}))))))

(def system
  {::world/rules rules})

;; REPL playground starts here

(defn limited-game-loop
  ([loop-fn time-data how-long]
   (if (> how-long 0)
     (js/requestAnimationFrame
      (fn [ts]
        (let [delta (- ts (:total time-data))
              time-data (assoc time-data :total ts :delta delta)]
          (loop-fn time-data)
          (limited-game-loop loop-fn time-data (- how-long delta)))))
     (println "done"))))

(defonce canvas (js/document.querySelector "canvas"))
(defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))
(defonce game {:context gl-context})

(comment
  (do (gl game clearColor 0.02 0.02 0.0 1.0)
      (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

  (then-load-model
   (eduction (map #(str "assets/models/" %)) ["moon.glb"])
   #_{:clj-kondo/ignore [:inline-def]}
   (fn [{:keys [gltf bins]}]
     (def gltf-json gltf)
     (def result-bin (first bins))))

  (#_"all data (w/o images bc it's too big to print in repl)"
   -> gltf-json
   (update :images (fn [images] (map #(update % :uri (juxt type count)) images))))

  (#_"the texture byte array"
   -> gltf-json :images first :uri data-uri->header+Uint8Array
   ((juxt (comp (fn [data-header] (re-matches #"(.*):(.*);(.*)" data-header)) first)
          (comp type second)
          (comp (fn [arr] (.-length arr)) second))))

  (data-uri->ImageBitmap
   (-> gltf-json :images first :uri)
   (fn [bitmap-data width height]
     #_{:clj-kondo/ignore [:inline-def]}
     (def the-texture (texture/texture-incantation game bitmap-data width height 0))))

  (go
    (let [tiff   (<p! (geotiff/fromUrl "assets/models/ldem_4.tif"))
          image  (<p! (.getImage tiff))
          raster (<p! (.readRasters image #js {:samples #js [0]}))
          raster (aget raster 0)
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
        (def the-ldem-tex (vars->map texture tex-unit)))))

  (def default-gltf-shader
    (let [vert   {:precision  "mediump float"
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
                                      (= uv TEXCOORD_0))}}
          ;; frag from https://clojurecivitas.github.io/opengl_visualization/main.html
          frag   {:precision  "mediump float"
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
                                 (= o_color (vec4 (* (.rgb (color (lonlat vpoint))) phong) "1.0")))}}]
      {:esse-id :DEFAULT-GLTF-SHADER
       :program-data (shader/create-program game vert frag)}))

  (-> default-gltf-shader :program-data)

  (def summons
    (gl-incantation game
                    [default-gltf-shader]
                    (gltf-magic game gltf-json result-bin)))

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
                                        (m/radians 180.0)))
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
     5000))

  :-)
