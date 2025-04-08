(ns babashka.pods.jvm-test
  (:require [babashka.pods.test-common :refer [test-program assertions
                                               pod-registry]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(deftest jvm-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (binding [*out* out
                      *err* err]
              (try (load-string
                    test-program)
                   (catch Exception e (prn e))))]

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
    (is (str/includes? (pr-str ex) "Version or path must be provided"))))
