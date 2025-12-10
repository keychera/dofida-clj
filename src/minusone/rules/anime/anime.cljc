(ns minusone.rules.anime.anime
  (:require
   [com.rpl.specter :as sp]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minusone.rules.anime.keyframe :as keyframe]
   [minusone.rules.gl.gltf :as gltf]
   [odoyle.rules :as o]
   [rules.time :as time]
   [thi.ng.math.core :as m]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [clojure.spec.alpha :as s]
   [engine.math :as m-ext]))

(defn- resolve-sampler
  "this needs input with some related data connected (:input. :output -> bufferView, :target)"
  [gltf-data bin input-accessor]
  (let [bufferViews        (:bufferViews gltf-data)
        bufferView         (get bufferViews (:bufferView input-accessor))
        byteLength         (:byteLength bufferView)
        byteOffset         (:byteOffset bufferView)
        u8s                #?(:clj [bin byteLength byteOffset] ;; jvm TODO
                              :cljs (.subarray bin byteOffset (+ byteLength byteOffset)))
        component-size     4.0 ;; assuming all float for now
        component-per-elem (gltf/gltf-type->num-of-component (:type input-accessor))
        buffer             #?(:clj [component-size u8s] ;; jvm TODO
                              :cljs (vec (js/Float32Array. u8s.buffer u8s.byteOffset (/ u8s.byteLength component-size))))]
    (if (> component-per-elem 1)
      (partition component-per-elem buffer)
      buffer)))

(defn interpret-element [element path]
  (case path
    "translation" (apply v/vec3 element)
    "rotation" (apply q/quat element)
    "scale" (apply v/vec3 element)))

(s/def ::anime-name string?)
(s/def ::interpolation string?)
(s/def ::min-input float?)
(s/def ::max-input float?)
(s/def ::target-node int?)
(s/def ::target-path #{"translation" "rotation" "scale"})

(s/def ::anime
  (s/keys :req-un [::anime-name
                   ::interpolation
                   ::keyframe/keyframes
                   ::min-input
                   ::max-input
                   ::target-node
                   ::target-path]))

(s/def ::animes (s/coll-of ::anime))

(defn gltf->animes [gltf-data bin] ;; assuming only one bin for now 
  (when-let [animes (:animations gltf-data)]
    (let [accessors (:accessors gltf-data)
          resolver  (partial resolve-sampler gltf-data bin)]
      (transduce
       (map (fn [{:keys [channels samplers] :as anime}]
              (eduction
               (map (fn [{:keys [target sampler]}]
                      ;; flatten channel as a composite of sampler and target
                      (-> (get samplers sampler)
                          (assoc :target target))))
               (map #(update % :input (fn [id] (get accessors id))))
               (map #(assoc % ;; assume time as always scalar
                            :min-input (first (get-in % [:input :min]))
                            :max-input (first (get-in % [:input :max]))))
               (filter #(not (= (:max-input %) (:min-input %))))
               (map #(update % :output (fn [id] (get accessors id))))
               (map #(update % :input resolver))
               (map #(update % :output resolver))
               (map (fn [{:keys [target] :as this}]
                      (sp/transform [:output sp/ALL]
                                    (fn [ele] (interpret-element ele (-> target :path)))
                                    this)))
               (map (fn [channel]
                      (assoc channel :keyframes (map vector (:input channel) (:output channel)))))
               (map (fn [{:keys [keyframes target] :as channel}]
                      (let [kfs (->> (take (inc (count keyframes)) (cycle keyframes))
                                     (partition 2 1)
                                     (map (fn [[start-kf next-kf]]
                                            (let [[input output] start-kf
                                                  [next-input next-output] next-kf]
                                              {::keyframe/inp input
                                               ::keyframe/out output
                                               ::keyframe/next-inp next-input
                                               ::keyframe/next-out next-output
                                               ;; not yet parsing :interpolation
                                               ::keyframe/anime-fn identity})))
                                     butlast)]
                        (-> channel
                            (dissoc :input :output :target)
                            (assoc :keyframes kfs
                                   :target-node (:node target)
                                   :target-path (:path target)
                                   :anime-name (:name anime))))))
               channels)))
       concat animes))))

(defonce db* (atom {}))

(def rules
  (o/ruleset
   {::process-gltf-anime
    [:what
     [esse-id ::gltf/bins bins]
     [esse-id ::gltf/data gltf-data]
     :then
     (let [animes (gltf->animes gltf-data (first bins))]
       (when (seq animes)
         (println "anime for" esse-id)
         (swap! db* update ::animes concat
                (into [] (map #(assoc % :esse-id esse-id)) animes))))]

    ::animation-update
    [:what
     [::time/now ::time/total tt]
     :then
     (let [db             @db*
           max-progress   11.0
           duration       4800.0
           progress       (* max-progress (/ (mod tt duration) duration))
           running-animes (eduction
                           (filter #(#{"anim" "Survey"} (:anime-name %)))
                           (mapcat (fn [{:keys [keyframes esse-id max-input target-node target-path]}]
                                     (let [progress (mod progress max-input)]
                                       (into [] (map #(merge % (vars->map esse-id progress target-node target-path))) keyframes))))
                           (filter (fn [{:keys [progress] ::keyframe/keys [inp next-inp]}] (and (>= progress inp) (< progress next-inp))))
                           (map (fn [{:keys [progress esse-id target-node target-path]
                                      ::keyframe/keys [inp next-inp out next-out]}]
                                  (let [t        (/ (- progress inp) (- next-inp inp))
                                        mixed    (condp instance? out
                                                   thi.ng.geom.quaternion.Quat4 (m-ext/quat-mix out next-out t)
                                                   (m/mix out next-out t))]
                                    [esse-id target-node target-path mixed])))
                           (::animes db))]
       (swap! db* assoc ::interpolated
              (reduce
               (fn [m [esse-id target-node target-path v]] (assoc-in m [esse-id target-node target-path] v))
               {} running-animes)))]}))

(def system
  {::world/after-load-fn (fn [world _game]
                           (reset! db* {})
                           world)
   ::world/rules rules})

(comment
  (let [data      (:minusone.simple-gltf/simpleanime @gltf/debug-data*)
        bin       (:bin data)
        gltf-data (:gltf-data data)
        animes    (gltf->animes gltf-data bin)]
    (s/conform ::animes (into [] (map #(assoc % :esse-id :me)) animes)))
  
  (tagged-literal 'flare/html {:title "game"
                               :url (str "http://localhost:9333/" (rand))
                               :reveal true
                               :sidebar-panel? true})

  (::interpolated @db*)
  (into []
        (filter #(= (:esse-id %) :minusone.simple-gltf/simpleskin))
        (::animes @db*))

  :-)