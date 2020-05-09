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
        (let [reply (read stdout)
              id    (get reply "id")
              id    (bytes->string id)
              value* (find reply "value")
              value (some-> value*
                            second
                            bytes->string
                            read-fn)
              status (get reply "status")
              status (set (map (comp keyword bytes->string) status))
              done? (contains? status :done)
              error? (contains? status :error)
              value (if error?
                      (let [message (or (some-> (get reply "ex-message")
                                                bytes->string)
                                        "")
                            data (or (some-> (get reply "ex-data")
                                             bytes->string
                                             read-fn)
                                     {})]
                        (ex-info message data))
                      value)
              chan (get @chans id)
              out (some-> (get reply "out")
                          bytes->string)
              err (some-> (get reply "err")
                          bytes->string)]
          (when (or value* error?) (async/put! chan value))
          (when (or done? error?) (async/close! chan))
          (when out
            (binding [*out* out-stream]
              (println out)))
          (when err (binding [*out* err-stream]
                      (println err))))
        (recur))
      (catch Exception e
        (binding [*out* err-stream]
          (prn e))))))

(defn next-id []
  (str (java.util.UUID/randomUUID)))

(defn invoke [pod pod-var args async?]
  (let [stream (:stdin pod)
        format (:format pod)
        chans (:chans pod)
        write-fn (case format
                   :edn pr-str
                   :json cheshire/generate-string)
        id (next-id)
        chan (async/chan)
        _ (swap! chans assoc id chan)
        _ (write stream {"id" id
                         "op" "invoke"
                         "var" (str pod-var)
                         "args" (write-fn args)})]
    (if async? chan ;; TODO: https://blog.jakubholy.net/2019/core-async-error-handling/
        (let [v (async/<!! chan)]
          (if (instance? Throwable v)
            (throw v)
            v)))))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec _opts]
   (let [pod-spec (if (string? pod-spec) [pod-spec] pod-spec)
         pb (ProcessBuilder. ^java.util.List pod-spec)
         _ (.redirectError pb java.lang.ProcessBuilder$Redirect/INHERIT)
         p (.start pb)
         stdin (.getOutputStream p)
         stdout (.getInputStream p)
         stdout (java.io.PushbackInputStream. stdout)
         _ (write stdin {"op" "describe"
                         "id" (next-id)})
         reply (read stdout)
         format (-> (get reply "format") bytes->string keyword)
         ops (some->> (get reply "ops") keys (map keyword) set)
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
         vars-fn (fn [ns-name-str vars]
                   (reduce
                    (fn [m var]
                      (let [name (get-string var "name")
                            async? (some-> (get var "async")
                                           bytes->string
                                           #(Boolean/parseBoolean %))
                            name-sym (symbol name)
                            sym (symbol ns-name-str name)]
                        (assoc m name-sym (fn [& args]
                                            (let [res (invoke pod sym args async?)]
                                              res)))))
                    {}
                    vars))
         pod-namespaces (reduce (fn [namespaces namespace]
                                  (let [name-str (-> namespace (get "name") bytes->string)
                                        name-sym (symbol name-str)
                                        vars (get namespace "vars")
                                        vars (vars-fn name-str vars)]
                                    (assoc namespaces name-sym vars)))
                                {}
                                pod-namespaces)]
     (assoc pod :namespaces pod-namespaces))))
