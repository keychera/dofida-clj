(ns minusthree.engine.rendering.par-streamlines
  (:require
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.constants :refer [GL_ARRAY_BUFFER GL_DYNAMIC_DRAW
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT GL_LINES
                                  GL_TRIANGLES GL_UNSIGNED_SHORT]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}])
  (:import
   [java.nio Buffer]
   [org.lwjgl.system MemoryUtil NativeResource Struct]
   [org.lwjgl.util.par
    ParSLConfig
    ParSLPosition
    ParSLSpineList
    ParStreamlines]))

(set! *warn-on-reflection* true)
;; https://prideout.net/blog/par_streamlines/
;; https://blog.lwjgl.org/memory-management-in-lwjgl-3/
;; is MemoryStack similar to "regular" initialization mentioned here? https://stackoverflow.com/q/60029592/8812880

(defn free-struct!? [{:keys [^Struct struct!? ptrs!?]}]
  (.free struct!?)
  (doseq [ptr!? ptrs!?]
    (condp instance? ptr!?
      Buffer (MemoryUtil/memFree ^Buffer ptr!?)
      NativeResource (.free ^NativeResource ptr!?))))

(defn create-spine-list!? [verts spine-lengths]
  (let [num-v           (count verts)
        num-spine-len   (count spine-lengths)
        positions!?     (ParSLPosition/calloc num-v)
        spine-lengths!? (MemoryUtil/memAllocShort num-spine-len)
        spine-list!?    (ParSLSpineList/calloc)]

    (dotimes [i num-v]
      (let [[x y] (nth verts i)]
        (-> ^ParSLPosition (.get positions!? i) (.x (float x)) (.y (float y)))))

    (dotimes [i num-spine-len]
      (let [len (short (nth spine-lengths i))]
        (.put spine-lengths!? i len)))

    (doto spine-list!?
      (.vertices positions!?)
      (.spine_lengths spine-lengths!?))

    {:struct!? spine-list!? :ptrs!? [spine-lengths!? positions!?]}))

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
  (let [ctx     nil
        shader  (cljgl/create-program-info-from-source ctx sl-vs sl-fs)
        vao-id  "streamline"
        spell   [{:bind-vao vao-id}
                 {:buffer-data positions :buffer-type GL_ARRAY_BUFFER :buffer-name :position :usage GL_DYNAMIC_DRAW}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL_FLOAT}
                 {:buffer-data indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        ;; cast-spell kinda have a lil complected api with esse-id
        summons (gl-magic/cast-spell ctx nil spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))]
    {:vao vao :program-info shader}))

(defn init [game]
  (let [config!?     (doto (ParSLConfig/calloc)
                       (.thickness 3.0))
        *par-ctx     (ParStreamlines/parsl_create_context config!?)
        spine-list!? (create-spine-list!?
                      [[0.0 0.0] [2.0 1.0] [4.0 0.0]]
                      [3])
        *mesh        (ParStreamlines/parsl_mesh_from_lines *par-ctx (:struct!? spine-list!?))
        pos-buffer   (-> *mesh .positions .address (MemoryUtil/memFloatBufferSafe (* 2 (.num_vertices *mesh))))
        idx-buffer   (.triangle_indices *mesh (* (.num_triangles *mesh) 3))
        gl-data      (gl-stuff pos-buffer idx-buffer)]
    (println (.num_vertices *mesh) (.num_triangles *mesh))
    (assoc game
           ::spine-list!? spine-list!?
           ::ptrs!? [config!?]
           ::*par-ctx *par-ctx
           ::*mesh *mesh
           :vao (:vao gl-data)
           :program-info (:program-info gl-data))))

(defn render [{::keys [*mesh] :keys [vao program-info]}]
  (let [ctx nil]
    (gl ctx useProgram (:program program-info))
    (gl ctx bindVertexArray vao)
    (let [index-count (* (.num_triangles *mesh) 3)]
      (gl ctx drawElements GL_TRIANGLES index-count GL_UNSIGNED_SHORT 0))))

(defn destroy [{::keys [*par-ctx spine-list!? ptrs!?]}]
  (free-struct!? spine-list!?)
  (doseq [ptr!? ptrs!?]
    (.free ^NativeResource ptr!?))
  (ParStreamlines/parsl_destroy_context *par-ctx))

(comment
  GL_TRIANGLES GL_LINES

  (require '[clojure.java.javadoc :refer [add-remote-javadoc javadoc]])
  (add-remote-javadoc "org.lwjgl." "https://javadoc.lwjgl.org/")
  (javadoc ParSLConfig)

  :-)
