(require '[babashka.pods :as pods])
(def pod-id (:pod/id (pods/load-pod ["clojure" "-A:test-pod"])))
(require '[pod.test-pod :as pod])
(def pod-ns-name (ns-name (find-ns 'pod.test-pod)))

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
(pods/invoke pod-id 'pod.test-pod/add-sync [1 2]
             {:handlers {:success
                         (fn [value]
                           (deliver callback-result value))}})

(def sync-invoke
  (pods/invoke pod-id 'pod.test-pod/add-sync [1 2]))

(def error-result (promise))
(pods/invoke pod-id 'pod.test-pod/add-sync ["1" 2]
             {:handlers
              {:error (fn [m]
                        (deliver error-result m))}})

(def assoc-result (pod/assoc {:a 1} :b 2))
(def add-result (pod.test-pod/add-sync 1 2 3))
(def nil-result (pod.test-pod/return-nil))

(def x9 pod.test-pod/x9)

(def tagged (pod/reader-tag))
(def other-tagged (pod/other-tag))

(require '[pod.test-pod.loaded])

(def loaded (pod.test-pod.loaded/loaded 1))

(pods/unload-pod pod-id)
(def successfully-removed (nil? (find-ns 'pod.test-pod)))

[pod-id
 pod-ns-name
 assoc-result
 add-result
 sync-invoke
 @stream-results
 ex-result
 nil-result
 @callback-result
 (:ex-message @error-result)
 (:ex-data @error-result)
 successfully-removed
 x9
 tagged
 other-tagged
 loaded]
