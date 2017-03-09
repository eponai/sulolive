(ns eponai.devcards.devcards-main
  (:require
    [cljsjs.react.dom]
    [devcards.core :as dc]
    [eponai.devcards.checkout.shipping-cards]
    [om.dom :as dom]
    [cljs.spec :as s])
  (:require-macros
    [devcards.core :refer [defcard]]))

(s/check-asserts true)
(defcard my-first-card
  ;; TODO:
  (dom/div nil "This is a devcard"))

