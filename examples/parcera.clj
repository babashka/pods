(require '[babashka.pods :as pods])
(pods/load-pod 'org.babashka/parcera "0.0.1-SNAPSHOT")
(require '[pod.babashka.parcera :as parcera])

(prn (parcera/ast "(ns foo)"))
;;=> (:code (:list (:symbol "ns") (:whitespace " ") (:symbol "foo")))

(prn (parcera/code (parcera/ast "(ns foo)")))
