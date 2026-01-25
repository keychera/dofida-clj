(ns minustwo.stage.celestial
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :refer [gl-ctx]]
   [engine.math.primitives :as primitives]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.pacing :as pacing]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_FLOAT GL_TRIANGLES]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.room :as room]
   [minustwo.zone.render :as render]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]))

;; cmzw_ https://x.com/i/status/1964694609046700408

(def shader-omocha-vs
  (str cljgl/version-str
       "
        precision mediump float;
        
        in vec3 a_pos;
        in vec2 a_uv;

        uniform mat4 u_model;
        
        out vec2 UV;
        
        void main() {
          gl_Position = u_model * vec4(a_pos, 1.0);
          UV = a_uv;
        }"))

(def shader-omocha-fs
  ;; wtf I forgot I already made this in glsl 💀 
  ;; https://github.com/keychera/shader/blob/main/glslCanvas/cmzw_/stylized-flame.frag 
  (str cljgl/version-str
       "
precision mediump float;

in vec2 UV;
uniform float u_time;
out vec4 o_color;

float rand2(in vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))
                 * 43758.5453123);
}

vec3 rand3(vec2 x) {
    return fract(cos(mod(vec3(dot(x, vec2(13.9898, 8.141)),
							  dot(x, vec2(3.4562, 17.398)),
                              dot(x, vec2(13.254, 5.867))), vec3(3.14))) * 43758.5);
}

float voronoise_noise_2d( vec2 coord, vec2 size, float offset, float seed) {
  vec2 i = floor(coord) + rand2(vec2(seed, 1.0-seed)) + size;
  vec2 f = fract(coord);
  
  vec2 a = vec2(0.0);
  
  for( int y=-2; y<=2; y++ ) {
    for( int x=-2; x<=2; x++ ) {
      vec2  g = vec2( float(x), float(y) );
      vec3  o = rand3( mod(i + g, size) + vec2(seed) );
      o.xy += 0.25 * sin(offset * 6.28318530718 + 6.28318530718*o.xy);
      vec2  d = g - f + o.xy;
      float w = pow( 1.0-smoothstep(0.0, 1.414, length(d)), 1.0 );
      a += vec2(o.z*w,w);
    }
  }
  
  return a.x/a.y;
}

#define fbm_2d_voronoise_OCTAVES 5
#define fbm_2d_voronoise_FOLDS 0

float fbm_2d_voronoise(vec2 coord, vec2 size, float persistence, float offset, float seed) {
  float normalize_factor = 0.0;
  float value = 0.0;
  float scale = 1.0;
  for (int i = 0; i < fbm_2d_voronoise_OCTAVES; i++) {
    float noise = voronoise_noise_2d(coord*size, size, offset, seed);
    for (int f = 0; f < fbm_2d_voronoise_FOLDS; ++f) {
      noise = abs(2.0*noise-1.0);
    }
    value += noise * scale;
    normalize_factor += scale;
    size *= 2.0;
    scale *= persistence;
  }
  return value / normalize_factor;
}


float cellular2_noise_2d(vec2 coord, vec2 size, float offset, float seed) {
  vec2 o = floor(coord)+rand2(vec2(seed, 1.0-seed))+size;
  vec2 f = fract(coord);
  float min_dist1 = 2.0;
  float min_dist2 = 2.0;
  for(float x = -1.0; x <= 1.0; x++) {
    for(float y = -1.0; y <= 1.0; y++) {
      vec2 neighbor = vec2(float(x),float(y));
      vec2 node = rand2(mod(o + vec2(x, y), size)) + vec2(x, y);
      node = 0.5 + 0.25 * sin(offset * 6.28318530718 + 6.28318530718*node);
      vec2 diff = neighbor + node - f;
      float dist = length(diff);
      if (min_dist1 > dist) {
        min_dist2 = min_dist1;
        min_dist1 = dist;
      } else if (min_dist2 > dist) {
        min_dist2 = dist;
      }
    }
  }
  return min_dist2-min_dist1;
}

#define fbm_2d_cellular_OCTAVES 4
#define fbm_2d_cellular_FOLDS 0

float fbm_2d_cellular(vec2 coord, vec2 size, float persistence, float offset, float seed) {
  float normalize_factor = 0.0;
  float value = 0.0;
  float scale = 1.0;
  for (int i = 0; i < fbm_2d_cellular_OCTAVES; i++) {
    float noise = cellular2_noise_2d(coord*size, size, offset, seed);
    for (int f = 0; f < fbm_2d_cellular_FOLDS; ++f) {
      noise = abs(2.0*noise-1.0);
    }
    value += noise * scale;
    normalize_factor += scale;
    size *= 2.0;
    scale *= persistence;
  }
  return value / normalize_factor;
}

float cell_min_vor(vec2 UV) {
     float vor_fbm = fbm_2d_voronoise(UV, vec2(2.), 0.5, 0., 0.);
    float cell_fbm = 1. - fbm_2d_cellular(UV, vec2(4.), 0.5, 0., 0.);
    return cell_fbm - (vor_fbm * 0.5);
}

float circle(vec2 UV, vec2 scale) {
    return 1. - length(abs((UV - 0.5) * scale) * 2.);
}

float smin( float a, float b, float k )
{
    float res = exp2( -k*a ) + exp2( -k*b );
    return -log2( res )/k;
}

float remap(float in_v, float min_v, float max_v, float step_v) {
    float v = in_v * (max_v - min_v);
    return min_v + v - mod(v, max(step_v, 0.00001));
}

float tones_map(float in_v, float in_min, float in_max, float out_min, float out_max) {
   
    float v = min(in_min, in_v);
    v = max(in_max, v);
     float ratio = (in_v - in_min)/in_max;
    return v * ratio * (out_max - out_min) / out_max;
}

float map_a(vec2 UV) {
    vec2 UV_a = UV;
    UV_a.y += (-u_time * 1.7) + (rand2(UV_a)*0.01);
    return cell_min_vor(UV_a);
}

vec2 slope_with_a(vec2 UV, float epsilon) {
	float x = map_a(fract(UV + vec2(epsilon, 0.))) - map_a(fract(UV - vec2(epsilon, 0.)));
    float y = map_a(fract(UV + vec2(0., epsilon))) - map_a(fract(UV - vec2(0., epsilon)));
    return vec2(x, y);
}

float map_s_a(vec2 UV) {
    float sine_a = sin((UV.y - u_time) * 8.);
    float sm_st_grad = smoothstep(0.3, 1., UV.y);
    float value = sine_a * sm_st_grad;
    return remap(value, 0.5, 1., 0.);
}

float warp_s_a_with_a(vec2 UV, float amount) {
    vec2 sloper = slope_with_a(UV, 0.01075);
    vec2 warper = UV + (amount * sloper);
    return map_s_a(warper);
}

float the_c(vec2 UV, float width) {
    vec2 UV_a = UV;
    UV_a.y += (-u_time * 1.7) + (rand2(UV_a)*0.01);
    float a = cell_min_vor(UV_a);
    float a_circle = circle(UV, vec2(2.,1.));
    return smin(a_circle * width, a, 0.33);
}

void main() {
    // voronoise
    float value = 0.;

    float vor_fbm = value = fbm_2d_voronoise(UV, vec2(2.), 0.5, 0., 0.);
    float cell_fbm = value = 1. - fbm_2d_cellular(UV, vec2(4.), 0.5, 0., 0.);
    
    value = cell_min_vor(UV);
    
    vec2 UV_a = UV;
    UV_a.y += (-u_time * 1.7) + (rand2(UV_a)*0.01);
    float a = value = cell_min_vor(UV_a);
    
    vec2 UV_b = UV;
    UV_b.y += (-u_time * 1.9) + (rand2(UV_b)*0.01);
    float b = value = cell_min_vor(UV_b);
    
    value = circle(UV, vec2(1.));
    
	float sine_a = value = sin((UV.y - u_time) * 8.);
    
    float sm_st_grad = value = smoothstep(0.3, 1., UV.y);
    
    float s_a = value = sine_a * sm_st_grad;

    value = remap(value, 0.5, 1., 0.);
    
    float warp = value = warp_s_a_with_a(vec2(UV.x, UV.y), 0.1);
    

    float width = 30.;
    float a_circle = value = circle(UV, vec2(2.,1.));
    value =  smin(a_circle * width, a, 0.33); // it's the c
    
    vec2 UV_c = UV;
    UV_c.x += (warp - 0.5)/2. ;
    value = the_c(UV_c, 30.);
    
    value = tones_map(value, 0.1, 0.05, -1., 1.);
    
    float hop = 1.1;
    value = floor(hop * value)/ hop;
    
    vec3 color = vec3(1.);
    color =  vec3(1.000,0.499,0.154);
    
	  o_color = vec4(color * vec3(value), value);
}"))

(defn init-fn [world _game]
  (-> world
      (esse ::celestia
            #::shader{:use ::celestia-shader}
            t3d/default)))

(defn after-load-fn [world game]
  (let [ctx (gl-ctx game)]
    (-> world
        (pacing/set-config {:max-progress 1e6})
        (esse ::celestia-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx shader-omocha-vs shader-omocha-fs)})
        (esse ::celestia
              #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                     :scale (v/vec3 0.25)}
              #::gl-magic{:casted? :pending
                          :spell [{:bind-vao ::celestia-vao}
                                  {:buffer-data primitives/plane3d-vertices :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr :a_pos :use-shader ::celestia-shader :count 3 :component-type GL_FLOAT}
                                  {:buffer-data primitives/plane3d-uvs :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr :a_uv :use-shader ::celestia-shader :count 2 :component-type GL_FLOAT}
                                  {:unbind-vao true}]}))))

(def rules
  (o/ruleset
   {::celestia
    [:what
     [::celestia ::gl-magic/casted? true]
     [::celestia ::t3d/transform model]
     [::celestia ::shader/use shader-id]
     [shader-id ::shader/program-info program-info]]}))

(defn render-fn [world _game]
  (let [room-data (utils/query-one world ::room/data)
        ctx       (:ctx room-data)
        project   (:project room-data)
        view      (:player-view room-data)
        vao-db*   (:vao-db* room-data)
        progress  (:progress (utils/query-one world ::pacing/progress))]
    (when-let [{:keys [model program-info]} (utils/query-one world ::celestia)]
      (let [vao (get @vao-db* ::celestia-vao)]
        (gl ctx useProgram (:program program-info))
        (gl ctx bindVertexArray vao)
        
        (cljgl/set-uniform ctx program-info :u_model (vec->f32-arr (vec model)))
        (cljgl/set-uniform ctx program-info :u_time (* 1e-4 Math/PI progress))

        (gl ctx drawArrays GL_TRIANGLES 0 6)))))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules
   ::world/render-fn #'render-fn})
