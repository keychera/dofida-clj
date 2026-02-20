(ns minusthree.engine.rendering.par-streamlines
  (:require
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic])
  (:import
   [java.lang.foreign Arena]
   [org.lwjgl.opengl GL45]))

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
                 {:buffer-data positions :buffer-type GL45/GL_ARRAY_BUFFER :buffer-name :position :usage GL45/GL_DYNAMIC_DRAW}
                 {:point-attr :POSITION :use-shader shader :count 2 :component-type GL45/GL_FLOAT}
                 {:buffer-data indices :buffer-type GL45/GL_ELEMENT_ARRAY_BUFFER}
                 {:unbind-vao true}]
        ;; cast-spell kinda have a lil complected api with esse-id
        summons (gl-magic/cast-spell nil spell)
        vao     (-> summons ::gl-magic/data ::gl-magic/vao (get vao-id))]
    {:vao vao :program-info shader}))

(defn init [game]
  (let [pos-arr (float-array [-0.5 -0.5,  0.5 -0.5,  0.0  0.5])
        idx-arr (short-array [0 1 2])
        gl-data (gl-stuff pos-arr idx-arr)]
    (assoc game
           :vao (:vao gl-data)
           :program-info (:program-info gl-data))))

(defn render [{:keys [vao program-info]}]
  (GL45/glUseProgram (:program program-info))
  (GL45/glBindVertexArray vao)
  (GL45/glDrawElements GL45/GL_TRIANGLES 6 GL45/GL_UNSIGNED_SHORT 0))

(defn destroy [{::keys []}])

(comment
  (require '[clojure.java.javadoc :refer [add-remote-javadoc javadoc]])
  (add-remote-javadoc "org.lwjgl." "https://javadoc.lwjgl.org/")
  (javadoc Arena)

  :-)
