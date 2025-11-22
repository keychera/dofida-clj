(ns engine.sugar 
  (:require
   [play-cljc.math :as pl-cljc-m]))

(defn f32-arr [& els] (#?(:clj float-array :cljs #(js/Float32Array. %)) els))

(def identity-mat-4
  #?(:clj (float-array (pl-cljc-m/identity-matrix 4))
     :cljs (pl-cljc-m/identity-matrix 4)))
