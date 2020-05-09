(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod (impl/load-pod pod-spec _opts)
         namespaces (:namespaces pod)]
     (doseq [[ns-sym v] namespaces]
       (load-string (format "(ns %s)" ns-sym))
       (doseq [[var-sym v] v]
         (ns-unmap ns-sym var-sym)
         (intern ns-sym var-sym v)))
     (future (impl/processor pod)))))
