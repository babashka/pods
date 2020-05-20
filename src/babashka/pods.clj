(ns babashka.pods
  (:require [babashka.pods.jvm :as jvm]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec opts] (jvm/load-pod pod-spec opts)))

(defn invoke [pod-id sym args opts]
  (jvm/invoke pod-id sym args opts))
