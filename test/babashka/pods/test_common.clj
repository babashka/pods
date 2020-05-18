(ns babashka.pods.test-common
  (:require [clojure.java.io :as io]
            [clojure.test :refer [is]]))

(def test-program (slurp (io/file "test-resources" "test_program.clj")))

(defn assertions [out err ret]
  (is (= '[{:a 1, :b 2}
           6
           [1 2 3 4 5 6 7 8 9]
           "Illegal arguments / {:args (1 2 3)}"
           nil] ret))
  (is (= "nil\n(\"hello\" \"print\" \"this\" \"debugging\" \"message\")\n:foo\n:foo\n" (str out)))
  (is (= "(\"hello\" \"print\" \"this\" \"error\")\n" (str err))))
