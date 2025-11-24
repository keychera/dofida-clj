(ns minusone.rules.gl.vao 
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::use keyword?)
(s/def ::vao any?)