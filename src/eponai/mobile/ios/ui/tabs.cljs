(ns eponai.mobile.ios.ui.tabs
  (:require [eponai.mobile.ios.ui.transactions :as t]
            [eponai.mobile.ios.ui.add-transaction :as a]
            [eponai.mobile.ios.style :refer [styles]]
            [eponai.mobile.components :refer [view text tab-bar-ios tab-bar-ios-item]]
            [eponai.mobile.om-helper :as om-helper]
            [eponai.client.ui :refer-macros [opts]]
            [om.next :as om :refer-macros [defui]]
            [taoensso.timbre :refer-macros [debug error]]))

(defui ^:once Tabs
  static om/IQueryParams
  (params [this]
    {:transactions    (om-helper/subquery-in this [:transactions] t/Transactions)
     :add-transaction (om-helper/subquery-in this [:add-transaction] a/AddTransaction)})
  static
  om/IQuery
  (query [this]
    ;;TODO: Optimize this query so that it doesn't query for both all the time.
    ;;     Should only query for the one selected.
    '[{:proxy/transactions ?transactions}
      {:proxy/add-transaction ?add-transaction}])
  Object
  (initLocalState [this]
    {:selected-tab :transactions})
  (render [this]
    (let [{:keys [selected-tab]} (om/get-state this)]
      (tab-bar-ios
       {:ref :tab-bar}
       (tab-bar-ios-item {:ref :transactions-tab
                          :selected (= selected-tab :transactions)
                          :onPress #(om/update-state! this assoc :selected-tab :transactions)
                          :title    "Transactions"}
                         (t/->Transactions (-> (om/props this)
                                               (:proxy/transactions)
                                               (assoc :ref :transactions))))
       (tab-bar-ios-item {:ref :add-tab
                          :selected (= selected-tab :new)
                          :onPress  #(om/update-state! this assoc :selected-tab :new)
                          :title    "New"}
                         (view (styles :nav-bar->container)
                           (a/->AddTransaction (-> (om/props this)
                                                   (:proxy/add-transaction)
                                                   (assoc :ref :add-transaction)
                                                   (om/computed {:mode :create})))))))))

(def ->Tabs (om/factory Tabs))
