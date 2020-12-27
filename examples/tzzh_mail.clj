(require '[babashka.pods :as pods])
(pods/load-pod 'tzzh/mail "0.0.2")
(require '[pod.tzzh.mail :as m])

(m/send-mail {:host "smtp.gmail.com"
              :port 587
              :username "kylian.mbappe@gmail.com"
              :password "kylian123"
              :subject "Subject of the email"
              :from "kylian.mbappe@gmail.com"
              :to ["somebody@somehwere.com"]
              :cc ["somebodyelse@somehwere.com"]
              :text "aaa" ;; for text body
              :html "<b> kajfhajkfhakjs </b>" ;; for html body
              :attachments ["./do-everything.clj"] ;; paths to the files to attch
              })
