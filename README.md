# dofida-clj

> dofida, an esse made of stars, shaders, and clojure data structures.

## dev

we mostly dev in jvm lately. since assimp has failed us and we need clojure gloss.

### jvm

jvm desktop
```
clj -T:build desktop
```

jvm desktop with repl and imgui
```
clj -T:build repl
```

### js

js shadow-cljs dev
```
Calva Jack-in
```

js release
```
npx shadow-cljs release game
clj -M:serve :port 1339 :dir "resources/public"
```

## docs

jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html

webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext