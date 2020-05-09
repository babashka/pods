(ns babashka.pods.sci-test
  (:require [babashka.pods.sci :as pods]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is]]
            [sci.core :as sci]))

(deftest sci-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (sci/binding [sci/out out
                          sci/err err]
              (sci/eval-string
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
  ex-result]"
               {:namespaces {'babashka.pods
                             {'load-pod pods/load-pod}
                             'clojure.core.async
                             {'<!! async/<!!}}}))]

    (is (= '[{:a 1, :b 2}
             6
             [1 2 3 4 5 6 7 8 9]
             "Illegal arguments / {:args (1 2 3)}"] ret))
    (is (= "(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n" (str out)))
    (is (= "(\"hello\" \"print\" \"this\" \"error\")\n" (str err)))))
