(ns minusthree.engine.rendering.par-streamlines
  (:require
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loader :as loader]
   [minusthree.engine.time :as time]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.shader :as shader])
  (:import
   [java.lang.foreign Arena MemoryLayout MemorySegment]
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

(defonce _loadlib
  (loader/load-libs "par_streamlines"))

(def sl-vs
  (str cljgl/version-str
       "
precision highp float;
uniform vec2 resolution;
layout(location=0) in vec2 POSITION;
layout(location=1) in vec4 ANNOTATION;
layout(location=2) in float SPINE_LEN;
out vec4 annotation;
out float spine_length;

void main() {
  vec2 p = 2.0 * POSITION * resolution.xy - 1.0;
  annotation = ANNOTATION;
  spine_length = SPINE_LEN;
  vec2 spine_to_edge = annotation.zw;
  float wave = 0.5 + 0.5 * sin(10.0 * 6.28318 * annotation.x);
  p = p + spine_to_edge * 0.01 * wave;
  gl_Position = vec4(p, 0.0, 1.0);
}"))

(def sl-fs
  (str cljgl/version-str
       "
precision highp float;
const float radius = 15.0;
const float radius2 = radius * radius;
in vec4 annotation;
in float spine_length;
out vec4 o_color;

void main() {
  float dist1 = abs(annotation.x);
  float dist2 = spine_length - dist1;
  float dist = min(dist1, dist2);
  float alpha = 1.0;
  if (dist < radius) {
      float x = dist - radius;
      float y = annotation.y * radius;
      float d2 = x * x + y * y;
      float t = fwidth(d2);
      alpha = 1.0 - 0.99 * smoothstep(radius2 - t, radius2 + t, d2);
  }
  o_color = vec4(0, 0, 0, alpha);
}"))

(defn gl-stuff [positions indices spine-lengths annotations]
  (let [shader  (cljgl/create-program-info-from-source sl-vs sl-fs)
        vao-id  "streamline"
        spell   [{:bind-vao vao-id}
                 {:buffer-data positions :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW :buffer-name ::position-buf}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL45/GL_FLOAT}
                 {:buffer-data annotations :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW :buffer-name ::annotation-buf}
                 {:point-attr :ANNOTATION :use-shader shader :count 4 :component-type GL45/GL_FLOAT}
                 {:buffer-data spine-lengths :buffer-type GL45/GL_ARRAY_BUFFER :usage GL45/GL_DYNAMIC_DRAW :buffer-name ::spine-len-buf}
                 {:point-attr :SPINE_LEN :use-shader shader :count 1 :component-type GL45/GL_FLOAT}
                 {:buffer-data indices :buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        summons (gl-magic/cast-spell spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))
        buffers (-> summons ::gl-magic/data ::shader/buffer)]

    {:vao vao :program-info shader ::buffers buffers}))

(defn gl-update-buffers [mesh|| {:keys [::position-buf ::annotation-buf ::spine-len-buf]}]
  (let [positions||    (.asSlice (parsl_mesh/positions mesh||) 0
                                 (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_position/layout)))
        annotations||  (.asSlice (parsl_mesh/annotations mesh||) 0
                                 (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_annotation/layout)))
        mesh-sp-lens|| (.asSlice (parsl_mesh/spine_lengths mesh||) 0
                                 (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) parsl/C_FLOAT))]
    (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER position-buf)
    (GL45/glBufferSubData GL45/GL_ARRAY_BUFFER 0 (.asByteBuffer positions||))
    (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER annotation-buf)
    (GL45/glBufferSubData GL45/GL_ARRAY_BUFFER 0 (.asByteBuffer annotations||))
    (GL45/glBindBuffer GL45/GL_ARRAY_BUFFER spine-len-buf)
    (GL45/glBufferSubData GL45/GL_ARRAY_BUFFER 0 (.asByteBuffer mesh-sp-lens||))))

(defn vert [x y] {:x x :y y})

(defn make-vertices|| ^MemorySegment [^Arena arena verts]
  (let [vertices|| (parsl_position/allocateArray (count verts) arena)]
    (doseq [[i {:keys [x y]}] (map-indexed vector verts)]
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
                    (parsl_config/thickness 4.0)
                    (parsl_config/flags (bit-or (parsl/PARSL_FLAG_ANNOTATIONS)
                                                (parsl/PARSL_FLAG_SPINE_LENGTHS)))
                    (parsl_config/u_mode (parsl/PAR_U_MODE_DISTANCE)))
        context|| (parsl/parsl_create_context config||)]
    (parsl_context/reinterpret context|| arena parsl/parsl_destroy_context)))

(defn zero-if-small [v]
  (if (< (abs v) 1e-5) 0 v))

(defn make-circle [num-of-vert x y r]
  (let [two-pi (* 2 Math/PI)
        angle-shift (/ two-pi num-of-vert)]
    (->> (range (inc num-of-vert))
         (map (fn [i] (let [theta (* i angle-shift)]
                        (vert (zero-if-small (+ x (* r (Math/cos theta))))
                              (zero-if-small (+ y (* r (Math/sin theta)))))))))))

(defn init [{:keys [::arena/game-arena] :as game}]
  (let [app-width       600
        app-height      600
        resolution      (float-array [(/ 1.0 app-width) (/ 1.0 app-height)])
        circle-num       42
        verts           (flatten [(make-circle circle-num 300.0 300.0 250.0)
                                  [(vert 175 175) (vert 300 300) (vert 250 50)]])
        num-vert        (count verts)
        spine-lengths   [(inc circle-num) 3]
        verts||         (make-vertices|| game-arena verts)
        num-spines      (count spine-lengths)
        spine-lengths|| (make-spine-lengths|| game-arena spine-lengths)
        spine-list||    (make-spine-list|| game-arena num-vert verts|| num-spines spine-lengths|| false)
        context||       (parsl-context|| game-arena)
        mesh||          (parsl/parsl_mesh_from_lines context|| spine-list||)
        positions||     (.asSlice (parsl_mesh/positions mesh||) 0
                                  (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_position/layout)))
        num-tri         (* 3 (parsl_mesh/num_triangles mesh||))
        tri-indices||   (.asSlice (parsl_mesh/triangle_indices mesh||) 0
                                  (MemoryLayout/sequenceLayout num-tri parsl/C_INT))
        annotations||   (.asSlice (parsl_mesh/annotations mesh||) 0
                                  (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) (parsl_annotation/layout)))
        mesh-sp-lens||  (.asSlice (parsl_mesh/spine_lengths mesh||) 0
                                  (MemoryLayout/sequenceLayout (parsl_mesh/num_vertices mesh||) parsl/C_FLOAT))
        gl-data         (gl-stuff (.asByteBuffer positions||)
                                  (.asByteBuffer tri-indices||)
                                  (.asByteBuffer mesh-sp-lens||)
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
  (let [t-factor (* Math/PI tt 5e-6)]
      (doto ^MemorySegment verts||
        (-> (parsl_position/asSlice 0) (parsl_position/x (+ 250.0 (* 50 (Math/cos t-factor)))))
        #_#_#_#_(-> (parsl_position/asSlice 3) (parsl_position/x (+ 400 (* 50 (Math/cos t-factor)))))
        (-> (parsl_position/asSlice 3) (parsl_position/y (+ 150 (* 50 (Math/sin t-factor)))))
        (-> (parsl_position/asSlice 4) (parsl_position/x (- 400 (* 50 (Math/cos t-factor)))))
        (-> (parsl_position/asSlice 4) (parsl_position/y (- 150 (* 50 (Math/sin t-factor)))))))
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
