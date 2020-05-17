(ns babashka.pods.jvm-test
  (:require [babashka.pods.test-common :refer [test-program assertions]]
            [clojure.test :refer [deftest]]))

(deftest jvm-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (binding [*out* out
                      *err* err]
              (try (load-string
                    test-program)
                   (catch Exception e (prn e))))]

    (assertions out err ret)))
