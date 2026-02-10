# dofida-clj

> dofida, an esse made of stars, shaders, and clojure data structures.

### -3

```bash
clj -T:build minusthree
```

release
```bash
clj -T:build minusthree-rel

# or

clj -M:jvm:imgui -m minusthree.platform.jvm.jvm-game
```


### jvm

jvm desktop
```bash
clj -T:build desktop
```

jvm desktop with repl and imgui
```bash
clj -T:build repl
```

### js

js shadow-cljs dev
```bash
bun install
Calva Jack-in
```

js release
```bash
bun install
bun shadow-cljs release game
clj -M:serve :port 1339 :dir "resources/public"
```

## docs

jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html

webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext


## ffmpeg

```
ffmpeg -framerate 24 -i render-%04d.png -c:v libx264 -pix_fmt yuv420p output.mp4
```

## caution

interesting bug things we encounter/want to be wary off:
    
regarding graphics rendering
    
1. renders fine but the object is out of camera
2. ...or too small
3. ...or not visible in the current camera angle (culled, or a 2d plane seen from side)
4. sometimes optical illusion might occur and you thought it's a software bug

regarding odoyle rules

1. the assimetry of insert and retract, especially related to repl driven development where not inserting doesn't mean retracting so old facts may lingers
2. the rules being too general that it pickes up existing behaviours or facts you don't intend
3. the ordering still opaque sometimes but mostly caused by no.2 currently we rely on ::time/slize or we probably will rely on specific fact insertion like we did in berkelana-clj
