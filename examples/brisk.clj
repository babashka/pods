(require '[babashka.pods :as pods])

(pods/load-pod 'justone/brisk "0.2.0")

(require '[pod.brisk :as brisk])

(brisk/freeze-to-file "pod.nippy" {:han :solo})
(prn (brisk/thaw-from-file "pod.nippy"))
