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
  (copy-var add-transit-read-handler! jvm/add-transit-read-handler!)
  (copy-var add-transit-write-handler! jvm/add-transit-write-handler!)
  (copy-var set-default-transit-write-handler! jvm/set-default-transit-write-handler!))
