# babashka.pods

A library to load babashka pods. Used by babashka but also usable from the JVM
or other [sci](https://github.com/borkdude/sci)-based projects.

More information about babashka pods can be found
[here](https://github.com/borkdude/babashka/blob/master/doc/pods.md).

## Usage

Using [pod-babashka-hsqldb](https://github.com/borkdude/pod-babashka-hsqldb) as
an example pod.

On the JVM:

``` clojure
(require '[babashka.pods :as pods])
(pods/load-pod "pod-babashka-hsqldb")
(require '[pod.babashka.hsqldb :as sql])

(def db "jdbc:hsqldb:mem:testdb;sql.syntax_mys=true")
(sql/execute! db ["create table foo ( foo int );"])
;;=> [#:next.jdbc{:update-count 0}]
```

From the [Small Clojure Interpreter](https://github.com/borkdude/sci):

See [test/babashka/pods/sci_test.clj](test/babashka/pods/sci_test.clj).

## Why JVM support?

- Babashka pods allow you to leverage functionality from other programs
regardless of the technology they were implemented in. As such, pods can be a
light weight replacement for native interop (JNI, JNA, etc.).

- When developing pods, this library can be used to run tests for it on the JVM.

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
