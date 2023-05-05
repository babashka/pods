(require '[babashka.pods :as pods])

(def fmt (or (System/getenv "BABASHKA_POD_TEST_FORMAT")
             "edn"))

(def socket (System/getenv "BABASHKA_POD_TEST_SOCKET"))

(def cmd (cond-> ["clojure" "-M:test-pod"]
           (= "json" fmt) (conj "--json")
           (= "transit+json" fmt) (conj "--transit+json")))

;; (.println System/err cmd)

(def pod-id (:pod/id (pods/load-pod cmd
                                    {:socket (boolean socket)})))

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

(def add-sync-meta (:doc (meta #'pod.test-pod/add-sync)))
(def error-meta (:doc (meta #'pod.test-pod/error)))
(def read-other-tag-meta (:doc (meta #'pod.test-pod/read-other-tag)))

(def x9 pod.test-pod/x9)

(def tagged (if (= "edn" fmt)
              (pod/reader-tag)
              [1 2 3]))

(def other-tagged
  (if (= "edn" fmt)
    (pod/other-tag)
    [[1] [1]]))

(def fn-called (pod.test-pod/fn-call inc 2))

;; (.println System/err (str :fmt " " fmt))
(def local-date-time
  (if (= "transit+json" fmt)
    (instance? java.time.LocalDateTime (pod.test-pod/local-date-time (java.time.LocalDateTime/now)))
    true))

(def assoc-string-array
  (if (= "transit+json" fmt)
    (let [v (:a (pod.test-pod/assoc {} :a (into-array String ["foo"])))]
      (.isArray (class v)))
    true))

(def round-trip-meta
  (if (= "transit+json" fmt)
    (= {:my-meta 2} (meta (pod.test-pod/round-trip-meta (with-meta [2] {:my-meta 2}))))
    true))

(def round-trip-meta-nested
  (if (= "transit+json" fmt)
    (= {:my-meta 3} (meta (first (pod.test-pod/round-trip-meta [(with-meta [3] {:my-meta 3})]))))
    true))

(require '[pod.test-pod.only-code :as only-code])
(def should-be-1 (only-code/foo))

(require '[pod.test-pod.loaded2 :as loaded2])
(def loaded (loaded2/loaded 1))

(def incorrect-edn-response
  (try (pod.test-pod/incorrect-edn)
       (catch Exception e (ex-message e))))

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
 loaded
 fn-called
 local-date-time
 assoc-string-array
 round-trip-meta
 round-trip-meta-nested
 should-be-1
 add-sync-meta
 error-meta
 read-other-tag-meta
 incorrect-edn-response]
