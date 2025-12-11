(ns hooks
  (:require
   [clj-kondo.hooks-api :as api]))

(defn gl-hook
  [{:keys [node]}]
  (let [[_gl context _fn-name & args] (:children node)]
    ;; using 'vector is just a trick to make the context and args appear as used
    {:node (api/list-node (list* (api/token-node 'vector) context args))}))

(def rule-delimiter #{:what :when :then :then-finally})

(defn let-one-and-fake-use [sym-nodes faker]
  (let [per-symbol-def (transduce
                        (map #(map-indexed vector %))
                        concat
                        (vals (group-by api/sexpr sym-nodes)))
        let-symbols    (transduce
                        (map (fn [[idx node]]
                               (if (= idx 0)
                                 [node (api/token-node faker) (api/token-node '_) (api/token-node (api/sexpr node))]
                                 [(api/token-node '_) node])))
                        concat per-symbol-def)]
    (into [] let-symbols)))

(defn ruleset-node->lets-node [ruleset-node]
  (eduction
   (map (fn [[_rule-name-node rule-block-node]]
          (partition-by (fn [node] (rule-delimiter (api/sexpr node)))
                        (:children rule-block-node))))
   (map (fn [blocks]
          (loop [[node & remaining] blocks rule-block {} looking-at nil]
            (if node
              (if looking-at
                (recur remaining (assoc rule-block looking-at node) nil)
                (recur remaining rule-block (keyword (str (name (api/sexpr (first node))) "-block"))))
              rule-block))))
   (map (fn [{:keys [what-block when-block then-block then-finally-block]}]
          (let [what-nodes    (flatten (map :children what-block))
                sym-nodes     (into [] (comp (filter (comp symbol? api/sexpr))) what-nodes)
                keyword-nodes (into [] (comp (filter (comp keyword? api/sexpr))) what-nodes)
                sym-nodes     (let-one-and-fake-use sym-nodes '[])
                reserved-sym  (let-one-and-fake-use (into [] (map api/token-node) ['session 'match]) '[])]
            (api/list-node
             (list*
              (api/token-node 'let)
              (api/vector-node (conj reserved-sym
                                     (api/token-node '_)
                                     (api/list-node (list* (api/token-node 'vector) keyword-nodes))))
              (api/list-node (list (api/token-node 'prn) (api/token-node :separator)))
              (api/list-node
               (list*
                (api/token-node 'let)
                (api/vector-node (cond-> sym-nodes
                                   when-block (conj (api/token-node '_) (api/list-node when-block))))
                then-block))
              then-finally-block)))))
   (partition 2 (:children ruleset-node))))

(defn odoyle-ruleset-hook
  [{:keys [node]}]
  (let [ruleset-node       (nth (:children node) 1)
        rule-nodes-as-lets (ruleset-node->lets-node ruleset-node)]
    {:node (api/list-node (list* (api/token-node 'vector) rule-nodes-as-lets))}))

(comment
  ; debugging zone 
  (let [node (api/token-node ':-)]
    (api/reg-finding!
     {:row (:row (meta node))
      :col (:col (meta node))
      :message (apply str (:children node))
      :type    :dofida/angry}))

  (let [node (api/parse-string "(o/ruleset
    {::window
     [:what
      [::window ::dimension dimension]]})")
        ruleset-node (nth (:children node) 1)]
    (ruleset-node->lets-node ruleset-node)
    (odoyle-ruleset-hook {:node node})))

(vector (let [session ((fn* [] ())) _ session match ((fn* [] ())) _ match _ (vector ::window ::dimension)]
          (prn :separator)
          (let [dimension ((fn* [] ())) _ dimension])))

(vector (let [session []
              _ session
              match []
              _ match
              _ (vector ::window ::dimension)]
          (prn :separator)
          (let [dimension ((fn* [] ())) _ dimension])))