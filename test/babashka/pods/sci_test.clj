(ns babashka.pods.sci-test
  (:require [babashka.pods.sci :as pods]
            [babashka.pods.test-common :refer [test-program assertions]]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest]]
            [sci.core :as sci]))

(deftest sci-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (sci/binding [sci/out out
                          sci/err err]
              (sci/eval-string
               test-program
               {:namespaces {'babashka.pods
                             {'load-pod pods/load-pod}
                             'clojure.core.async
                             {'<!! async/<!!}}}))]

    (assertions out err ret)))
