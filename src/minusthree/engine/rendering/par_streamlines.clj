(ns minusthree.engine.rendering.par-streamlines
  (:require
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic])
  (:import
   [java.lang.foreign Arena MemoryLayout MemorySegment]
   [org.lwjgl.opengl GL45]
   [par
    par_streamlines_c
    par_streamlines_c$shared
    parsl_config
    parsl_context
    parsl_mesh
    parsl_position
    parsl_spine_list]))

(defonce _loadlib
  (System/load "C:/Users/Kevin/Documents/projects/dofida-clj/c/o/par/par_streamlines.dll"))

(set! *warn-on-reflection* true)
;; https://prideout.net/blog/par_streamlines/

(def sl-vs
  (str cljgl/version-str
       "
precision mediump float;
in vec2 POSITION;

void main() {
  gl_Position = vec4(POSITION, 0.0, 1.0);
}"))

(def sl-fs
  (str cljgl/version-str
       "
precision mediump float;
out vec4 o_color;

void main() {
  o_color = vec4(0.9, 0.2, 0.9, 0.7);
}"))

(defn gl-stuff [positions indices]
  (let [shader  (cljgl/create-program-info-from-source sl-vs sl-fs)
        vao-id  "streamline"
        spell   [{:bind-vao vao-id}
                 {:buffer-data positions :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL45/GL_FLOAT}
                 {:buffer-data indices :buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        ;; cast-spell kinda have a lil complected api with esse-id
        summons (gl-magic/cast-spell nil spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))]

    {:vao vao :program-info shader}))

(defn make-vertices|| ^MemorySegment [^Arena arena verts]
  (let [vertices|| (parsl_position/allocateArray (count verts) arena)]
    (doseq [[i [x y]] (map-indexed vector verts)]
      (let [vert|| (parsl_position/asSlice vertices|| i)]
        (parsl_position/x vert|| x)
        (parsl_position/y vert|| y)))
    vertices||))

(defn make-spine-lengths|| ^MemorySegment [^Arena arena ^shorts lengths]
  (.allocateFrom arena par_streamlines_c$shared/C_SHORT (short-array lengths)))

(defn make-spine-list|| ^MemorySegment [^Arena arena verts spine-lengths]
  (let [num-vert        (int (count verts))
        num-spines      (short (count spine-lengths))
        spine-list||    (parsl_spine_list/allocate arena)
        verts||         (make-vertices|| arena verts)
        spine-lengths|| (make-spine-lengths|| arena spine-lengths)]
    (doto spine-list||
      (parsl_spine_list/num_vertices num-vert)
      (parsl_spine_list/num_spines num-spines)
      (parsl_spine_list/vertices verts||)
      (parsl_spine_list/spine_lengths spine-lengths||))))

(defn parsl-context|| ^MemorySegment [^Arena arena]
  (let [config||  (doto (parsl_config/allocate arena)
                    (parsl_config/thickness 0.2))
        context|| (par_streamlines_c/parsl_create_context config||)]
    (parsl_context/reinterpret context|| arena par_streamlines_c/parsl_destroy_context)))

(defn init [{::arena/keys [game-arena] :as game}]
  (let [verts         [[0.0 0.0] [0.5 0.3] [1.0 0.0]]
        spine-length  [3]
        spine-list||  (make-spine-list|| game-arena verts spine-length)
        context||     (parsl-context|| game-arena)
        mesh||        (par_streamlines_c/parsl_mesh_from_lines context|| spine-list||)
        positions||   (.asSlice (parsl_mesh/positions mesh||) 0
                                (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_position/layout)))
        num-tri       (* 3 (parsl_mesh/num_triangles mesh||))
        tri-indices|| (.asSlice (parsl_mesh/triangle_indices mesh||) 0
                                (MemoryLayout/sequenceLayout num-tri par_streamlines_c$shared/C_INT))
        ;; positions  (float-array [-0.5 -0.5,  0.5 -0.5,  0.0  0.5])
        ;; tri-idxs   (short-array [0 1 2])
        gl-data       (gl-stuff (.asByteBuffer positions||) (.asByteBuffer tri-indices||))]
    ;; (println [(parsl_mesh/num_vertices mesh||) (parsl_mesh/num_triangles mesh||)])
    ;; (println [(type (.asIntBuffer (.asByteBuffer tri-indices||)))])
    ;; (println (vec (.toArray tri-indices|| par_streamlines_c$shared/C_INT)))
    ;; (println (vec (.toArray positions|| par_streamlines_c$shared/C_FLOAT)))
    (assoc game
           :num-tri num-tri
           :vao (:vao gl-data)
           :program (-> gl-data :program-info :program))))

(defn render [{:keys [vao :program num-tri]}]
  (GL45/glUseProgram program)
  (GL45/glBindVertexArray vao)
  (GL45/glDrawElements GL45/GL_TRIANGLES num-tri GL45/GL_UNSIGNED_INT 0))

(defn destroy [{::keys []}])

(comment
  (require '[clojure.java.javadoc :refer [add-remote-javadoc javadoc]])
  (add-remote-javadoc "org.lwjgl." "https://javadoc.lwjgl.org/")
  (javadoc Arena)

  (with-open [arena (Arena/ofConfined)]
    (let [spine-list|| (make-spine-list|| arena [[0.0 0.0] [2.0 1.0] [4.0 0.0]] [3])
          pos-arr||    (parsl_spine_list/vertices spine-list||)
          pos||        (parsl_position/asSlice pos-arr|| 1)]
      [(parsl_position/x pos||)
       (parsl_position/y pos||)]))

  :-)
