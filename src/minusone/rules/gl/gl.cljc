(ns minusone.rules.gl.gl
  (:require
    [clojure.spec.alpha :as s]))

;; dunno yet if this is a good abstraction
(s/def ::loaded? #{:pending :loading true})