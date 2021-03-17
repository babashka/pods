(defproject babashka/babashka.pods "0.0.1"
  :description "babashka pods"
  :url "https://github.com/babashka/babashka.pods"
  :scm {:name "git"
        :url "https://github.com/babashka/babashka.pods"}
  :license {:name "EPL-1.0"
            :url "https://www.eclipse.org/legal/epl-1.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [nrepl/bencode "1.1.0"]
                 [cheshire "5.10.0"]]
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass
                                    :sign-releases false}]]
  :profiles {:test {:dependencies [[borkdude/sci "0.2.4"]]}})
