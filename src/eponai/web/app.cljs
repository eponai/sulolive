(ns eponai.web.app
  (:require [om.next :as om :refer-macros [defui]]
    ;; To initialize ReactDOM:
            [cljsjs.react.dom]
            [taoensso.timbre :refer [info]]))

(defn run []
  (info "Run called in: " (namespace ::foo)))
