(ns babashka.pods.sci-test
  (:require [babashka.pods.sci :as pods]
            [babashka.pods.test-common :refer [test-program]]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is]]
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

    (is (= '[{:a 1, :b 2}
             6
             [1 2 3 4 5 6 7 8 9]
             "Illegal arguments / {:args (1 2 3)}"
             nil] ret))
    (is (= "nil\n(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n" (str out)))
    (is (= "(\"hello\" \"print\" \"this\" \"error\")\n" (str err)))))
