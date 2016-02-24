(ns eponai.devcards.devcards_main
  (:require
    [cljsjs.react.dom]
    [devcards.core :as dc]
    [sablono.core :as html :refer-macros [html]]
    [eponai.devcards.ui.widget-dc])
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
  (html [:h1 "Devcards is freaking awesome!"]))

