(ns babashka.pods.test-common)

(def test-program
  "
(require '[babashka.pods :as pods])
(require '[clojure.core.async :as async])

(pods/load-pod [\"clojure\" \"-A:test-pod\" \"--run-as-pod\"])
(require '[pod.test-pod :as pod])
(def stream-results (atom []))
(let [chan (pod.test-pod/range-stream 1 10)]
  (loop []
    (when-let [x (async/<!! chan)]
      (swap! stream-results conj x)
      (recur))))
(def ex-result
  (try (pod.test-pod/error 1 2 3)
    (catch clojure.lang.ExceptionInfo e
      (str (ex-message e) \" / \" (ex-data e)))))
(pod.test-pod/print \"hello\" \"print\" \"this\" \"debugging\" \"message\")
(pod.test-pod/print-err \"hello\" \"print\" \"this\" \"error\")
[(pod/assoc {:a 1} :b 2)
 (pod.test-pod/add-sync 1 2 3)
  @stream-results
  ex-result]")
