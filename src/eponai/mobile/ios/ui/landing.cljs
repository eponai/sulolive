(ns eponai.mobile.ios.ui.landing
  (:require-macros [natal-shell.components :refer [view text list-view]])
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(set! js/React (js/require "react-native"))

(defui Loading
  Object
  (render [this]
    (view {} (text {} "Loading !"))))

(def ->Loading (om/factory Loading))

(defui Login
  static om/IQuery
  (query [this]
    [])
  Object
  (render [this]
    (view {} (text {} "Login :)"))))

(def ->Login (om/factory Login))
