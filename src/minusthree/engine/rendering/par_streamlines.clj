(ns minusthree.engine.rendering.par-streamlines
  (:require
   [clojure.java.io :as io]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.time :as time]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader])
  (:import
   [java.lang.foreign Arena MemoryLayout MemorySegment]
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [org.lwjgl.opengl GL45]
   [par
    parsl
    parsl_annotation
    parsl_config
    parsl_context
    parsl_mesh
    parsl_position
    parsl_spine_list]))

(set! *warn-on-reflection* true)
;; https://prideout.net/blog/par_streamlines/

(defn load-libs [libname]
  (let [obj      (str libname ".dll")
        path     (str "public/libs/" obj)
        o-res    (io/resource path)
        temp-dir (Files/createTempDirectory "dofidalibs-" (into-array FileAttribute []))
        obj-path (.resolve temp-dir obj)
        obj-file (.toFile obj-path)]
    (println "loading" obj "...")
    (with-open [in  (io/input-stream o-res)
                out (io/output-stream obj-file)]
      (io/copy in out))
    (.deleteOnExit obj-file)
    (System/load (str (.toAbsolutePath obj-path)))))

(defonce _loadlib
  (load-libs "par_streamlines"))

(def sl-vs
  (str cljgl/version-str
       "
precision mediump float;
uniform vec2 resolution;
layout(location=0) in vec2 POSITION;
layout(location=1) in vec4 ANNOTATIONS;
out vec4 annotations;

void main() {
  vec2 p = 2.0 * POSITION * resolution.xy - 1.0;
  gl_Position = vec4(p, 0.0, 1.0);
  annotations = ANNOTATIONS;
}"))

(def sl-fs
  (str cljgl/version-str
       "
precision mediump float;
in vec4 annotations;
out vec4 o_color;

void main() {
  float t = annotations.x;
  vec3 color = mix(vec3(0.0, 0.0, 0.8), vec3(0.0, 0.8, 0.0), t);
  o_color = vec4(color, 1.0);
}"))

(defn gl-stuff [positions indices annotations]
  (let [shader  (cljgl/create-program-info-from-source sl-vs sl-fs)
        vao-id  "streamline"
        spell   [{:bind-vao vao-id}
                 {:buffer-data positions :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW :buffer-name ::position-buf}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL45/GL_FLOAT}
                 {:buffer-data annotations :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW :buffer-name ::annotation-buf}
                 {:point-attr :ANNOTATIONS :use-shader shader :count 4 :component-type GL45/GL_FLOAT}
                 {:buffer-data indices :buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        summons (gl-magic/cast-spell spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))
        buffers (-> summons ::gl-magic/data ::shader/buffer)]

    {:vao vao :program-info shader ::buffers buffers}))

(defn gl-update-buffers [mesh|| {:keys [::position-buf]}]
  (let [positions|| (.asSlice (parsl_mesh/positions mesh||) 0
                              (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_position/layout)))]
    (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER position-buf)
    (GL45/glBufferSubData GL45/GL_ARRAY_BUFFER 0 (.asByteBuffer positions||))))

(defn make-vertices|| ^MemorySegment [^Arena arena verts]
  (let [vertices|| (parsl_position/allocateArray (count verts) arena)]
    (doseq [[i [x y]] (map-indexed vector verts)]
      (let [vert|| (parsl_position/asSlice vertices|| i)]
        (parsl_position/x vert|| (float x))
        (parsl_position/y vert|| (float y))))
    vertices||))

(defn make-spine-lengths|| ^MemorySegment [^Arena arena ^shorts lengths]
  (.allocateFrom arena parsl/C_SHORT (short-array lengths)))

(defn make-spine-list|| ^MemorySegment
  ([^Arena arena num-vert verts|| num-spines spine-lengths||]
   (make-spine-list|| arena num-vert verts|| num-spines spine-lengths|| false))
  ([^Arena arena num-vert verts|| num-spines spine-lengths|| closed?]
   (doto (parsl_spine_list/allocate arena)
     (parsl_spine_list/num_vertices num-vert)
     (parsl_spine_list/num_spines num-spines)
     (parsl_spine_list/vertices verts||)
     (parsl_spine_list/spine_lengths spine-lengths||)
     (parsl_spine_list/closed closed?))))

(defn parsl-context|| ^MemorySegment [^Arena arena]
  (let [config||  (doto (parsl_config/allocate arena)
                    (parsl_config/thickness 15.0)
                    (parsl_config/flags (parsl/PARSL_FLAG_ANNOTATIONS)))
        context|| (parsl/parsl_create_context config||)]
    (parsl_context/reinterpret context|| arena parsl/parsl_destroy_context)))

(defn init [{:keys [::arena/game-arena] :as game}]
  (let [app-width       600
        app-height      300
        resolution      (float-array [(/ 1.0 app-width) (/ 1.0 app-height)])
        verts           [[50 150] [200 100] [550 200]
                         [400 200] [400 100]]
        spine-lengths   [3 2]
        num-vert        (count verts)
        verts||         (make-vertices|| game-arena verts)
        num-spines      (count spine-lengths)
        spine-lengths|| (make-spine-lengths|| game-arena spine-lengths)
        spine-list||    (make-spine-list|| game-arena num-vert verts|| num-spines spine-lengths||)
        context||       (parsl-context|| game-arena)
        mesh||          (parsl/parsl_mesh_from_lines context|| spine-list||)
        positions||     (.asSlice (parsl_mesh/positions mesh||) 0
                                  (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_position/layout)))
        num-tri         (* 3 (parsl_mesh/num_triangles mesh||))
        tri-indices||   (.asSlice (parsl_mesh/triangle_indices mesh||) 0
                                  (MemoryLayout/sequenceLayout num-tri parsl/C_INT))
        annotations||   (.asSlice (parsl_mesh/annotations mesh||) 0
                                  (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_annotation/layout)))
        gl-data         (gl-stuff (.asByteBuffer positions||)
                                  (.asByteBuffer tri-indices||)
                                  (.asByteBuffer annotations||))]
    (assoc game
           :verts|| verts||
           :context|| context||
           :spine-list|| spine-list||
           :vao (:vao gl-data)
           :program-info (:program-info gl-data)
           :num-tri num-tri
           :resolution resolution
           ::buffers (::buffers gl-data))))

(defn render [{:keys [vao program-info num-tri resolution
                      verts|| context|| spine-list|| ::buffers]
               tt ::time/total}]
  (let [t-factor (* Math/PI tt 1e-3)]
    (doto ^MemorySegment verts||
      (-> (parsl_position/asSlice 1) (parsl_position/y (+ 150 (* 100 (Math/sin t-factor)))))
      (-> (parsl_position/asSlice 3) (parsl_position/x (+ 400 (* 100 (Math/cos t-factor)))))
      (-> (parsl_position/asSlice 3) (parsl_position/y (+ 150 (* 100 (Math/sin t-factor)))))
      (-> (parsl_position/asSlice 4) (parsl_position/x (- 400 (* 100 (Math/cos t-factor)))))
      (-> (parsl_position/asSlice 4) (parsl_position/y (- 150 (* 100 (Math/sin t-factor)))))))
  (let [mesh|| (parsl/parsl_mesh_from_lines context|| spine-list||)]
    (GL45/glUseProgram (:program program-info))
    (gl-update-buffers mesh|| buffers)
    (cljgl/set-uniform program-info :resolution resolution)
    (GL45/glBindVertexArray vao)
    (GL45/glDrawElements GL45/GL_TRIANGLES num-tri GL45/GL_UNSIGNED_INT 0)))

(defn destroy [{::keys []}])

(comment
  (require '[clojure.java.javadoc :refer [add-remote-javadoc javadoc]])
  (add-remote-javadoc "org.lwjgl." "https://javadoc.lwjgl.org/")
  (javadoc Arena)

  :-)
