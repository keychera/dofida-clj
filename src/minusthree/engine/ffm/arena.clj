(ns minusthree.engine.ffm.arena
  (:require
   [clojure.spec.alpha :as s]) 
  (:import
   [java.lang.foreign Arena]))

;; https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md

(s/def ::arena #(instance? Arena %))
(s/def ::game-arena ::arena)
