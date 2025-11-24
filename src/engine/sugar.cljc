(ns engine.sugar)

(defn f32-arr ^floats [elems] (#?(:clj float-array :cljs #(js/Float32Array. %)) elems))
(defn i32-arr ^ints [elems] (#?(:clj int-array   :cljs #(js/Uint32Array. %))  elems))
