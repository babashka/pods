(require '[babashka.pods :as pods])

(pods/load-pod 'org.babashka/buddy "0.0.1")

(require '[pod.babashka.buddy.codecs :as codecs]
         '[pod.babashka.buddy.hash :as hash])

(println (-> (hash/sha256 "foobar")
             (codecs/bytes->hex)))

(pods/load-pod 'org.babashka/etaoin) ;; should cause error when version & path are missing
