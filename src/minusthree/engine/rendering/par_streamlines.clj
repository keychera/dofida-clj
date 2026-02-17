(ns minusthree.engine.rendering.par-streamlines
  (:require
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_ELEMENT_ARRAY_BUFFER
                                  GL_FLOAT GL_TRIANGLES GL_UNSIGNED_SHORT]]
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}])
  (:import
   [java.nio Buffer]
   [org.lwjgl.system MemoryStack MemoryUtil]
   [org.lwjgl.util.par
    ParSLConfig
    ParSLPosition
    ParSLSpineList
    ParStreamlines]))

(set! *warn-on-reflection* true)
;; https://prideout.net/blog/par_streamlines/
;; https://blog.lwjgl.org/memory-management-in-lwjgl-3/
;; is MemoryStack similar to "regular" initialization mentioned here? https://stackoverflow.com/q/60029592/8812880

(defn free-struct!? [{:keys [^Buffer struct!? ptrs!?]}]
  (org.lwjgl.system.MemoryUtil/memFree struct!?)
  (doseq [^Buffer ptr!? ptrs!?]
    (org.lwjgl.system.MemoryUtil/memFree ptr!?)))

(defn create-spine-list!? [verts spine-lengths]
  (let [num-v           (count verts)
        num-spine-len   (count spine-lengths)
        verts!?         (ParSLPosition/malloc num-v)
        spine-lengths!? (MemoryUtil/memAllocShort num-spine-len)
        spine-list!?    (ParSLSpineList/malloc)]

    (dotimes [i num-v]
      (let [[x y] (nth verts i)]
        (doto ^ParSLPosition (.get verts!? i) (.x (float x)) (.y (float y)))))

    (dotimes [i num-spine-len]
      (let [len (short (nth spine-lengths i))]
        (.put spine-lengths!? i len)))

    (doto spine-list!?
      (.vertices verts!?)
      (.spine_lengths spine-lengths!?))

    {:struct!? spine-list!? :ptrs!? [spine-lengths!? verts!?]}))

(def sl-vs
  (str cljgl/version-str
       "
precision mediump float;
in vec2 POSITION;

void main() {
  gl_Position = vec4(POSITION.x, 0.5, 0.0, 1.0);
}"))

(def sl-fs
  (str cljgl/version-str
       "
precision mediump float;
out vec4 o_color;

void main() {
  o_color = vec4(0.9, 0.2, 0.2, 0.7);
}"))

(defn gl-stuff [positions indices]
  (let [ctx     nil
        shader  (cljgl/create-program-info-from-source ctx sl-vs sl-fs)
        vao-id  "streamline"
        spell   [{:bind-vao vao-id}
                 {:buffer-data positions :buffer-type GL_ARRAY_BUFFER :buffer-name :position}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL_FLOAT}
                 {:buffer-data indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        ;; cast-spell kinda have a lil complected api with esse-id
        summons (gl-magic/cast-spell ctx nil spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))]
    {:vao vao :program-info shader}))

(defn init [game]
  (with-open [stack (MemoryStack/stackPush)]
    (let [config       (doto (ParSLConfig/malloc stack)
                         (.thickness 15.0))
          *par-ctx     (ParStreamlines/parsl_create_context config)
          spine-list!? (create-spine-list!?
                        [[50 150] [200 100] [550 200]
                         [400 200] [400 100]]
                        [3 2])
          *mesh        (ParStreamlines/parsl_mesh_from_lines *par-ctx (:struct!? spine-list!?))
          pos-buffer   (-> *mesh .positions .address (MemoryUtil/memFloatBuffer (* (.num_vertices *mesh) 2)))
          idx-buffer   (.triangle_indices *mesh (* (.num_triangles *mesh) 3))
          gl-data      (gl-stuff pos-buffer idx-buffer)]
      (assoc game
             ::spine-list!? spine-list!?
             ::*par-ctx *par-ctx
             ::*mesh *mesh
             :vao (:vao gl-data)
             :program-info (:program-info gl-data)))))

(defn render [{::keys [*mesh] :keys [vao program-info]}]
  (let [ctx nil]
    (gl ctx useProgram (:program program-info))
    (gl ctx bindVertexArray vao)
    (let [index-count (* (.num_triangles *mesh) 3)]
      (gl ctx drawElements GL_TRIANGLES index-count GL_UNSIGNED_SHORT 0))))

(defn destroy [{::keys [*par-ctx spine-list!?]}]
  (free-struct!? spine-list!?)
  (ParStreamlines/parsl_destroy_context *par-ctx))

(comment
  (require '[clojure.java.javadoc :refer [add-remote-javadoc javadoc]])
  (add-remote-javadoc "org.lwjgl." "https://javadoc.lwjgl.org/")
  (javadoc ParSLConfig)

  :-)
