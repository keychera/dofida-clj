(ns engine.sugar 
  (:require
   [play-cljc.math :as pl-cljc-m]))

(defn f32-arr [& elems] (#?(:clj float-array :cljs #(js/Float32Array. %)) elems))
(defn i32-arr [& elems] (#?(:clj int-array   :cljs #(js/Uint32Array. %))  elems))

(def identity-mat-4
  #?(:clj (float-array (pl-cljc-m/identity-matrix 4))
     :cljs (pl-cljc-m/identity-matrix 4)))
