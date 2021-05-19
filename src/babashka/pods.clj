(ns babashka.pods
  (:require [babashka.pods.jvm :as jvm]))

(defn load-pod
  ([pod-spec] (load-pod pod-spec nil))
  ([pod-spec version opts]
   (load-pod pod-spec (assoc opts :version version)))
  ([pod-spec opts] (jvm/load-pod pod-spec opts)))

(defn unload-pod
  ([pod-id-or-pod] (unload-pod pod-id-or-pod {}))
  ([pod-id-or-pod opts] (jvm/unload-pod pod-id-or-pod opts)))

(defn invoke
  ([pod-id-or-pod sym args] (invoke pod-id-or-pod sym args {}))
  ([pod-id-or-pod sym args opts] (jvm/invoke pod-id-or-pod sym args opts)))

(defn add-transit-read-handler! [tag fn]
  (jvm/add-transit-read-handler! tag fn))

(defn add-transit-write-handler! [tag fn classes]
  (jvm/add-transit-write-handler! tag fn classes))

(defn set-default-transit-write-handler! [tag-fn val-fn]
  (jvm/set-default-transit-write-handler! tag-fn val-fn))
