(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(def ^:private namespaces-to-load (atom {}))

(defn- unroot-resource [^String path]
  (symbol (.. path
              (substring 1)
              (replace \/ \.)
              (replace \_ \-))))

(defn- process-namespace [{:keys [:name :vars]}]
  (binding [*ns* (load-string (format "(ns %s) *ns*" name))]
    (doseq [[var-sym v] vars]
      (when-let [maybe-core (some-> (ns-resolve *ns* var-sym) meta :ns str symbol)]
        (when (= 'clojure.core maybe-core)
          (ns-unmap *ns* var-sym)))
      (cond
        (ifn? v)
        (intern name var-sym v)
        (string? v)
        (load-string v)))))

(let [core-load clojure.core/load]
  (intern 'clojure.core 'load
          (fn [& paths]
            (let [nss @namespaces-to-load]
              (doseq [path paths]
                (let [lib (unroot-resource path)]
                  (if-let [pod (get nss lib)]
                    (let [ns (impl/load-ns pod lib)]
                      (process-namespace ns))
                    (core-load path))))))))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec version opts] (load-pod pod-spec (assoc opts :version version)))
  ([pod-spec opts]
   (let [opts (if (string? opts)
                {:version opts}
                opts)
         pod (impl/load-pod
              pod-spec
              (merge {:remove-ns remove-ns
                      :resolve (fn [sym]
                                 (or (resolve sym)
                                     (intern
                                      (create-ns (symbol (namespace sym)))
                                      (symbol (name sym)))))}
                     opts))
         namespaces (:namespaces pod)]
     (swap! namespaces-to-load
            merge
            (into {}
                  (keep (fn [[ns-name _ defer?]]
                          (when defer?
                            [ns-name pod]))
                        namespaces)))
     (binding [impl/*pod-id* (:pod-id pod)]
       (doseq [[ns-sym vars lazy?] namespaces
               :when (not lazy?)]
         (process-namespace {:name ns-sym :vars vars})))
     (future (impl/processor pod))
     {:pod/id (:pod-id pod)})))

(defn unload-pod
  ([pod-id] (unload-pod pod-id {}))
  ([pod-id _opts]
   (impl/unload-pod pod-id)))

(defn invoke
  ([pod-id sym args] (invoke pod-id sym args {}))
  ([pod-id sym args opts] (impl/invoke-public pod-id sym args opts)))

(defmacro copy-var [name var]
  `(do (def ~name ~var)
       (let [m# (meta (var ~var))
             doc# (:doc m#)
             arglists# (:arglists m#)]
         (alter-meta! (var ~name) assoc
                      :arglists arglists#
                      :doc doc#))))

#_:clj-kondo/ignore
(do
  (copy-var add-transit-read-handler! impl/add-transit-read-handler!)
  (copy-var add-transit-write-handler! impl/add-transit-write-handler!)
  (copy-var set-default-transit-write-handler! impl/set-default-transit-write-handler!))
