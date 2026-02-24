(ns minusthree.engine.ffm.memory 
  (:require
   [clojure.spec.alpha :as s]) 
  (:import
   [java.lang.foreign MemorySegment]))

(s/def ::segment #(instance? MemorySegment %))
