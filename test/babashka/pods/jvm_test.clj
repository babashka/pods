(ns babashka.pods.jvm-test
  (:require [clojure.test :refer [deftest is]]))

(deftest jvm-test2
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (binding [*out* out
                      *err* err]
              (try (load-string
                    "
(require '[babashka.pods.jvm :as pods])

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
                   (catch Exception e (prn e))))]

    (is (= '[{:a 1, :b 2}
             6
             [1 2 3 4 5 6 7 8 9]
             "Illegal arguments / {:args (1 2 3)}"] ret))
    (is (= "(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n" (str out)))
    (is (= "WARNING: assoc already refers to: #'clojure.core/assoc in namespace: pod.test-pod, being replaced by: #'pod.test-pod/assoc\nWARNING: print already refers to: #'clojure.core/print in namespace: pod.test-pod, being replaced by: #'pod.test-pod/print\n(\"hello\" \"print\" \"this\" \"error\")\n" (str err)))))
