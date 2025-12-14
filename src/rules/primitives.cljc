(ns rules.primitives)

(def plane3d-vertices
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 0.0,  1.0 -1.0 0.0, -1.0  1.0 0.0,
    -1.0  1.0 0.0,  1.0 -1.0 0.0,  1.0  1.0 0.0]))

(def plane3d-uvs
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [0.0 0.0, 1.0 0.0, 0.0 1.0,
    0.0 1.0, 1.0 0.0, 1.0 1.0]))
