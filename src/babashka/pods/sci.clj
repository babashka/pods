(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]))

(defn process-namespace [ctx {:keys [:name :vars]}]
  (let [env (:env ctx)
        ns-name name
        sci-ns (sci/create-ns ns-name)]
    (sci/binding [sci/ns sci-ns]
      (doseq [[var-name var-value] vars]
        (cond (ifn? var-value)
              (swap! env assoc-in [:namespaces ns-name var-name]
                     (sci/new-var
                      (symbol (str ns-name) (str var-name)) var-value))
              (string? var-value)
              (do
                (prn "eval" @sci/ns var-value)
                (sci/eval-string* ctx var-value)))))))

(def load-pod
  (with-meta
    (fn
      ([ctx pod-spec] (load-pod ctx pod-spec nil))
      ([ctx pod-spec _opts]
       (let [env (:env ctx)
             pod (binding [*out* @sci/out
                           *err* @sci/err]
                   (impl/load-pod
                    pod-spec
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
                               v))))}))
             namespaces (:namespaces pod)
             load? (contains? (:ops pod) :load)
             namespaces-to-load (when load?
                                  (set (keep (fn [[ns-name vars]]
                                               (when (empty? vars)
                                                 ns-name))
                                               namespaces)))]
         (when (seq namespaces-to-load)
           (let [load-fn (fn load-fn [{:keys [:namespace]}]
                           (when (contains? namespaces-to-load namespace)
                             (impl/load-ns
                              pod namespace (fn [namespace]
                                              (process-namespace ctx namespace)))
                             ""))
                 prev-load-fn (:load-fn @env)
                 new-load-fn (fn [m]
                               (or (load-fn m)
                                   (when prev-load-fn
                                     (prev-load-fn m))))]
             (swap! env assoc :load-fn new-load-fn)))
         (doseq [[ns-name vars] namespaces
                 :when (or (not load)
                           (seq vars))]
           (process-namespace ctx {:name ns-name :vars vars}))
         (sci/future (impl/processor pod))
         {:pod/id (:pod-id pod)})))
    {:sci.impl/op :needs-ctx}))

(defn unload-pod
  ([pod-id] (unload-pod pod-id {}))
  ([pod-id _opts]
   (impl/unload-pod pod-id)))

(defn invoke
  ([pod-id sym args] (invoke pod-id sym args {}))
  ([pod-id sym args opts] (impl/invoke-public pod-id sym args opts)))
