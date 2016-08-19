(ns eponai.mobile.ios.ui.tab-bar-main
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [tab-bar-ios tab-bar-ios-item navigator-ios view text modal touchable-highlight]]
    [eponai.mobile.components.button :as button]
    [eponai.mobile.ios.ui.projects :refer [Projects ->Projects]]
    [eponai.mobile.ios.ui.add-transaction :refer [AddTransaction ->AddTransaction]]
    [eponai.mobile.ios.ui.profile :refer [Profile ->Profile]]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

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
  [_ props & [{:keys [is-selected? on-press]}]]
  (tab-bar-ios-item
    (opts {:title    "Add"
           :selected is-selected?
           :onPress  on-press})
    ;(->AddTransaction (om/computed props
    ;                               {:mode :create
    ;                                :on-cancel #()}))
    ))

(defmethod tab-bar-item :tab/projects
  [_ props & [{:keys [is-selected? on-press]}]]
  (tab-bar-ios-item
    (opts {:title    "List"
           :selected is-selected?
           :onPress  on-press})
    (->Projects props)))

(defui LoggedIn
  static om/IQuery
  (query [_]
    [{:proxy/dashboard (om/get-query Projects)}
     {:proxy/add-transaction (om/get-query AddTransaction)}
     {:proxy/projects (om/get-query Projects)}])
  Object
  (initLocalState [_]
    {:selected-tab :tab-list
     :add-visible? false})
  (componentDidMount [_]
    (.setBarStyle (.-StatusBar js/ReactNative) "default"))
  (render [this]
    (let [{:keys [selected-tab add-visible?]} (om/get-state this)
          {:keys [proxy/add-transaction
                  proxy/projects]} (om/props this)]
      (view
        (opts {:style {:flex 1}})
        (modal
          (opts {:visible add-visible?
                 :animationType "slide"})
          (->AddTransaction (om/computed add-transaction
                                         {:mode      :create
                                          :on-cancel #(do
                                                       (debug "Pressed add transaction cancel") (om/update-state! this assoc :add-visible? false))})))
        (tab-bar-ios
          (opts {:tintColor "blue" :barTintColor "white" :unselectedTintColor "gray"})

          (tab-bar-item :tab/projects
                        projects
                        {:is-selected? (= selected-tab :tab-list)
                         :on-press     #(om/update-state! this assoc :selected-tab :tab-list)})
          ;(tab-bar-item :tab/add-transaction
          ;              add-transaction
          ;              {:is-selected? (= selected-tab :tab-add)
          ;               :on-press     #(om/update-state! this assoc :selected-tab :tab-add)})

          (tab-bar-item :tab/add-transaction
                        add-transaction
                        {:on-press     #(om/update-state! this assoc :add-visible? true)})

          (tab-bar-item :tab/profile
                        (om/props this)
                        {:is-selected? (= selected-tab :tab-profile)
                         :on-press     #(om/update-state! this assoc :selected-tab :tab-profile)})
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