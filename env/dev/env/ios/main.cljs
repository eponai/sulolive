(ns ^:figwheel-no-load env.ios.main
  (:require [eponai.mobile.ios.core :as core]
            [eponai.mobile.ios.app :as app]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :heads-up-display true
  :jsload-callback #(app/reload!))

(core/init)

(def root-el (app/app-root))

