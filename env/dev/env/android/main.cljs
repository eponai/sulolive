(ns ^:figwheel-no-load env.android.main
  (:require [eponai.mobile.android.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :heads-up-display false
  :jsload-callback core/reload!)

(core/init)

(def root-el (core/app-root))

