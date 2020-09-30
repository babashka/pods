(ns pod.test-pod
  (:refer-clojure :exclude [read read-string])
  (:require [bencode.core :as bencode]
            [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io PushbackInputStream])
  (:gen-class))

(def debug? false)

(defn debug [& args]
  (when debug?
    (binding [*out* (io/writer "/tmp/log.txt" :append true)]
      (apply println args))))

(def stdin (PushbackInputStream. System/in))

(defn write [v]
  (bencode/write-bencode System/out v)
  (.flush System/out))

(defn read-string [^"[B" v]
  (String. v))

(defn read []
  (bencode/read-bencode stdin))

(def dependents
  (for [i (range 10)]
    {"name" (str "x" i)
     "code"
     (if-not (zero? i)
       (format "(def x%s (inc x%s))" i (dec i))
       "(def x0 0)")}))

(defn run-pod [cli-args]
  (let [format (if (contains? cli-args "--json")
                 :json
                 :edn)
        write-fn (if (identical? :json format)
                   cheshire/generate-string
                   pr-str)
        read-fn (if (identical? :json format)
                  #(cheshire/parse-string % true)
                  edn/read-string)]
    (try
      (loop []
        (let [message (try (read)
                           (catch java.io.EOFException _
                             ::EOF))]
          (when-not (identical? ::EOF message)
            (let [op (get message "op")
                  op (read-string op)
                  op (keyword op)]
              (case op
                :describe
                (do (write {"format" (if (= format :json)
                                       "json"
                                       "edn")
                            "readers" {"my/tag" "identity"
                                       ;; NOTE: this function is defined later,
                                       ;; which should be supported
                                       "my/other-tag" "pod.test-pod/read-other-tag"}
                            "namespaces"
                            [{"name" "pod.test-pod"
                              "vars" (into [{"name" "add-sync"}
                                            {"name" "range-stream"
                                             "async" "true"}
                                            {"name" "assoc"}
                                            {"name" "error"}
                                            {"name" "print"}
                                            {"name" "print-err"}
                                            {"name" "return-nil"}
                                            {"name" "do-twice"
                                             "code" "(defmacro do-twice [x] `(do ~x ~x))"}
                                            {"name" "fn-call"
                                             "code" "(defn fn-call [f x] (f x))"}
                                            {"name" "reader-tag"}
                                            ;; returns thing with other tag
                                            {"name" "other-tag"}
                                            ;; reads thing with other tag
                                            {"name" "read-other-tag"
                                             "code" "(defn read-other-tag [x] [x x])"}]
                                           dependents)}
                             {"name" "pod.test-pod.loaded"
                              "defer" "true"}
                             {"name" "pod.test-pod.loaded2"
                              "defer" "true"}
                             {"name" "pod.test-pod.only-code"
                              "vars" [{"name" "foo"
                                       "code" "(defn foo [] 1)"}]}]
                            "ops" {"shutdown" {}}})
                    (recur))
                :invoke (let [var (-> (get message "var")
                                      read-string
                                      symbol)
                              _ (debug "var" var)
                              id (-> (get message "id")
                                     read-string)
                              args (get message "args")
                              args (read-string args)
                              args (read-fn args)]
                          (case var
                            pod.test-pod/add-sync
                            (try (let [ret (apply + args)]
                                   (write
                                    {"value" (write-fn ret)
                                     "id" id
                                     "status" ["done"]}))
                                 (catch Exception e
                                   (write
                                    {"ex-data" (write-fn {:args args})
                                     "ex-message" (.getMessage e)
                                     "status" ["done" "error"]
                                     "id" id})))
                            pod.test-pod/range-stream
                            (let [rng (apply range args)]
                              (doseq [v rng]
                                (write
                                 {"value" (write-fn v)
                                  "id" id})
                                (Thread/sleep 100))
                              (write
                               {"status" ["done"]
                                "id" id}))
                            pod.test-pod/assoc
                            (write
                             {"value" (write-fn (apply assoc args))
                              "status" ["done"]
                              "id" id})
                            pod.test-pod/error
                            (write
                             {"ex-data" (write-fn {:args args})
                              "ex-message" (str "Illegal arguments")
                              "status" ["done" "error"]
                              "id" id})
                            pod.test-pod/print
                            (do (write
                                 {"out" (pr-str args)
                                  "id" id})
                                (write
                                 {"status" ["done"]
                                  "id" id}))
                            pod.test-pod/print-err
                            (do (write
                                 {"err" (pr-str args)
                                  "id" id})
                                (write
                                 {"status" ["done"]
                                  "id" id}))
                            pod.test-pod/return-nil
                            (write
                             {"status" ["done"]
                              "id" id
                              "value" "nil"})
                            pod.test-pod/reader-tag
                            (write
                             {"status" ["done"]
                              "id" id
                              "value" "#my/tag[1 2 3]"})
                            pod.test-pod/other-tag
                            (write
                             {"status" ["done"]
                              "id" id
                              "value" "#my/other-tag[1]"}))
                          (recur))
                :shutdown (System/exit 0)
                :load-ns (let [ns (-> (get message "ns")
                                      read-string
                                      symbol)
                               id (-> (get message "id")
                                      read-string)]
                           (case ns
                             pod.test-pod.loaded
                             (write
                              {"status" ["done"]
                               "id" id
                               "name" "pod.test-pod.loaded"
                               "vars" [{"name" "loaded"
                                        "code" "(defn loaded [x] (inc x))"}]})
                             pod.test-pod.loaded2
                             (write
                              {"status" ["done"]
                               "id" id
                               "name" "pod.test-pod.loaded2"
                               "vars" [{"name" "x"
                                        "code" "(require '[pod.test-pod.loaded :as loaded])"}
                                       {"name" "loaded"
                                        "code" "(defn loaded [x] (loaded/loaded x))"}]}))
                           (recur)))))))
      (catch Exception e
        (binding [*out* *err*]
          (prn e))))))

(defn -main [& args]
  (when (= "true" (System/getenv "BABASHKA_POD"))
    (run-pod (set args))))
