(ns babashka.pods.impl
  {:no-doc true}
  (:refer-clojure :exclude [read])
  (:require [babashka.pods.impl.resolver :as resolver]
            [bencode.core :as bencode]
            [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.transit :as transit])
  (:import [java.io PushbackInputStream]
           [java.net Socket]))

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

(defn next-id []
  (str (java.util.UUID/randomUUID)))

(def ^:dynamic *pod-id* nil)

(defonce transit-read-handlers (atom {}))
(defonce transit-read-handler-maps (atom {}))

(defn update-transit-read-handler-map []
  (swap! transit-read-handler-maps assoc *pod-id*
         (transit/read-handler-map (get @transit-read-handlers *pod-id*))))

(defn transit-json-read [pod-id ^String s]
  (with-open [bais (java.io.ByteArrayInputStream. (.getBytes s "UTF-8"))]
    (let [r (transit/reader bais :json {:handlers (get @transit-read-handler-maps pod-id)})]
      (transit/read r))))

;; https://www.cognitect.com/blog/2015/9/10/extending-transit
(defn add-transit-read-handler!
  ([tag fn]
   (let [rh (transit/read-handler fn)]
     (swap! transit-read-handlers assoc-in [*pod-id* tag] rh)
     (update-transit-read-handler-map)
     nil)))

(defonce transit-write-handlers (atom {}))
(defonce transit-write-handler-maps (atom {}))

(defn update-transit-write-handler-map []
  (swap! transit-write-handler-maps assoc *pod-id*
         (transit/write-handler-map (get @transit-write-handlers *pod-id*))))

;; https://www.cognitect.com/blog/2015/9/10/extending-transit
(defn add-transit-write-handler!
  [classes tag fn]
  (let [rh (transit/write-handler tag fn)]
    (doseq [class classes]
      (swap! transit-write-handlers assoc-in [*pod-id* class] rh)))
  (update-transit-write-handler-map)
  nil)

(defonce transit-default-write-handlers (atom {}))

(defn set-default-transit-write-handler! [tag-fn val-fn]
  (let [wh (transit/write-handler tag-fn val-fn)]
    (swap! transit-default-write-handlers assoc *pod-id* wh)))

(defn transit-json-write [pod-id ^String s]
  (with-open [baos (java.io.ByteArrayOutputStream. 4096)]
    (let [w (transit/writer baos :json {:handlers (get @transit-write-handler-maps pod-id)
                                        :default-handler (get @transit-default-write-handlers pod-id)})]
      (transit/write w s)
      (str baos))))

