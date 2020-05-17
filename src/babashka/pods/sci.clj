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
         (doseq [[ns-name vars] namespaces
                 :let [sci-ns (sci/create-ns ns-name)]]
           (sci/binding [sci/ns sci-ns]
             (doseq [[var-name var-value] vars]
               (cond (ifn? var-value)
                     (swap! env assoc-in [:namespaces ns-name var-name] var-value)
                     (string? var-value)
                     (sci/eval-string* ctx var-value)))))
         (sci/future (impl/processor pod))
         nil)))
    {:sci.impl/op :needs-ctx}))
