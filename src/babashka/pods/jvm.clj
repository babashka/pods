(ns babashka.pods.jvm
  (:require [babashka.pods.impl :as impl]))

(def ^:private namespaces-to-load (atom {}))

(defn- unroot-resource [^String path]
  (symbol (.. path
              (substring 1)
              (replace \/ \. )
              (replace \_ \-))))

(defn- process-namespace [{:keys [:name :vars :done]}]
  (binding [*ns* (load-string (format "(ns %s) *ns*" name))]
    (doseq [[var-sym v] vars]
      (cond
        (ifn? v)
        (do
          (ns-unmap *ns* var-sym)
          (intern name var-sym v))
        (string? v)
        (load-string v))))
  (when done (deliver done :ok)))

(let [core-load clojure.core/load]
  (intern 'clojure.core 'load
          (fn [& paths]
            (let [nss @namespaces-to-load]
              (doseq [path paths]
                (let [lib (unroot-resource path)]
                  (if-let [pod (get nss lib)]
                    (impl/load-ns
                     pod lib (fn [namespace]
                               (process-namespace namespace)))
                    (core-load path))))))))

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
         namespaces (:namespaces pod)
         load? (contains? (:ops pod) :load)]
     (when load?
       (swap! namespaces-to-load
              merge
              (into {}
                    (keep (fn [[ns-name vars]]
                            (when (empty? vars)
                              [ns-name pod]))
                          namespaces))))
     (doseq [[ns-sym vars] namespaces
             :when (or (not load)
                       (seq vars))]
       (process-namespace {:name ns-sym :vars vars}))
     (future (impl/processor pod))
     {:pod/id (:pod-id pod)})))

(defn unload-pod
  ([pod-id] (unload-pod pod-id {}))
  ([pod-id _opts]
   (impl/unload-pod pod-id)))

(defn invoke
  ([pod-id sym args] (invoke pod-id sym args {}))
  ([pod-id sym args opts] (impl/invoke-public pod-id sym args opts)))
