(ns babashka.pods.sci
  (:require [babashka.pods.impl :as impl]
            [sci.core :as sci]
            [clojure.java.io :as io]
            [babashka.pods.impl.resolver :as resolver])
  (:import (java.io PushbackInputStream File)))

(set! *warn-on-reflection* true)

(defn- process-namespace [ctx {:keys [:name :vars]}]
  (let [env (:env ctx)
        ns-name name
        sci-ns (sci/create-ns (symbol ns-name))]
    (sci/binding [sci/ns sci-ns]
      ;; ensure ns map in ctx, see #20
      (swap! env update-in [:namespaces ns-name]
             (fn [ns-map]
               (if ns-map ns-map {:obj sci-ns})))
      (doseq [[var-name var-value :as var] vars]
        (cond (ifn? var-value)
              (swap! env assoc-in [:namespaces ns-name var-name]
                     (sci/new-var
                      (symbol (str ns-name) (str var-name)) var-value (meta var-name)))
              (string? var-value)
              (sci/eval-string* ctx var-value))))))

(defn metadata-cache-file ^File [^File bb-edn-file pod-spec {:keys [:version :path]}]
  (if version
    (io/file (resolver/cache-dir {:pod/name pod-spec :pod/version version})
             "metadata.cache")
    (let [config-dir (.getParentFile bb-edn-file)
          cache-dir (io/file config-dir ".babashka")
          pod-file (-> path io/file .getName)
          cache-file (io/file cache-dir (str pod-file ".metadata.cache"))]
      cache-file)))

(defn load-metadata-from-cache [bb-edn-file pod-spec opts]
  (let [cache-file (metadata-cache-file bb-edn-file pod-spec opts)]
    (when (.exists cache-file)
      (with-open [r (PushbackInputStream. (io/input-stream cache-file))]
        (impl/read r)))))

(defn load-pod-metadata* [bb-edn-file pod-spec {:keys [:version :cache] :as opts}]
  (let [metadata (impl/load-pod-metadata pod-spec opts)
        cache-file (when cache (metadata-cache-file bb-edn-file pod-spec opts))]
    (when cache-file
      (io/make-parents cache-file)
      (with-open [w (io/output-stream cache-file)]
        (impl/write w metadata)))
    metadata))

(defn load-pod-metadata
  ([pod-spec opts] (load-pod-metadata nil pod-spec opts))
  ([bb-edn-file pod-spec {:keys [:cache] :as opts}]
   (let [metadata
         (if-let [cached-metadata (when cache
                                    (load-metadata-from-cache bb-edn-file
                                                              pod-spec
                                                              opts))]
           cached-metadata
           (load-pod-metadata* bb-edn-file pod-spec opts))]
     (reduce
       (fn [pod-namespaces ns]
         (let [ns-sym (-> ns (get "name") impl/bytes->string symbol)]
           (assoc pod-namespaces ns-sym {:pod-spec pod-spec
                                         :opts (assoc opts :metadata metadata)})))
       {} (get metadata "namespaces")))))

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
