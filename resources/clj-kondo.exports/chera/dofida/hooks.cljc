(ns hooks
  (:require [clj-kondo.hooks-api :as api]))

(defn gl-hook
  [{:keys [node]}]
  (let [[_gl context _fn-name & args] (:children node)]
    ;; using 'vector is just a trick to make the context and args appear as used
    {:node (api/list-node (list* (api/token-node 'vector) context args))}))

(comment
  ; use this for hooks debugging
  #_(api/reg-finding!
     {:row (:row (meta node))
      :col (:col (meta node))
      :message (apply str (:children node))
      :type    :dofida/angry}))

