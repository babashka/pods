(ns babashka.pods.sci-test
  (:require [babashka.pods.sci :as pods]
            [babashka.pods.test-common :refer [test-program assertions pod-registry]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [sci.core :as sci]))

(deftest sci-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ctx-ref (volatile! nil)
        ctx (sci/init {:namespaces {'babashka.pods
                                    {'load-pod (fn [& args]
                                                 (apply pods/load-pod @ctx-ref args))
                                     'invoke pods/invoke
                                     'unload-pod pods/unload-pod
                                     'add-transit-read-handler!  pods/add-transit-read-handler!
                                     'add-transit-write-handler! pods/add-transit-write-handler!
                                     'set-transit-default-write-handler! pods/set-transit-default-write-handler!}}
                       :classes {'System System
                                 'java.time.LocalDateTime java.time.LocalDateTime
                                 'java.lang.Class Class}})
        _ (vreset! ctx-ref ctx)
        ret (sci/binding [sci/out out
                          sci/err err]
              (sci/eval-string* ctx test-program))]
    (assertions out err ret)))

(deftest pod-registry-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ex (binding [*out* out
                     *err* err]
             (try (load-string
                   pod-registry)
                  (catch Exception e
                    e)))]
    (is (str/includes? (str out) "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2"))
    (is (str/includes? (pr-str ex) "Version must be provided" ))))
