(ns babashka.pods.jvm-test
  (:require [babashka.pods.test-common :refer [test-program]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(deftest jvm-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (binding [*out* out
                      *err* err]
              (try (load-string
                    (str/replace test-program "babashka.pods" "babashka.pods.jvm"))
                   (catch Exception e (prn e))))]

    (is (= '[{:a 1, :b 2}
             6
             [1 2 3 4 5 6 7 8 9]
             "Illegal arguments / {:args (1 2 3)}"] ret))
    (is (= "(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n" (str out)))
    (is (= "WARNING: assoc already refers to: #'clojure.core/assoc in namespace: pod.test-pod, being replaced by: #'pod.test-pod/assoc\nWARNING: print already refers to: #'clojure.core/print in namespace: pod.test-pod, being replaced by: #'pod.test-pod/print\n(\"hello\" \"print\" \"this\" \"error\")\n" (str err)))))
