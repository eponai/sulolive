(ns eponai.mobile.ios.ui.tab-bar-main
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [tab-bar-ios tab-bar-ios-item navigator-ios view text modal]]
    [eponai.mobile.ios.ui.dashboard :refer [Dashboard ->Dashboard]]
    [eponai.mobile.ios.ui.profile :refer [Profile ->Profile]]
    [om.next :as om :refer-macros [defui]]))

(defmulti tab-bar-item (fn [k _ & _] k))

(defmethod tab-bar-item :tab/profile
  [_ props & [{:keys [is-selected? on-press]}]]
  (tab-bar-ios-item
    (opts {:title    "Me"
           :selected is-selected?
           :onPress  on-press})
    (navigator-ios
      (opts {:style            {:flex 1}
             :initialRoute     {:title     ""
                                :component ->Profile
                                :passProps props}
             :itemWrapperStyle {:marginTop 60 :marginBottom 50}}))))

(defmethod tab-bar-item :tab/add-transaction
  [_ _ & [{:keys [is-selected? on-press]}]]
  (tab-bar-ios-item
    (opts {:title    "Add"
           :selected is-selected?
           :onPress  on-press})
    (view nil)))

(defmethod tab-bar-item :tab/dashboard
  [_ _ & [{:keys [is-selected? on-press]}]]
  (tab-bar-ios-item
    (opts {:title    "List"
           :selected is-selected?
           :onPress  on-press})
    (->Dashboard)))

(defui LoggedIn
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Dashboard)}])
  Object
  (initLocalState [_]
    {:selected-tab :tab-list})
  (componentDidMount [_]
    (.setBarStyle (.-StatusBar js/ReactNative) "default"))
  (render [this]
    (let [{:keys [selected-tab]} (om/get-state this)]
      (modal
        (opts {:visible true})
        (tab-bar-ios
          (opts {:tintColor "blue" :barTintColor "white" :unselectedTintColor "gray"})

          (tab-bar-item :tab/profile
                        (om/props this)
                        {:is-selected? (= selected-tab :tab-profile)
                         :on-press     #(om/update-state! this assoc :selected-tab :tab-profile)})

          (tab-bar-item :tab/add-transaction
                        nil
                        {:is-selected? (= selected-tab :tab-add)
                         :on-press     #(om/update-state! this assoc :selected-tab :tab-add)})

          (tab-bar-item :tab/dashboard
                        nil
                        {:is-selected? (= selected-tab :tab-list)
                         :on-press     #(om/update-state! this assoc :selected-tab :tab-list)})
          ;(tab-bar-ios-item
          ;  (opts {:title    "Me"
          ;         :selected (= selected-tab :tab-profile)
          ;         :onPress  #(om/update-state! this assoc :selected-tab :tab-profile)})
          ;  (navigator-ios
          ;    (opts {:style            {:flex 1}
          ;           :initialRoute     {:title     ""
          ;                              :component ->Profile
          ;                              :passProps (om/props this)}
          ;           :itemWrapperStyle {:marginTop 60 :marginBottom 50}})))
          ;(tab-bar-ios-item
          ;  (opts {:title    "List"
          ;         :selected (= selected-tab :tab-list)
          ;         :onPress  #(om/update-state! this assoc :selected-tab :tab-list)})
          ;  (t/->Transactions))
          ))
      )))

(def ->LoggedIn (om/factory LoggedIn))