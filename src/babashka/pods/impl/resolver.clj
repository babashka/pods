(ns babashka.pods.impl.resolver
  {:no-doc true}
  (:refer-clojure :exclude [resolve])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str])
  (:import [java.net URL HttpURLConnection]
           [java.nio.file Files]))

(set! *warn-on-reflection* true)

(defn normalize-arch [arch]
  (if (= "amd64" arch)
    "x86_64"
    arch))

(defn normalize-os [os]
  (-> os str/lower-case (str/replace #"\s+" "_")))

(def os
  (delay
    {:os/name (or (System/getenv "BABASHKA_PODS_OS_NAME")
                  (System/getProperty "os.name"))
     :os/arch (let [arch (or (System/getenv "BABASHKA_PODS_OS_ARCH")
                             (System/getProperty "os.arch"))]
                (normalize-arch arch))}))

(defn warn [& strs]
  (binding [*out* *err*]
    (apply println strs)))

(defn match-artifacts
  ([package] (match-artifacts package (:os/arch @os)))
  ([package arch]
   (let [artifacts (:pod/artifacts package)
         res (filter (fn [{os-name :os/name
                           os-arch :os/arch}]
                       (let [os-arch (normalize-arch os-arch)]
                         (and (re-matches (re-pattern os-name) (:os/name @os))
                              (re-matches (re-pattern os-arch)
                                          arch))))
                     artifacts)]
     (if (empty? res)
       (if (and (= "Mac OS X" (:os/name @os))
                (= "aarch64" (:os/arch @os)))
         ;; Rosetta2 fallback on Apple M1 machines
         (match-artifacts package "x86_64")
         (throw (IllegalArgumentException. (format "No executable found for pod %s (%s) and OS %s/%s"
                                                   (:pod/name package)
                                                   (:pod/version package)
                                                   (:os/name @os)
                                                   (:os/arch @os)))))
       res))))

(defn unzip [{:keys [^java.io.File zip-file
                     ^java.io.File destination-dir
                     verbose]}]
  (when verbose (warn "Unzipping" (.getPath zip-file) "to" (.getPath destination-dir)))
  (let [output-path (.toPath destination-dir)
        zip-file (io/file zip-file)
        _ (.mkdirs (io/file destination-dir))]
    (with-open
      [fis (Files/newInputStream (.toPath zip-file) (into-array java.nio.file.OpenOption []))
       zis (java.util.zip.ZipInputStream. fis)]
      (loop []
        (let [entry (.getNextEntry zis)]
          (when entry
            (let [entry-name (.getName entry)
                  new-path (.resolve output-path entry-name)]
              (if (.isDirectory entry)
                (Files/createDirectories new-path ^"[Ljava.nio.file.attribute.FileAttribute;"
                                         (into-array java.nio.file.attribute.FileAttribute []))
                (Files/copy ^java.io.InputStream zis
                            new-path
                            ^"[Ljava.nio.file.CopyOption;"
                            (into-array
                             java.nio.file.CopyOption
                             [java.nio.file.StandardCopyOption/REPLACE_EXISTING]))))
            (recur)))))))

(defn un-tgz [^java.io.File zip-file ^java.io.File destination-dir verbose?]
  (when verbose? (warn "Unzipping" (.getPath zip-file) "to" (.getPath destination-dir)))
  (let [tmp-file (java.io.File/createTempFile "glam" ".tar")
        output-path (.toPath tmp-file)]
    (with-open
      [fis (Files/newInputStream (.toPath zip-file) (into-array java.nio.file.OpenOption []))
       zis (java.util.zip.GZIPInputStream. fis)]
      (Files/copy ^java.io.InputStream zis
                  output-path
                  ^"[Ljava.nio.file.CopyOption;"
                  (into-array
                   [java.nio.file.StandardCopyOption/REPLACE_EXISTING])))
    (.mkdirs destination-dir)
    (let [res (sh "tar" "xf" (.getPath tmp-file) "--directory" (.getPath destination-dir))]
      (when-not (zero? (:exit res))
        (throw (ex-info (:err res) res))))
    (.delete tmp-file)))

(defn make-executable [dest-dir executables verbose?]
  (doseq [e executables]
    (let [f (io/file dest-dir e)]
      (when verbose? (warn "Making" (.getPath f) "executable."))
      (.setExecutable f true))))

(defn download [source ^java.io.File dest verbose?]
  (when verbose? (warn "Downloading" source "to" (.getPath dest)))
  (let [source (URL. source)
        dest (io/file dest)
        conn ^HttpURLConnection (.openConnection ^URL source)]
    (.setInstanceFollowRedirects conn true)
    (.connect conn)
    (io/make-parents dest)
    (with-open [is (.getInputStream conn)]
      (io/copy is dest))))

(defn repo-dir []
  (io/file (if-let [pods-dir (System/getenv "BABASHKA_PODS_DIR")]
             (io/file pods-dir)
             (io/file (or
                       (System/getenv "XDG_DATA_HOME")
                       (System/getProperty "user.home"))
                      ".babashka"
                      "pods"))
           "repository"))

(def pods-repo-dir
  ;; wrapped in delay for GraalVM native-image
  (delay
   (repo-dir)))

(defn github-url [qsym version]
  (format
   "https://raw.githubusercontent.com/babashka/pod-registry/master/manifests/%s/%s/manifest.edn"
   qsym version))

(defn pod-manifest
  [qsym version force?]
  (let [f (io/file @pods-repo-dir (str qsym) (str version) "manifest.edn")]
    (if (and (not force?)
             (.exists f))
      (edn/read-string (slurp f))
      (do (download (github-url qsym version) f false)
          (edn/read-string (slurp f))))))

(defn cache-dir
  ^java.io.File
  [{pod-name :pod/name
    pod-version :pod/version}]
  (let [base-file
        (if-let [pods-dir (System/getenv "BABASHKA_PODS_DIR")]
          (io/file pods-dir)
          (io/file (or
                    (System/getenv "XDG_CACHE_HOME")
                    (System/getProperty "user.home"))
                   ".babashka"
                   "pods"))]
    (io/file base-file
             "repository"
             (str pod-name)
             pod-version
             (normalize-os (:os/name @os))
             (:os/arch @os))))

(defn data-dir
  ^java.io.File
  [{pod-name :pod/name
    pod-version :pod/version}]
  (io/file @pods-repo-dir
           (str pod-name)
           pod-version
           (normalize-os (:os/name @os))
           (:os/arch @os)))

(defn sha256 [file]
  (let [buf (byte-array 8192)
        digest (java.security.MessageDigest/getInstance "SHA-256")]
    (with-open [bis (io/input-stream (io/file file))]
      (loop []
        (let [count (.read bis buf)]
          (when (pos? count)
            (.update digest buf 0 count)
            (recur)))))
    (-> (.encode (java.util.Base64/getEncoder)
                 (.digest digest))
        (String. "UTF-8"))))

(defn resolve [qsym version force?]
  (when-not (string? version)
    (throw (IllegalArgumentException. "Version must be provided for resolving from pod registry!")))
  (when-let [manifest (pod-manifest qsym version force?)]
    (let [artifacts (match-artifacts manifest)
          cdir (cache-dir manifest)
          ddir (data-dir manifest)
          execs (mapv (fn [artifact]
                        (let [url (:artifact/url artifact)
                              file-name (last (str/split url #"/"))
                              cache-file (io/file cdir file-name)
                              executable (io/file ddir (:artifact/executable artifact))]
                          (when (or force? (not (.exists executable)))
                            (warn (format "Downloading pod %s (%s)" qsym version))
                            (download url cache-file false)
                            (when-let [expected-sha (:artifact/hash artifact)]
                              (let [sha (sha256 cache-file)]
                                (when-not (= (str/replace expected-sha #"^sha256:" "")
                                             sha)
                                  (throw (ex-info (str "Wrong SHA-256 for file" (str cache-file))
                                                  {:sha sha
                                                   :expected-sha expected-sha})))))
                            (let [filename (.getName cache-file)]
                              (cond (str/ends-with? filename ".zip")
                                    (unzip {:zip-file cache-file
                                            :destination-dir ddir
                                            :verbose true})
                                    (or (str/ends-with? filename ".tgz")
                                        (str/ends-with? filename ".tar.gz"))
                                    (un-tgz cache-file ddir
                                            false))
                              (.delete cache-file))
                            (make-executable ddir [(:artifact/executable artifact)] false)
                            (warn (format "Successfully installed pod %s (%s)" qsym version))
                            (io/file ddir (:artifact/executable artifact)))
                          (io/file ddir (:artifact/executable artifact)))) artifacts)]
      {:executable (.getAbsolutePath ^java.io.File (first execs))
       :options (:pod/options manifest)})))
