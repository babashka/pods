(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod (impl/load-pod pod-spec _opts)
         namespaces (:namespaces pod)]
     (doseq [[ns-sym v] namespaces]
       (create-ns ns-sym)
       (doseq [[var-sym v] v]
         (intern ns-sym var-sym v)))
     (future (impl/processor pod)))))
