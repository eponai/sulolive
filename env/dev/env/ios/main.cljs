(ns ^:figwheel-no-load env.ios.main
  (:require [eponai.mobile.ios.core :as core]
            [eponai.mobile.ios.app :as app]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(def dev-machine-ip "192.168.0.4")

(figwheel/watch-and-reload
  :websocket-url (str "ws://" dev-machine-ip ":3449/figwheel-ws")
  :heads-up-display false
  :jsload-callback #(app/reload!))

(core/init {:server-address (str "http://" dev-machine-ip ":3000")})

(def root-el (app/app-root))

