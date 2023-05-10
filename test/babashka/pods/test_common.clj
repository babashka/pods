(ns babashka.pods.test-common
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [is]]))

(def test-program (slurp (io/file "test-resources" "test_program.clj")))

(defn assertions [out err ret]
  ;; (.println System/err ret)
  ;; (.println System/err out)
  ;; (.println System/err err)
  (doseq [[expected actual]
          (map vector (replace
                       {::edn-error (if (= "edn"
                                           (System/getenv "BABASHKA_POD_TEST_FORMAT"))
                                      "Map literal must contain an even number of forms"
                                      ::dont-care)}
                       '["pod.test-pod"
                         pod.test-pod
                         {:a 1, :b 2}
                         6
                         3
                         [1 2 3 4 5 6 7 8 9]
                         #"Illegal arguments / \{:args [\(\[]1 2 3[\)\]]\}"
                         nil
                         3
                         #"cast"
                         {:args ["1" 2]}
                         true
                         9
                         [1 2 3]
                         [[1] [1]]
                         2
                         3
                         true ;; local-date
                         true ;; roundtrip string array
                         true ;; roundtrip metadata
                         true ;; roundtrip metadata nested
                         true ;; dont roundtrip metadata (when arg-meta "false"/ absent)
                         1
                         "add the arguments"
                         nil
                         nil
                         ::edn-error])
               (concat ret (repeat ::nil)))]
    (cond (instance? java.util.regex.Pattern expected)
          (is (re-find expected actual))
          (= ::dont-care expected) nil
          :else
          (is (= expected actual))))
  (is (= "(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n:foo\n:foo\n" (str out)))
  (is (str/starts-with? (str err) "(\"hello\" \"print\" \"this\" \"error\")" )))

(def pod-registry (slurp (io/file "test-resources" "pod_registry.clj")))
