(ns minustwo.gl.constants)

;; https://javagl.github.io/GLConstantsTranslator/GLConstantsTranslator.html

;; Primitive types
(def ^:const GL_TRUE 1)
(def ^:const GL_TRIANGLES 4)

;; Shader
(def ^:const GL_FRAGMENT_SHADER 35632)
(def ^:const GL_VERTEX_SHADER 35633)
(def ^:const GL_COMPILE_STATUS 35713)
(def ^:const GL_LINK_STATUS 35714)


;; Data types
(def ^:const GL_UNSIGNED_BYTE 5121)
(def ^:const GL_UNSIGNED_SHORT 5123)
(def ^:const GL_UNSIGNED_INT 5125)
(def ^:const GL_FLOAT 5126)

;; Buffer types
(def ^:const GL_STATIC_DRAW 35044)
(def ^:const GL_DYNAMIC_DRAW 35048)
(def ^:const GL_ARRAY_BUFFER 34962)
(def ^:const GL_ELEMENT_ARRAY_BUFFER 34963)
(def ^:const GL_UNIFORM_BUFFER 35345)

;; Texture types and units
(def ^:const GL_TEXTURE_2D 3553)
(def ^:const GL_TEXTURE0 33984)

;; Render states
(def ^:const GL_DEPTH_TEST 2929)
(def ^:const GL_BLEND 3042)
(def ^:const GL_CULL_FACE 2884)
(def ^:const GL_FRONT 1028)
(def ^:const GL_BACK 1029)
(def ^:const GL_FRONT_AND_BACK 1032)
(def ^:const GL_MULTISAMPLE 32925)


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
