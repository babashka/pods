(ns babashka.pods.impl-test
  (:require [clojure.test :refer :all]
            [babashka.pods.impl :refer :all]))

(deftest load-pod-test
  (testing "resolve fn gets called when pod has EDN data readers"
    (let [resolved? (atom false)
          test-resolve (fn [_sym]
                         (reset! resolved? true))
          pod (load-pod ["clojure" "-M:test-pod"] {:resolve test-resolve})]
      (is @resolved?)
      (.destroy ^Process (:process pod)))))

(deftest invoke-pod-test
  (testing "invoke state (chan) being cleaned when done"
    (let [pod (load-pod ["clojure" "-M:test-pod"] {:resolve identity})
          _ (future (processor pod))]
      (is (= 6 (invoke pod 'pod.test-pod/add-sync [4 2] {})))
      (is (empty? @(:chans pod)))
      (.destroy ^Process (:process pod))))

  (testing "unfulfilled invokes throw on pod quitting"
    (let [pod (load-pod ["clojure" "-M:test-pod"] {:resolve identity})
          _ (future (processor pod))]
      (try (invoke pod 'pod.test-pod/exit-1 [] {})
           (catch Throwable e
             (is (= "EOF" (ex-message e)))))
      (is (empty? @(:chans pod)))
      (.destroy ^Process (:process pod)))))
