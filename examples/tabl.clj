#!/usr/bin/env bb

(require '[babashka.pods :as pods])
(pods/load-pod 'justone/tabl "0.2.0")

(require '[pod.tabl.fancy :as fancy])
(require '[pod.tabl.doric :as doric])

(fancy/print-table [{:foo 1 :bar 2} {:foo 2 :bar 3}])
(doric/print-table [{:foo 1 :bar 2} {:foo 2 :bar 3}])
