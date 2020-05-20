(ns babashka.pods.impl
  {:no-doc true}
  (:refer-clojure :exclude [read])
  (:require [bencode.core :as bencode]
            [cheshire.core :as cheshire]
            [clojure.core.async :as async]
            [clojure.edn :as edn]))

(set! *warn-on-reflection* true)

(defn add-shutdown-hook! [^Runnable f]
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. f))))

(defn write [^java.io.OutputStream stream v]
  (locking stream
    (bencode/write-bencode stream v)
    (.flush stream)))

(defn read [stream]
  (bencode/read-bencode stream))

(defn bytes->string [^"[B" bytes]
  (String. bytes))

(defn get-string [m k]
  (-> (get m k)
      bytes->string))

(defn get-maybe-string [m k]
  (some-> (get m k)
          bytes->string))

(defn processor [pod]
  (let [stdout (:stdout pod)
        format (:format pod)
        chans (:chans pod)
        out-stream (:out pod)
        err-stream (:err pod)
        read-fn (case format
                  :edn edn/read-string
                  :json #(cheshire/parse-string-strict % true))]
    (try
      (loop []
        (let [reply (try (read stdout)
                         (catch java.io.EOFException _
                           ::EOF))]
          (when-not (identical? ::EOF reply)
            (let [id    (get reply "id")
                  id    (bytes->string id)
                  value* (find reply "value")
                  value (some-> value*
                                second
                                bytes->string
                                read-fn)
                  status (get reply "status")
                  status (set (map (comp keyword bytes->string) status))
                  error? (contains? status :error)
                  done? (or error? (contains? status :done))
                  [ex-message ex-data]
                  (when error?
                    [(or (some-> (get reply "ex-message")
                                 bytes->string)
                         "")
                     (or (some-> (get reply "ex-data")
                                 bytes->string
                                 read-fn)
                         {})])
                  chan (get @chans id)
                  promise? (instance? clojure.lang.IPending chan)
                  exception (when (and promise? error?)
                              (ex-info ex-message ex-data))
                  {error-handler :error
                   done-handler :done
                   success-handler :success} (when (map? chan)
                                                    chan)
                  out (some-> (get reply "out")
                              bytes->string)
                  err (some-> (get reply "err")
                              bytes->string)]
              (when (or value* error?)
                (cond promise?
                      (deliver chan (if error? exception value))
                      (and (not error?) success-handler)
                      (success-handler {:value value})
                      (and error? error-handler)
                      (error-handler {:ex-message ex-message
                                      :ex-data ex-data})))
              (when done?
                (when promise?
                  (deliver chan nil))
                (when done-handler
                  (done-handler {})))
              (when out
                (binding [*out* out-stream]
                  (println out)))
              (when err (binding [*out* err-stream]
                          (println err))))
            (recur))))
      (catch Exception e
        (binding [*out* err-stream]
          (prn e))))))

(defn next-id []
  (str (java.util.UUID/randomUUID)))

(defn invoke [pod pod-var args opts]
  (let [handlers (:handlers opts)
        stream (:stdin pod)
        format (:format pod)
        chans (:chans pod)
        write-fn (case format
                   :edn pr-str
                   :json cheshire/generate-string)
        id (next-id)
        chan (if handlers handlers
                 (promise))
        _ (swap! chans assoc id chan)
        _ (write stream {"id" id
                         "op" "invoke"
                         "var" (str pod-var)
                         "args" (write-fn args)})]
    ;; see: https://blog.jakubholy.net/2019/core-async-error-handling/
    (cond handlers handlers
          :else (let [v @chan]
                  (if (instance? Throwable v)
                    (throw v)
                    v)))))

(def pods (atom {}))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod-spec (if (string? pod-spec) [pod-spec] pod-spec)
         pb (ProcessBuilder. ^java.util.List pod-spec)
         _ (.redirectError pb java.lang.ProcessBuilder$Redirect/INHERIT)
         _ (doto (.environment pb)
             (.put "BABASHKA_POD" "true"))
         p (.start pb)
         stdin (.getOutputStream p)
         stdout (.getInputStream p)
         stdout (java.io.PushbackInputStream. stdout)
         _ (write stdin {"op" "describe"
                         "id" (next-id)})
         reply (read stdout)
         format (-> (get reply "format") bytes->string keyword)
         ops (some->> (get reply "ops") keys (map keyword) set)
         pod-id (get-maybe-string reply "pod-id")
         pod {:process p
              :pod-spec pod-spec
              :stdin stdin
              :stdout stdout
              :chans (atom {})
              :format format
              :ops ops
              :out *out*
              :err *err*}
         _ (add-shutdown-hook!
            (fn []
              (if (contains? ops :shutdown)
                (do (write stdin {"op" "shutdown"
                                  "id" (next-id)})
                    (.waitFor p))
                (.destroy p))))
         pod-namespaces (get reply "namespaces")
         pod-id (or pod-id (when-let [ns (first pod-namespaces)]
                             (get-string ns "name")))
         pod (assoc pod :pod-id pod-id)
         vars-fn (fn [ns-name-str vars]
                   (reduce
                    (fn [m var]
                      (let [name (get-string var "name")
                            async? (some-> (get var "async")
                                           bytes->string
                                           #(Boolean/parseBoolean %))
                            name-sym (symbol name)
                            sym (symbol ns-name-str name)
                            code (get-maybe-string var "code")]
                        (assoc m name-sym
                               (or code
                                   (fn [& args]
                                     (let [res (invoke pod sym args {:async async?})]
                                       res))))))
                    {}
                    vars))
         pod-namespaces (reduce (fn [namespaces namespace]
                                  (let [name-str (-> namespace (get "name") bytes->string)
                                        name-sym (symbol name-str)
                                        vars (get namespace "vars")
                                        vars (vars-fn name-str vars)]
                                    (assoc namespaces name-sym vars)))
                                {}
                                pod-namespaces)
         pod (assoc pod :namespaces pod-namespaces)]
     (swap! pods assoc pod-id pod)
     pod)))

(defn lookup-pod [pod-id]
  (get @pods pod-id))

(defn invoke-public [pod-id fn-sym args opts]
  (let [pod (lookup-pod pod-id)]
    {:result (invoke pod fn-sym args opts)}))
