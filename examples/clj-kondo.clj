#!/usr/bin/env bb

(require '[babashka.pods :as pods])

(pods/load-pod 'borkdude/clj-kondo "2020.12.12")

(require '[pod.borkdude.clj-kondo :as clj-kondo])

(-> (clj-kondo/run! {:lint ["src"]})
    :summary)

