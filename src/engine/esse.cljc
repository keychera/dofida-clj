(ns engine.esse
  (:require
   [clojure.spec.alpha :as s]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(defn ->sprite [x y sprite]
  {::x x ::y y ::current-sprite sprite})

(s/def ::x number?)
(s/def ::y number?)
(s/def ::compiled-shader map?)
(s/def ::current-sprite (s/nilable map?))