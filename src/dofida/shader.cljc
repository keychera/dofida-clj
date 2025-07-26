(ns dofida.shader)

(defn merge-shader-fn [& maps]
  (let [res        (apply merge-with merge maps)
        has-main?  (get-in res [:signatures 'main])]
    (when (not has-main?) (throw (#?(:clj Exception. :cljs js/Error.) "shader has no main")))
    res))