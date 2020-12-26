#!/usr/bin/env bb

(require '[babashka.pods :as pods])
(pods/load-pod 'org.babashka/filewatcher "0.0.1-SNAPSHOT")

(require '[pod.babashka.filewatcher :as fw])

(fw/watch "/tmp" (fn [event] (prn event)) {:delay-ms 50})

@(promise)
