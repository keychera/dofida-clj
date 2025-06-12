(ns dofida.shader)

(defn merge-shader-fn [& maps]
  (let [res        (apply merge-with merge maps)
        sign-count (count (keys (:signatures res)))
        has-main?  (get-in res [:signatures 'main])]
    (when (> sign-count 8)
      (throw (#?(:clj Exception. :cljs js/Error.) (str "only shader with max 8 signatures allowed, # of signature: " sign-count))))
    (when (not has-main?)
      (throw (#?(:clj Exception. :cljs js/Error.) "shader has no main")))
    res))