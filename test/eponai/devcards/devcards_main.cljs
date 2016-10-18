(ns eponai.devcards.devcards-main
  (:require
    [cljsjs.react.dom]
    [devcards.core :as dc]
    [sablono.core :as html :refer-macros [html]])
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
  (html [:h1 "Devcards is freaking awesome!"]))

