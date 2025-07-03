# dofida-clj

this is created with [play-cljc](https://github.com/oakes/play-cljc)

To build this project, you'll need the Clojure CLI tool:

https://clojure.org/guides/deps_and_cli


To develop in a browser with live code reloading:

```
clj -M:dev
```


To build a release version for the web:

```
clj -M:prod
```


To develop the native version:

```
clj -M:dev native

# NOTE: On Mac OS, you need to add the macos alias:

clj -M:dev:macos native
```


To build the native version as a jar file:

```
clj -M:prod uberjar
```


in calva
```
Jack-in -> deps.edn + Figwheel main -> eval engine.engine/tick
```

## shadow-cljs migration

what I did
1. create package.json file and write `{}`
2. npm install --save-dev shadow-cljs
3. npm install -g shadow-cljs
4. shadow-cljs init
5. copy shadow-cljs.edn from ref 1 make it use deps.edn  shadow-cljs alias
6. create the shadow-cljs alias in deps.edn
7. change the script tag's src to point to `js/main.js`
8. change the reload hook in refresh.cljc
9. shadow-cljs watch game

ref:
1. https://github.com/pesterhazy/cljs-spa-example/compare/master...thheller:shadow-cljs
2. copilot helping me navigate the js world
