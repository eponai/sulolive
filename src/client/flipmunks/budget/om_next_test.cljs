(ns flipmunks.budget.om_next_test
  (:require [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defui HelloWorld
  Object
  (render [this]
    (html [:div (get (om/props this) :title)])))

(def hello (om/factory HelloWorld))

(defn run []
  (js/React.render 
    (html [:p 
           (map #(hello {:title (str "Hello " %)})
                (range 3))]) 
    (gdom/getElement "my-app")))

