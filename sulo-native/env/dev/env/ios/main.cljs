(ns ^:figwheel-no-load env.ios.main
  ;; (:require-macros [env.client.utils :as utils])
  (:require [eponai.mobile.ios.core :as core]
            [eponai.mobile.ios.app :as app]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

;; (def ip (utils/dev-machine-ip))
(def ip "localhost")

(figwheel/watch-and-reload
  :websocket-url (str "ws://" ip ":3450/figwheel-ws")
  :heads-up-display false
  :jsload-callback #(app/reload!))

(core/init {:server-address (str "http://" ip ":3000")})

(def root-el (app/app-root))

