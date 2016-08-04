(ns eponai.mobile.ios.ui.tab-bar-item
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.ios.ui.tab-bar-item.dashboard :refer [->Dashboard]]
    [eponai.mobile.ios.ui.tab-bar-item.profile :refer [->Profile]]
    [eponai.mobile.components :refer [view text text-input image list-view touchable-highlight navigator-ios tab-bar-ios tab-bar-ios-item]]))

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
;(defn profile-tab [props {:keys [is-selected? on-press]}]
;  (tab-bar-ios-item
;    (opts {:title    "Me"
;           :selected is-selected?
;           :onPress  on-press})
;    (navigator-ios
;      (opts {:style            {:flex 1}
;             :initialRoute     {:title     ""
;                                :component ->Profile
;                                :passProps props}
;             :itemWrapperStyle {:marginTop 60 :marginBottom 50}}))))

;(defn transactions-tab [_ {:keys [is-selected? on-press]}]
;  (tab-bar-ios-item
;    (opts {:title    "List"
;           :selected is-selected?
;           :onPress  on-press})
;    (->Transactions)))