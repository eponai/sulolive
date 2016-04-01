(ns eponai.mobile.ios.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.mobile.components :refer
             [view text list-view navigator navigator-navigation-bar
              list-view-data-source touchable-highlight]]
            [eponai.mobile.ios.ui.add-transaction :as at]
            [eponai.mobile.ios.style :refer [styles]]
            [eponai.client.ui :as ui :refer-macros [opts camel]]
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [taoensso.timbre :refer-macros [info debug error trace warn]]))

(defn transaction-list-data-source [prev-props props]
  (list-view-data-source (:query/transactions prev-props)
                         (:query/transactions props)))

(defn kstr->k
  "key->str->key roundtrip, where (= k (kstr->k (str k)))"
  [kstr]
  (keyword (subs kstr 1)))

(defui ^:once Transactions
  static om/IQueryParams
  (params [this]
    {:edit-transaction (om-helper/subquery-in this [:nav :edit-transaction] at/AddTransaction)})
  static om/IQuery
  (query [this]
    '[{:query/transactions [:transaction/title
                            :transaction/uuid
                            :transaction/amount
                            {:transaction/date [:date/ymd]}
                            {:transaction/tags [:tag/name]}
                            {:transaction/currency [:currency/code]}
                            {:transaction/project [:project/uuid]}
                            {:transaction/type [:db/ident]}]}
      {:proxy/edit-transaction ?edit-transaction}])
  Object
  (initLocalState [this]
    {:data-source (transaction-list-data-source nil (om/props this))
     :selected-transaction nil})
  (componentWillUpdate [this next-props _]
    (om/update-state! this assoc :data-source
                      (transaction-list-data-source (om/props this) next-props)))
  (select-transaction [this transaction]
    (om/update-state! this assoc
                      :selected-transaction transaction)
    (.push (om-helper/react-ref this :nav)
           (str ::route-selected)))

  (exit-transaction [this]
    (.pop (om-helper/react-ref this :nav)
          (str ::route-transactions)))

  (render-transactions [this]
    (with-om-vars
      this
      (let [{:keys [query/transactions]} (om/props this)]
        (debug "Rendering transactions: " transactions)
        (list-view
          (styles :nav-bar->container
                  {:data-source (:data-source (om/get-state this))
                   :render-row  (fn [{:keys [transaction/title transaction/amount]
                                      :as   transaction}]
                                  (touchable-highlight
                                    (camel {:on-press #(.select-transaction this transaction)})
                                    (view (camel {:style {:flex-direction "row"}})
                                          (text (opts {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :margin-right 30}})
                                                title)
                                          (text (opts {:style {:font-size 30 :font-weight "100" :margin-bottom 20}})
                                                amount))))})))))
  (render-nav-bar [this]
    (with-om-vars
      this
      (navigator-navigation-bar
        (styles :nav-bar
                {:route-mapper
                 #js {"LeftButton"
                      (fn [route nav idx state]
                        (when (= (kstr->k route) ::route-selected)
                          (touchable-highlight
                            (camel {:on-press #(.exit-transaction this)})
                            (text {:width 50 :height 36 :font-size 17}
                                  "Back"))))
                      "RightButton"
                      (fn [route nav idx state]
                        )
                      "Title"
                      (fn [route nav idx state]
                        (text {:width 50 :height 36 :font-size 17}
                              (condp = (kstr->k route)
                                ::route-transactions "Transactions"
                                ::route-selected (-> this
                                                     om/get-state
                                                     :selected-transaction
                                                     :transaction/title))))}}))))
  (render-selected [this]
    (with-om-vars
      this
      (when-not (:selected-transaction (om/get-state this))
        (warn "NO SELECTED TRANSACTION"))
      (view (styles :nav-bar->container)
        (at/->AddTransaction (-> (om/props this)
                                 (:proxy/edit-transaction)
                                 (assoc :ref :edit-transaction)
                                 (om/computed
                                   {:on-saved    #(.exit-transaction this)
                                    :mode        :edit
                                    :transaction (:selected-transaction (om/get-state this))}))))))
  (render [this]
    (navigator
      ;; VERY IMPORTANT: Needs to call .forceUpdate in onDidFocus.
      ;; When we don't update this component, it'll render with stale props.
      (camel {:on-did-focus   #(.forceUpdate this)
              :navigation-bar (.render-nav-bar this)
              :ref            :nav
              :initial-route  (str ::route-transactions)
              :render-scene   (fn [route _]
                                (condp = (kstr->k route)
                                  ::route-transactions (.render-transactions this)
                                  ::route-selected (.render-selected this)))}))))


(def ->Transactions (om/factory Transactions))