(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod (impl/load-pod pod-spec _opts)
         namespaces (:namespaces pod)]
     (doseq [[ns-sym v] namespaces]
       (binding [*ns* (load-string (format "(ns %s) *ns*" ns-sym))]
         (doseq [[var-sym v] v]
           (cond
             (ifn? v)
             (do
               (ns-unmap *ns* var-sym)
               (intern ns-sym var-sym v))
             (string? v)
             (load-string v)))))
     (future (impl/processor pod))
     nil)))
