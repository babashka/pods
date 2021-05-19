(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]))

(defn- process-namespace [ctx {:keys [:name :vars]}]
  (let [env (:env ctx)
        ns-name name
        sci-ns (sci/create-ns (symbol ns-name))]
    (sci/binding [sci/ns sci-ns]
      ;; ensure ns map in ctx, see #20
      (swap! env update-in [:namespaces ns-name]
             (fn [ns-map]
               (if ns-map ns-map {:obj sci-ns})))
      (doseq [[var-name var-value] vars]
        (cond (ifn? var-value)
              (swap! env assoc-in [:namespaces ns-name var-name]
                     (sci/new-var
                      (symbol (str ns-name) (str var-name)) var-value))
              (string? var-value)
              (sci/eval-string* ctx var-value))))))

(defn load-pod
  ([ctx pod-spec] (load-pod ctx pod-spec nil))
  ([ctx pod-spec version opts] (load-pod ctx pod-spec (assoc opts :version version)))
  ([ctx pod-spec opts]
   (let [opts (if (string? opts)
                {:version opts}
                opts)
         env (:env ctx)
         pod (binding [*out* @sci/out
                       *err* @sci/err]
               (impl/load-pod
                pod-spec
                (merge
                 {:remove-ns
                  (fn [sym]
                    (swap! env update :namespaces dissoc sym))
                  :resolve
                  (fn [sym]
                    (let [sym-ns (or (some-> (namespace sym)
                                             symbol)
                                     'clojure.core)
                          sym-name (symbol (name sym))]
                      (or (get-in @env [:namespaces sym-ns sym-name])
                          (let [v (sci/new-var sym {:predefined true})]
                            (swap! env assoc-in [:namespaces sym-ns sym-name]
                                   v)
                            v))))}
                 opts)))
         namespaces (:namespaces pod)
         namespaces-to-load (set (keep (fn [[ns-name _ defer?]]
                                         (when defer?
                                           ns-name))
                                       namespaces))]
     (when (seq namespaces-to-load)
       (let [load-fn (fn load-fn [{:keys [:namespace]}]
                       (when (contains? namespaces-to-load namespace)
                         (let [ns (impl/load-ns pod namespace)]
                           (process-namespace ctx ns))
                         {:file nil
                          :source ""}))
             prev-load-fn (:load-fn @env)
             new-load-fn (fn [m]
                           (or (load-fn m)
                               (when prev-load-fn
                                 (prev-load-fn m))))]
         (swap! env assoc :load-fn new-load-fn)))
     (binding [impl/*pod-id* (:pod-id pod)]
       (doseq [[ns-name vars lazy?] namespaces
               :when (not lazy?)]
         (process-namespace ctx {:name ns-name :vars vars})))
     (sci/future (impl/processor pod))
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
