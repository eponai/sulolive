(ns eponai.mobile.ios.ui.root
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def react-native js/ReactNative)

(defn create-element [rn-comp opts & children]
  (let [children (vec (flatten children))
        opts (clj->js opts)]
    (condp = (count children)
      0 (js/React.createElement rn-comp opts)
      1 (js/React.createElement rn-comp opts (first children))
      (dom/create-element rn-comp opts children))))

(def view (partial create-element (.-View react-native)))
(def text (partial create-element (.-Text react-native)))
(def image (partial create-element (.-Image react-native)))
(def touchable-highlight (partial create-element (.-TouchableHighlight react-native)))


(defui Root
  Object
  (render [this]
    (debug "Render ")
    (view nil (text nil "This is an example"))))

(def ->Root (om/factory Root))
