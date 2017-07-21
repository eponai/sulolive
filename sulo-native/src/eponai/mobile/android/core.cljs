(ns eponai.mobile.android.core
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [re-natal.support :as sup]))

(set! js/window.React (js/require "react"))
(def ReactNative (js/require "react-native"))

(def create-element dom/create-element)

(def app-registry (.-AppRegistry ReactNative))
(def view (partial create-element (.-View ReactNative)))
(def text (partial create-element (.-Text ReactNative)))
(def image (partial create-element (.-Image ReactNative)))
(def touchable-highlight (partial create-element (.-TouchableHighlight ReactNative)))

(def logo-img (js/require "./images/cljs.png"))

(defn alert [title]
  (.alert (.-Alert ReactNative) title))

(defui AppRoot
  static om/IQuery
  (query [this]
    '[:app/msg])
  Object
  (render [this]
    (let [{:keys [app/msg]} (om/props this)]
      (view {:style {:flexDirection "column" :margin 40 :alignItems "center"}}
            (text {:style {:fontSize 30 :fontWeight "100" :marginBottom 20 :textAlign "center"}} msg)
            (image {:source logo-img
                    :style  {:width 80 :height 80 :marginBottom 30}})
            (touchable-highlight {:style   {:backgroundColor "#999" :padding 10 :borderRadius 5}
                                  :onPress #(alert "HELLO!")}
                                 (text {:style {:color "white" :textAlign "center" :fontWeight "bold"}} "press me"))))))

(defonce RootNode (sup/root-node! 1))
(defonce app-root (om/factory RootNode))

(defonce reconciler-atom (atom nil))

(defn init []
  (let [read (fn [{:keys [state]} k _]
               (let [st @state]
                 (if-let [[_ v] (find st k)]
                   {:value v}
                   {:value :not-found})))
        reconciler (om/reconciler
                     {:state        (atom {:app/msg "Hello Clojure in iOS and Android!"})
                      :parser       (om/parser {:read read})
                      :root-render  sup/root-render
                      :root-unmount sup/root-unmount
                      :migrate nil})]
    (reset! reconciler-atom reconciler)
    (om/add-root! reconciler AppRoot 1))
  (.registerComponent app-registry "SuloLiveApp" (fn [] app-root)))

(defn reload! []
  #(om/add-root! @reconciler-atom AppRoot 1))
