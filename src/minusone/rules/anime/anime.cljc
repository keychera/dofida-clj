(ns minusone.rules.anime.anime
  (:require
   [com.rpl.specter :as sp]
   [minusone.rules.anime.keyframe :as keyframe]
   [minusone.rules.gl.gltf :as gltf]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]))

(defn- resolve-sampler
  "this needs input with some related data connected (:input. :output -> bufferView, :target)"
  [gltf-json bin gltf-input]
  (let [bufferViews (:bufferViews gltf-json)
        bufferView (get bufferViews (:bufferView gltf-input))
        {:keys [byteLength byteOffset]} bufferView
        u8s    #?(:clj [bin byteLength byteOffset] ;; jvm TODO
                  :cljs (.subarray bin byteOffset (+ byteLength byteOffset)))
        component-size 4.0 ;; assuming all float for now
        component-per-element (gltf/gltf-type->num-of-component (:type gltf-input))
        buffer #?(:clj [component-size u8s] ;; jvm TODO
                  :cljs (vec (js/Float32Array. u8s.buffer u8s.byteOffset (/ u8s.byteLength component-size))))]
    (if (> component-per-element 1)
      (partition component-per-element buffer)
      buffer)))

(defn interpret-element [element path]
  (case path
    "translation" (apply v/vec3 element)
    "rotation" (apply q/quat element)
    "scale" (apply v/vec3 element)))

(comment
  (let [data      (:minusone.simple-gltf/simpleanime @gltf/debug-data*)
        bin       (:bin data)
        gltf-json (:gltf-json data)
        accessors (:accessors gltf-json)
        resolver  (partial resolve-sampler gltf-json bin)
        anime     (eduction
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
                                      channels)))))
                   (:animations gltf-json))
        channels (eduction
                  (map (fn [channel] {:max-input (:max-input channel)
                                      :keyframes (map vector (:input channel) (:output channel))}))
                  (map (fn [{:keys [max-input keyframes]}]
                         (->> (take (inc (count keyframes)) (cycle keyframes))
                              (partition 2 1)
                              (map (fn [[start-kf next-kf]]
                                     (let [[input output] start-kf
                                           [next-input next-output] next-kf]
                                       {::keyframe/inp input
                                        ::keyframe/out output
                                        ::keyframe/next-inp (if (= next-input max-input) 0 next-input)
                                        ::keyframe/next-out next-output
                                        ::keyframe/anime-fn identity}))))))
                  (-> anime first :channels))]
    channels))
