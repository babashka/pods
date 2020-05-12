(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]))

(def load-pod
  (with-meta
    (fn
      ([ctx pod-spec] (load-pod ctx pod-spec nil))
      ([ctx pod-spec _opts]
       (let [pod (binding [*out* @sci/out
                           *err* @sci/err]
                   (impl/load-pod pod-spec _opts))
             namespaces (:namespaces pod)
             env (:env ctx)]
         (swap! env update :namespaces merge namespaces)
         (sci/future (impl/processor pod))
         nil)))
    {:sci.impl/op :needs-ctx}))
