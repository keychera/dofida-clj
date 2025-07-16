(ns engine.esse
  (:require
   [clojure.spec.alpha :as s]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(defn ->sprite [x y image-to-load]
  {::x x ::y y ::image-to-load image-to-load})

(s/def ::x number?)
(s/def ::y number?)

(s/def ::shader-compile-fn fn?)
(s/def ::compiling-shader boolean?)
(s/def ::compiled-shader map?)

(s/def ::image-to-load string?)
(s/def ::loading-image boolean?)
(s/def ::current-sprite (s/nilable map?))