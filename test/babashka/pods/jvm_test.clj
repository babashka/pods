(ns babashka.pods.jvm-test
  (:require [babashka.pods.test-common :refer [test-program]]
            [clojure.test :refer [deftest is]]))

(deftest jvm-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ret (binding [*out* out
                      *err* err]
              (try (load-string
                    test-program)
                   (catch Exception e (prn e))))]

    (is (= '[{:a 1, :b 2}
             6
             [1 2 3 4 5 6 7 8 9]
             "Illegal arguments / {:args (1 2 3)}"] ret))
    (is (= "(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n" (str out)))
    (is (= "(\"hello\" \"print\" \"this\" \"error\")\n" (str err)))))
