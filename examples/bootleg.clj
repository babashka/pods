(require '[babashka.pods :as pods])

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.bootleg.utils :as utils])

(-> [:div
     [:h1 "Using Bootleg From Babashka"]
     [:p "This is a demo"]]
    (utils/convert-to :html))
