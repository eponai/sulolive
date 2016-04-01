(ns env.ios.local-main
  (:require [eponai.mobile.ios.core :as core]))

;; TODO: Un-hardcode this.
(core/init {:server-address "http://192.168.0.4:3000"})

