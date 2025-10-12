# dofida-clj

To develop in a browser with live code reloading:

## js

js shadow-cljs dev
```
Calva Jack-in
```

js release
```
npx shadow-cljs release game
clj -M:serve :port 1339 :dir "resources/public"
```

## jvm

jvm desktop
```
clj -T:build desktop
```

jvm desktop with repl and imgui
```
clj -T:build repl
```
