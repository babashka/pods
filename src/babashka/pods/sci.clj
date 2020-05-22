(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]))

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
             namespaces (:namespaces pod)]
         (doseq [[ns-name vars] namespaces
                 :let [sci-ns (sci/create-ns ns-name)]]
           (sci/binding [sci/ns sci-ns]
             (doseq [[var-name var-value] vars]
               (cond (ifn? var-value)
                     (swap! env assoc-in [:namespaces ns-name var-name]
                            (sci/new-var
                             (symbol (str ns-name) (str var-name)) var-value))
                     (string? var-value)
                     (sci/eval-string* ctx var-value)))))
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
