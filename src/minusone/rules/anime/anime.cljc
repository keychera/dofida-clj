(ns minusone.rules.anime.anime
  (:require
   [com.rpl.specter :as sp]
   [minusone.rules.anime.keyframe :as keyframe]
   [minusone.rules.gl.gltf :as gltf]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [odoyle.rules :as o]))

(defn- resolve-sampler
  "this needs input with some related data connected (:input. :output -> bufferView, :target)"
  [gltf-data bin input-accessor]
  (println input-accessor)
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

(defonce db* (atom {}))

(def rules
  (o/ruleset
   {::process-gltf-anime
    [:what
     [esse-id ::gltf/bin bins]]}))

(comment
  (let [data      (:minusone.simple-gltf/simpleanime @gltf/debug-data*)
        bin       (:bin data)
        gltf-data (:gltf-data data)
        accessors (:accessors gltf-data)
        resolver  (partial resolve-sampler gltf-data bin)
        animes    (eduction
                   (map (fn [{:keys [channels samplers] :as anime}]
                          (-> anime
                              (dissoc :samplers)
                              (assoc :channels
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
                                             (assoc channel
                                                    :max-input (:max-input channel)
                                                    :keyframes (map vector (:input channel) (:output channel)))))
                                      (map (fn [{:keys [max-input keyframes] :as channel}]
                                             (let [kfs (->> (take (inc (count keyframes)) (cycle keyframes))
                                                            (partition 2 1)
                                                            (map (fn [[start-kf next-kf]]
                                                                   (let [[input output] start-kf
                                                                         [next-input next-output] next-kf]
                                                                     {::keyframe/inp input
                                                                      ::keyframe/out output
                                                                      ::keyframe/next-inp (if (= next-input max-input) 0 next-input)
                                                                      ::keyframe/next-out next-output
                                                                      ::keyframe/anime-fn identity}))))]
                                               (-> channel
                                                   (dissoc :input :output)
                                                   (assoc :keyframes kfs)))))
                                      channels)))))
                   (:animations gltf-data))]
    animes))
