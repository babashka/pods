(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]))

(defn load-pod
  ([ctx pod-spec] (load-pod ctx pod-spec nil))
  ([ctx pod-spec _opts]
   (let [pod (impl/load-pod pod-spec _opts)
         namespaces (:namespaces pod)
         env (:env ctx)]
     (swap! env
            (fn [env]
              update env :namespaces merge namespaces))
     (sci/future (impl/processor pod)))))

