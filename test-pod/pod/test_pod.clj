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
                          "namespaces"
                          [{"name" "pod.test-pod"
                            "vars" [{"name" "add-sync"}
                                    {"name" "range-stream"
                                     "async" "true"}
                                    {"name" "assoc"}
                                    {"name" "error"}
                                    {"name" "print"}
                                    {"name" "print-err"}
                                    {"name" "return-nil"}
                                    {"name" "do-twice"
                                     "code" "(defmacro do-twice [x] `(do ~x ~x))"}]}]
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
                          pod.test-pod/add-sync (write
                                                 {"value" (write-fn (apply + args))
                                                  "id" id
                                                  "status" ["done"]})
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
                            "value" "nil"}))
                        (recur))
              :shutdown (System/exit 0))))))))

(defn -main [& args]
  (when (= "true" (System/getenv "BABASHKA_POD"))
    (run-pod (set args))))
