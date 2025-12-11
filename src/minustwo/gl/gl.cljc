(ns minustwo.gl.gl
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::loaded? #{:pending :loading true})

;; Primitive types
(def ^:const GL_TRIANGLES 4)

;; Data types
(def ^:const GL_UNSIGNED_BYTE 5121)
(def ^:const GL_UNSIGNED_SHORT 5123)
(def ^:const GL_UNSIGNED_INT 5125)
(def ^:const GL_FLOAT 5126)

;; Buffer types
(def ^:const GL_STATIC_DRAW 35044)
(def ^:const GL_ARRAY_BUFFER 34962)
(def ^:const GL_ELEMENT_ARRAY_BUFFER 34963)

;; Texture types and units
(def ^:const GL_TEXTURE_2D 3553)
(def ^:const GL_TEXTURE0 33984)

;; Render states
(def ^:const GL_DEPTH_TEST 2929)
(def ^:const GL_BLEND 3042)

;; Blend functions
(def ^:const GL_SRC_ALPHA 770)
(def ^:const GL_ONE_MINUS_SRC_ALPHA 771)
(def ^:const GL_ZERO 0)
(def ^:const GL_ONE 1)

;; Buffer bits
(def ^:const GL_COLOR_BUFFER_BIT 16384)
(def ^:const GL_DEPTH_BUFFER_BIT 256)

;; Framebuffer and texture formats
(def ^:const GL_FRAMEBUFFER 36160)
(def ^:const GL_FRAMEBUFFER_COMPLETE 36053)
(def ^:const GL_COLOR_ATTACHMENT0 36064)
(def ^:const GL_RGBA 6408)

;; Texture filtering
(def ^:const GL_TEXTURE_MAG_FILTER 10240)
(def ^:const GL_TEXTURE_MIN_FILTER 10241)
(def ^:const GL_NEAREST 9728)
(def ^:const GL_LINEAR 9729)

;; Texture limits
(def ^:const GL_MAX_TEXTURE_IMAGE_UNITS 34930)
(def ^:const GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS 35660)
(def ^:const GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS 35661)

;; gl macros from play-cljc
#?(:clj
   (defmacro lwjgl
     "Wraps org.lwjgl.opengl.GL33, calling a method if the provided symbol starts with a
      lower-case letter, or a static field if it starts with an upper-case letter."
     [aname & args]
     (let [s (str aname)
           ^Character l (nth s 0)
           remaining-letters (subs s 1)]
       (if (Character/isUpperCase l)
         (symbol (str "org.lwjgl.opengl.GL33/GL_" s))
         (cons (symbol (str "org.lwjgl.opengl.GL33/gl" (Character/toUpperCase l) remaining-letters))
               args)))))

#?(:cljs
   (defmacro webgl
     "Wraps the WebGL2RenderingContext object, calling a method if the provided symbol starts with a
     lower-case letter, or a static field if it starts with an upper-case letter."
     [context aname & args]
     (let [s (str aname)
           l (.charAt s 0)]
       (if (= l (.toUpperCase l))
         `(aget ~context ~s)
         (concat [(symbol (str "." s)) ~context] args)))))

#_{:clj-kondo/ignore [:unused-binding]}
(defmacro gl
  [context aname & args]
  #?(:clj `(lwjgl ~aname ~@args)
     :cljs `(webgl ~context ~aname ~@args)))

