(ns engine.sugar)

(defn f32-arr [elems] (#?(:clj float-array :cljs #(js/Float32Array. %)) elems))
(defn i32-arr [elems] (#?(:clj int-array   :cljs #(js/Uint32Array. %))  elems))
