(ns engine.sugar)

(defn vec->f32-arr ^floats [elems] (#?(:clj float-array :cljs #(js/Float32Array. %)) elems))
(defn f32-arr ^floats [size] (#?(:clj float-array :cljs #(js/Float32Array. %)) size))
(defn vec->i32-arr ^ints [elems] (#?(:clj int-array :cljs #(js/Uint32Array. %)) elems))
(defn i32-arr ^floats [size] (#?(:clj int-array :cljs #(js/Uint32Array. %)) size))
