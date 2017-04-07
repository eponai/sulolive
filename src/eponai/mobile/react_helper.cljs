(ns eponai.mobile.react-helper
  (:require [taoensso.timbre :refer [error]]))

(set! js/window.React (js/require "react"))
(set! js/window.ReactNative (js/require "react-native"))
(set! js/document #js {:createElement (fn [& args]
                                        (error "Calling create-element on js/document!"))})
(def ReactNative js/window.ReactNative)

(defn create-element [rn-comp opts & children]
  (apply js/React.createElement rn-comp (clj->js opts) children))

(def app-registry (.-AppRegistry ReactNative))
(def view (partial create-element (.-View ReactNative)))
(def text (partial create-element (.-Text ReactNative)))
(def image (partial create-element (.-Image ReactNative)))
(def button (partial create-element (.-Button ReactNative)))
(def touchable-highlight (partial create-element (.-TouchableHighlight ReactNative)))
(def scroll-view (partial create-element (.-ScrollView ReactNative)))
(def touchable-opacity (partial create-element (.-TouchableOpacity ReactNative)))

(defn alert [title]
  (.alert (.-Alert ReactNative) title))