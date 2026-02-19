(ns minusthree.gl.macros
  "gl wrapper macros inspired by play-cljc")

(defmacro lwjgl
  "Wraps org.lwjgl.opengl.GL33, calling a method if the provided symbol starts with a
     lower-case letter, or a static field if it starts with an upper-case letter."
  [_ gl-method & args]
  (let [s (str gl-method)
        ^Character l (nth s 0)
        remaining-letters (subs s 1)]
    (if (Character/isUpperCase l)
      (symbol (str "org.lwjgl.opengl.GL33/GL_" s))
      (cons (symbol (str "org.lwjgl.opengl.GL33/gl" (Character/toUpperCase l) remaining-letters))
            args))))
