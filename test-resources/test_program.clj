(require '[babashka.pods :as pods])
(prn (pods/load-pod ["clojure" "-A:test-pod"])) ;; should return nil
(require '[pod.test-pod :as pod])

(def stream-results (atom []))
(def done-prom (promise))
(pods/invoke "pod.test-pod" 'pod.test-pod/range-stream [1 10]
             {:handlers {:success (fn [value]
                                    (swap! stream-results conj value))
                         :done (fn []
                                 (deliver done-prom :ok))}})
@done-prom

(def ex-result
  (try (pod.test-pod/error 1 2 3)
       (catch clojure.lang.ExceptionInfo e
         (str (ex-message e) " / " (ex-data e)))))

(pod.test-pod/print "hello" "print" "this" "debugging" "message")
(pod.test-pod/print-err "hello" "print" "this" "error")

(pod/do-twice (prn :foo))

(def callback-result (promise))
(pods/invoke "pod.test-pod" 'pod.test-pod/add-sync [1 2]
             {:handlers {:success
                         (fn [value]
                           (deliver callback-result value))}})

(def error-result (promise))
(pods/invoke "pod.test-pod" 'pod.test-pod/add-sync ["1" 2]
             {:handlers
              {:error (fn [m]
                        (deliver error-result m))}})

[(pod/assoc {:a 1} :b 2)
 (pod.test-pod/add-sync 1 2 3)
 @stream-results
 ex-result
 (pod.test-pod/return-nil)
 @callback-result
 (:ex-message @error-result)
 (:ex-data @error-result)]
