(ns flipmunks.budget.om-next-test
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defui HelloWorld
  Object
  (render [this]
    (dom/div nil "Hello World")))

(def hello (om/factory HelloWorld))

(defn run []
  (js/React.render (hello) (gdom/getElement "my-app")))

