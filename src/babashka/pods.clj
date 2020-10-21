(ns babashka.pods
  (:require [babashka.pods.jvm :as jvm]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec opts] (jvm/load-pod pod-spec opts)))

(defn unload-pod
  ([pod-id-or-pod] (unload-pod pod-id-or-pod {}))
  ([pod-id-or-pod opts] (jvm/unload-pod pod-id-or-pod opts)))

(defn invoke
  ([pod-id-or-pod sym args] (invoke pod-id-or-pod sym args {}))
  ([pod-id-or-pod sym args opts] (jvm/invoke pod-id-or-pod sym args opts)))
