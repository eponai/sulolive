(ns eponai.mobile.ios.ui.landing
  (:require-macros [natal-shell.components :refer [view text text-input image list-view touchable-highlight]]
                   [natal-shell.alert :refer [alert]])
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [goog.object :as gobj]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(set! js/React (js/require "react-native"))

(def logo-img (js/require "./images/cljs.png"))

(defui Loading
  Object
  (render [this]
    (let [{:keys [ui.component.loading/is-logged-in?]} (om/props this)]
      (view {} (text {} "Loading !")))))

(def ->Loading (om/factory Loading))

(defn- get-prop
  "PRIVATE: Do not use"
  [c k]
  (gobj/get (.-props c) k))

(defui ^:once Login
  static om/IQuery
  (query [this]
    [{:query/loading [:ui.component.loading/is-logged-in?]}])
  Object
  (render [this]
    (debug "Login: " this)
    (let [{:keys [:input/email :input/verify]} (om/get-state this)]
      (view (opts {:style {:flex-direction "column" :margin 40 :align-items "center"}})
            (text (opts {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}}) "Log in")
            (image (opts {:source logo-img
                          :style  {:width 80 :height 80 :margin-bottom 30}}))
            (view (opts {:style {:flex-direction "row" :align-items "center"}})
                  (text (opts {:style {:font-size 30 :margin-right 10}}) "Email:")
                  (text-input (opts {:style        {:height 40 :width 200 :border-color "gray" :border-width 1}
                                     :onChangeText #(om/update-state! this assoc :input/email %)
                                     :value        email
                                     :autoCapitalize "none"})))
            (touchable-highlight (opts {:style   {:background-color "#999" :padding 10 :border-radius 5}
                                        :onPress #(om/transact! this `[(signup/email ~{:input-email email
                                                                                       :device      :mobile})])})
                                 (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                                       "Log in"))))))



(defonce ->Login (om/factory Login))
