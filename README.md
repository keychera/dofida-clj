# dofida-clj

> dofida, an esse made of stars, shaders, and clojure data structures.

### -3

```bash
clj -T:build minusthree
```

play release 
```bash
clj -T:build minusthree-rel

# or

clj -M:jvm:imgui -m minusthree.platform.jvm.jvm-game
```

build release
```bash
clj -T:build minusthree-uber
```

compile classes
```bash
clj -T:build minusthree-compile
java -cp "$(clojure -A:imgui -Spath);target/input/classes" minusthree.platform.jvm.jvm_game
```

graalvm
```bash
# keychera's fastmath fork
git clone https://github.com/keychera/fastmath ../fastmath
pushd ../fastmath;clj -T:build compile-java; popd
clj -T:build minusthree-prepare-for-graal
# play around a bit with the game, building reachability-metadata.json, then
clj -T:build minusthree-graal
clj -T:build run-standalone
```

glfw linux debugging (wsl actually)
```bash
# ref https://github.com/alecjacobson/computer-graphics-bounding-volume-hierarchy
sudo apt-get install libglfw3-dev libgl1-mesa-dev libglu1-mesa-dev
sudo apt install mesa-utils
glxinfo | grep OpenGL

# actually doesn't need all of above. just install Ubuntu 24.04
# then follow https://www.graalvm.org/latest/getting-started/linux/
sudo apt-get install build-essential zlib1g-dev
# lastly just use out clojure tools.build command above (prepare -> graal)
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
