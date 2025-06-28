(ns engine.esse 
  (:require
   [clojure.spec.alpha :as s]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(defn ->shader []
  {::compiled-shader {}})

;; specs

(s/def ::compiled-shader map?)