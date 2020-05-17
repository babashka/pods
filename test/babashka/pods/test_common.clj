(ns babashka.pods.test-common
  (:require [clojure.test :refer [is]]))

(def test-program "
(require '[babashka.pods :as pods])
(require '[clojure.core.async :as async])

(prn (pods/load-pod [\"clojure\" \"-A:test-pod\"])) ;; should return nil
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
(pod/do-twice (prn :foo))
[(pod/assoc {:a 1} :b 2)
 (pod.test-pod/add-sync 1 2 3)
  @stream-results
  ex-result
  (pod.test-pod/return-nil)]")

(defn assertions [out err ret]
  (is (= '[{:a 1, :b 2}
           6
           [1 2 3 4 5 6 7 8 9]
           "Illegal arguments / {:args (1 2 3)}"
           nil] ret))
  (is (= "nil\n(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n:foo\n:foo\n" (str out)))
  (is (= "(\"hello\" \"print\" \"this\" \"error\")\n" (str err))))
