(ns babashka.pods.impl-test
  (:require [clojure.test :refer :all]
            [babashka.pods.impl :refer :all]))

(deftest load-pod-test
  (testing "resolve fn gets called when pod has EDN data readers"
    (let [resolved? (atom false)
          test-resolve (fn [_sym]
                         (reset! resolved? true))]
      (load-pod ["clojure" "-M:test-pod"] {:resolve test-resolve})
      (is @resolved?))))
