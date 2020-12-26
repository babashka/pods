(require '[babashka.pods :refer [load-pod]])

(load-pod 'org.babashka/lanterna "0.0.1-SNAPSHOT" {#_#_:force true #_#_:transport :socket})

(require '[pod.babashka.lanterna.terminal :as terminal])

(def terminal (terminal/get-terminal))

(terminal/start terminal)

(terminal/put-string terminal
                     (str "Hello TUI Babashka!")
                     10 5)
(terminal/put-string terminal
                     (str "The size of this terminal: "
                          (terminal/get-size terminal))
                     10 6)
(terminal/put-string terminal
                     "Press q to exit."
                     10 7)

(terminal/flush terminal)

(def k (terminal/get-key-blocking terminal))

(terminal/put-string terminal
                     (str "You pressed: " k)
                     10 8)

(Thread/sleep 1000)

(terminal/stop terminal)

(shutdown-agents)