(defn invoke [pod pod-var args opts]
  (let [handlers (:handlers opts)
        stream (:stdin pod)
        format (:format pod)
        chans (:chans pod)
        write-fn (case format
                   :edn pr-str
                   :json cheshire/generate-string
                   :transit+json #(transit-json-write (:pod-id pod) %))
        id (next-id)
        chan (if handlers handlers
                 (promise))
        _ (swap! chans assoc id chan)
        _ (write stream {"id" id
                         "op" "invoke"
                         "var" (str pod-var)
                         "args" (write-fn args)})]
    (cond handlers nil
          :else (let [v @chan]
                  (if (instance? Throwable v)
                    (throw v)
                    v)))))

(defn bencode->vars [pod ns-name-str vars]
  (mapv
   (fn [var]
     (let [name (get-string var "name")
           async? (some-> (get var "async")
                          bytes->string
                          #(Boolean/parseBoolean %))
           name-sym (symbol name)
           sym (symbol ns-name-str name)
           code (get-maybe-string var "code")
           vmeta (some-> (get var "meta")
                         bytes->string
                         edn/read-string)
           name-sym (if vmeta
                      (with-meta name-sym vmeta)
                      name-sym)]
       [name-sym
        (or code
            (fn [& args]
              (let [res (invoke pod sym args {:async async?})]
                res)))]))
   vars))

(defn processor [pod]
  (let [stdout (:stdout pod)
        format (:format pod)
        chans (:chans pod)
        out-stream (:out pod)
        err-stream (:err pod)
        readers (:readers pod)
        read-fn (case format
                  :edn (fn [s]
                         (try (edn/read-string {:readers readers} s)
                              (catch Exception e
                                (binding [*out* *err*]
                                  (println "Cannot read EDN: " (pr-str s))
                                  (throw e)))))
                  :json (fn [s]
                          (try (cheshire/parse-string-strict s true)
                               (catch Exception e
                                 (binding [*out* *err*]
                                   (println "Cannot read JSON: " (pr-str s))
                                   (throw e)))))
                  :transit+json
                  (fn [s]
                    (try (transit-json-read (:pod-id pod) s)
                         (catch Exception e
                           (binding [*out* *err*]
                             (println "Cannot read Transit JSON: " (pr-str s))
                             (throw e))))))]
    (binding [*pod-id* (:pod-id pod)]
      (try
        (loop []
          (let [reply (try (read stdout)
                           (catch java.io.EOFException _
                             ::EOF)
                           (catch java.net.SocketException e
                             (if (= "Socket closed" (ex-message e))
                               ::EOF
                               (throw e))))]
            (when-not (identical? ::EOF reply)
              (let [id (get reply "id")
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
                    namespace (when-let [v (get reply "vars")]
                                (let [name-str (-> (get reply "name")
                                                   bytes->string)
                                      name (symbol name-str)]
                                  {:name name
                                   :vars (bencode->vars pod name-str v)}))
                    chan (get @chans id)
                    promise? (instance? clojure.lang.IPending chan)
                    exception (when (and promise? error?)
                                (ex-info ex-message ex-data))
                    ;; NOTE: if we need more fine-grained handlers, we will add
                    ;; a :raw handler that will just get the bencode message's raw
                    ;; data
                    {error-handler :error
                     done-handler :done
                     success-handler :success} (when (map? chan)
                                                 chan)
                    out (some-> (get reply "out")
                                bytes->string)
                    err (some-> (get reply "err")
                                bytes->string)]
                ;; NOTE: write to out and err before delivering promise for making
                ;; listening to output synchronous.
                (when out
                  (binding [*out* out-stream]
                    (println out)))
                (when err (binding [*out* err-stream]
                            (println err)))
                (when (or value* error? namespace)
                  (cond promise?
                        (deliver chan (cond error? exception
                                            value value
                                            namespace namespace))
                        (and (not error?) success-handler)
                        (success-handler value)
                        (and error? error-handler)
                        (error-handler {:ex-message ex-message
                                        :ex-data ex-data})))
                (when (and done? (not error?))
                  (when promise?
                    (deliver chan nil))
                  (when done-handler
                    (done-handler))))
              (recur))))
        (catch Exception e
          (binding [*out* *err* #_err-stream]
            (prn e)))))))

(def pods (atom {}))

(defn get-pod-id [x]
  (if (map? x)
    (:pod/id x)
    x))

(defn lookup-pod [pod-id]
  (get @pods pod-id))

(defn destroy* [{:keys [:stdin :process :ops]}]
  (if (contains? ops :shutdown)
    (do (write stdin
               {"op" "shutdown"
                "id" (next-id)})
        (.waitFor ^Process process))
    (.destroy ^Process process)))

(defn destroy [pod-id-or-pod]
  (let [pod-id (get-pod-id pod-id-or-pod)]
    (when-let [pod (lookup-pod pod-id)]
      (destroy* pod)
      (when-let [rns (:remove-ns pod)]
        (doseq [[ns-name _] (:namespaces pod)]
          (rns ns-name))))
    (swap! pods dissoc pod-id)
    nil))

(def bytes->symbol
  (comp symbol bytes->string))

(defn read-readers [reply resolve-fn]
  (when-let [dict (get reply "readers")]
    (let [dict-keys (map symbol (keys dict))
          dict-vals (map (comp resolve-fn bytes->symbol) (vals dict))]
      (zipmap dict-keys dict-vals))))

(defn bencode->namespace [pod namespace]
  (let [name-str (-> namespace (get "name") bytes->string)
        name-sym (symbol name-str)
        vars (get namespace "vars")
        vars (bencode->vars pod name-str vars)
        defer? (some-> namespace (get-maybe-string "defer") (= "true"))]
    [name-sym vars defer?]))

(defn create-socket
  "Connect a socket to a remote host. The call blocks until
   the socket is connected."
  ^Socket
  [^String hostname ^Integer port]
  (Socket. hostname port))

(defn close-socket
  "Close the socket, and also closes its input and output streams."
  [^Socket socket]
  (try (.close socket)
       nil
       (catch java.net.SocketException _ nil)))

(defn port-file [pid]
  (io/file (str ".babashka-pod-" pid ".port")))

(defn read-port [^java.io.File port-file]
  (loop []
    (let [f port-file]
      (if-let [s (when (.exists f)
                   (let [s (slurp f)]
                     (when (str/ends-with? s "\n")
                       (str/trim s))))]
        (Integer/parseInt s)
        (recur)))))

(defn debug [& strs]
  (binding [*out* *err*]
    (println (str/join " " (map pr-str strs)))))

(defn resolve-pod [pod-spec {:keys [:version :path :force] :as opts}]
  (when (qualified-symbol? pod-spec)
    (when (and (not version) (not path))
      (throw (IllegalArgumentException. "Version or path must be provided")))
    (when (and version path)
      (throw (IllegalArgumentException. "You must provide either version or path, not both"))))
  (let [resolved (when (and (qualified-symbol? pod-spec) version)
                   (resolver/resolve pod-spec version force))
        opts (if resolved
               (if-let [extra-opts (:options resolved)]
                 (merge opts extra-opts)
                 opts)
               opts)
        pod-spec (cond
                   resolved [(:executable resolved)]
                   path [path]
                   (string? pod-spec) [pod-spec]
                   :else pod-spec)]
    {:pod-spec pod-spec, :opts opts}))

(defn run-pod [pod-spec {:keys [:transport] :as _opts}]
  (let [pb (ProcessBuilder. ^java.util.List pod-spec)
        socket? (identical? :socket transport)
        _ (if socket?
            (.inheritIO pb)
            (.redirectError pb java.lang.ProcessBuilder$Redirect/INHERIT))
        _ (cond-> (doto (.environment pb)
                    (.put "BABASHKA_POD" "true"))
                 socket? (.put "BABASHKA_POD_TRANSPORT" "socket"))
        p (.start pb)
        port-file (when socket? (port-file (.pid p)))
        socket-port (when socket? (read-port port-file))
        [socket stdin stdout]
        (if socket?
          (let [^Socket socket
                (loop []
                  (if-let [sock (try (create-socket "localhost" socket-port)
                                     (catch java.net.ConnectException _
                                       nil))]
                    sock
                    (recur)))]
            [socket
             (.getOutputStream socket)
             (PushbackInputStream. (.getInputStream socket))])
          [nil (.getOutputStream p) (java.io.PushbackInputStream. (.getInputStream p))])]
    {:process p
     :socket socket
     :stdin stdin
     :stdout stdout}))

(defn describe-pod [{:keys [:stdin :stdout]}]
  (write stdin {"op" "describe"
                "id" (next-id)})
  (read stdout))

(defn describe->ops [describe-reply]
  (some->> (get describe-reply "ops") keys (map keyword) set))

(defn describe->metadata [describe-reply resolve-fn]
  (let [format (-> (get describe-reply "format") bytes->string keyword)
        ops (describe->ops describe-reply)
        readers (when (identical? :edn format)
                  (read-readers describe-reply resolve-fn))]
    {:format format, :ops ops, :readers readers}))

(defn load-pod-metadata [pod-spec opts]
  (let [{:keys [:pod-spec :opts]} (resolve-pod pod-spec opts)
        running-pod (run-pod pod-spec opts)
        describe-reply (describe-pod running-pod)
        ops (describe->ops describe-reply)]
    (destroy* (assoc running-pod :ops ops))
    describe-reply))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec opts]
   (let [{:keys [:pod-spec :opts]} (resolve-pod pod-spec opts)
         {:keys [:remove-ns :resolve]} opts

         {p :process, stdin :stdin, stdout :stdout, socket :socket
          :as running-pod}
         (run-pod pod-spec opts)

         reply (or (:metadata opts)
                   (describe-pod running-pod))
         {:keys [:format :ops :readers]} (describe->metadata reply resolve)
         pod {:process p
              :pod-spec pod-spec
              :stdin stdin
              :stdout stdout
              :chans (atom {})
              :format format
              :ops ops
              :out *out*
              :err *err*
              :remove-ns remove-ns
              :readers readers}
         pod-namespaces (get reply "namespaces")
         pod-id (or (when-let [ns (first pod-namespaces)]
                      (get-string ns "name"))
                    (next-id))
         _ (add-shutdown-hook! #(do
                                  (destroy pod-id)
                                  (when socket
                                    ;; this probably isn't necessary because we
                                    ;; killed the process, but anyway
                                    (close-socket socket))))
         pod (assoc pod :pod-id pod-id)
         pod-namespaces (mapv #(bencode->namespace pod %)
                              pod-namespaces)
         pod (assoc pod :namespaces pod-namespaces)]
     (swap! pods assoc pod-id pod)
     pod)))

(defn load-ns [pod namespace]
  (let [prom (promise)
        chans (:chans pod)
        id (next-id)
        _ (swap! chans assoc id prom)]
    (write (:stdin pod)
           {"op" "load-ns"
            "ns" (str namespace)
            "id" id})
    @prom))

(defn invoke-public [pod-id fn-sym args opts]
  (let [pod-id (get-pod-id pod-id)
        pod (lookup-pod pod-id)]
    (invoke pod fn-sym args opts)))

(defn unload-pod [pod-id-or-pod]
  (destroy pod-id-or-pod))
