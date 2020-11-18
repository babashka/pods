(ns babashka.pods.sci-test
  (:require [babashka.pods.sci :as pods]
            [babashka.pods.test-common :refer [test-program assertions]]
            [clojure.test :refer [deftest]]
            [sci.core :as sci]))

(deftest sci-test
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        ctx-ref (volatile! nil)
        ctx (sci/init {:namespaces {'babashka.pods
                                    {'load-pod (fn [& args]
                                                 (apply pods/load-pod @ctx-ref args))
                                     'invoke pods/invoke
                                     'unload-pod pods/unload-pod}}
                       :classes {'System System}})
        _ (vreset! ctx-ref ctx)
        ret (sci/binding [sci/out out
                          sci/err err]
              (sci/eval-string* ctx test-program))]
    (assertions out err ret)))
