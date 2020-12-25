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

(def os {:os/name (System/getProperty "os.name")
         :os/arch (let [arch (System/getProperty "os.arch")]
                    (normalize-arch arch))})

(defn warn [& strs]
  (binding [*out* *err*]
    (apply println strs)))

(defn match-artifacts [package]
  (let [artifacts (:pod/artifacts package)]
    (filter (fn [{os-name :os/name
                  os-arch :os/arch}]
              (let [os-arch (normalize-arch os-arch)]
                (and (re-matches (re-pattern os-name) (:os/name os))
                     (re-matches (re-pattern os-arch)
                                 (:os/arch os)))))
            artifacts)))

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
                (Files/createDirectories new-path (into-array []))
                (Files/copy ^java.io.InputStream zis
                            new-path
                            ^"[Ljava.nio.file.CopyOption;"
                            (into-array
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
    (sh "tar" "xf" (.getPath tmp-file) "--directory" (.getPath destination-dir))
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
      (io/copy is dest))
    (when verbose? (warn "Download complete."))))

(def pod-meta-dir
  ;; wrapped in delay for GraalVM native-image
  (delay (io/file (System/getProperty "user.home")
                  ".babashka" "pods" "meta")))

(defn pod-meta
  ([qsym] (pod-meta qsym nil))
  ([qsym version]
   (let [version (or version "latest")
         f (io/file @pod-meta-dir (str qsym) (str version ".edn"))]
     (if (.exists f) (edn/read-string (slurp f))
         ;; TODO: download from github?
         (case qsym
           'org.babashka/pod-babashka-postgresql
           '{:pod/name org.babashka/pod-babashka-postgresql
             :pod/description ""
             :pod/version "0.0.1"
             :pod/license ""
             :pod/artifacts
             [{:os/name "Mac.*"
               :os/arch "x86_64"
               :artifact/url "https://github.com/babashka/babashka-sql-pods/releases/download/v0.0.1/pod-babashka-postgresql-0.0.1-macos-amd64.zip"
               #_#_:artifact/hash "sha256:sfEkDVDKf/owDyW+hCj22N5eNgFNYk62fxpvKexwva0="
               ;; TODO: should this be a command vector rather?
               :artifact/executable "pod-babashka-postgresql"
               ;; or rather, give optional args here.
               }
              {:os/name "Linux.*"
               :os/arch "amd64"
               :artifact/url "https://github.com/babashka/babashka-sql-pods/releases/download/v0.0.1/pod-babashka-postgresql-0.0.1-linux-amd64.zip"
               #_#_:artifact/hash "sha256:NlCox8UXMq/y0dBGTjYkDSJEcJ8UZrkBQsK/OyRIQ6c="
               :artifact/executable "pod-babashka-postgresql"}
              #_{:os/name "Windows.*"
                 :os/arch "amd64"
                 :artifact/url "https://github.com/borkdude/babashka/releases/download/v0.2.2/babashka-0.2.2-windows-amd64.zip"
                 :artifact/hash "sha256:AAMks+jCr5JbeU4jHwaGxPHG22jyfvB5lzVEaRTpcHE="
                 :artifact/executable "bb.exe"}]})))))

(defn cache-dir
  ^java.io.File
  [{pod-name :pod/name
    pod-version :pod/version}]
  (io/file (or
            (System/getenv "XDG_CACHE_HOME")
            (System/getProperty "user.home"))
           ".babashka"
           "pods"
           "repository"
           (str pod-name)
           pod-version))

(defn data-dir
  ^java.io.File
  [{pod-name :pod/name
    pod-version :pod/version}]
  (io/file (or
            (System/getenv "XDG_DATA_HOME")
            (System/getProperty "user.home"))
           ".babashka"
           "pods"
           "repository"
           (str pod-name)
           pod-version))

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

(defn resolve [qsym]
  (when-let [package (pod-meta qsym)]
    (let [artifacts (match-artifacts package)
          cdir (cache-dir package)
          ddir (data-dir package)
          execs (mapv (fn [artifact]
                        (let [url (:artifact/url artifact)
                              file-name (last (str/split url #"/"))
                              cache-file (io/file cdir file-name)
                              executable (io/file ddir (:artifact/executable artifact))]
                          (if (.exists executable)
                            nil #_(when verbose?
                                    (warn "Package" (pkg-name package) "already installed"))
                            (do (download url cache-file false)
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
                                                :verbose false})
                                        (or (str/ends-with? filename ".tgz")
                                            (str/ends-with? filename ".tar.gz"))
                                        (un-tgz cache-file ddir
                                                false)))
                                (make-executable ddir [(:artifact/executable artifact)] false)
                                (io/file ddir (:artifact/executable artifact))) )
                          (io/file ddir (:artifact/executable artifact)))) artifacts)]
      [(.getAbsolutePath ^java.io.File (first execs))])))
