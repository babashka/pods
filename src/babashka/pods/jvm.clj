(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod (impl/load-pod
              pod-spec
              {:remove-ns remove-ns
               :resolve (fn [sym]
                          (or (resolve sym)
                              (intern
                               (create-ns (symbol (namespace sym)))
                               (symbol (name sym)))))})
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
     {:pod/id (:pod-id pod)})))

(defn unload-pod
  ([pod-id] (unload-pod pod-id {}))
  ([pod-id _opts]
   (impl/unload-pod pod-id)))

(defn invoke
  ([pod-id sym args] (invoke pod-id sym args {}))
  ([pod-id sym args opts] (impl/invoke-public pod-id sym args opts)))
