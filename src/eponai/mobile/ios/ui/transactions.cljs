(ns eponai.mobile.ios.ui.transactions
  (:require [om.next :as om :refer-macros [defui]]
            [datascript.core :as d]
            [eponai.mobile.components :refer
             [view text list-view
              list-view-data-source touchable-highlight
              navigation-experimental-header
              navigation-experimental-header-title
              navigation-experimental-card-stack]]
            [eponai.mobile.ios.ui.add-transaction :as at]
            [eponai.mobile.ios.style :refer [styles]]
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [eponai.web.ui.utils :as web.utils]
            [eponai.client.ui :as ui :refer-macros [opts camel]]
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
    {:edit-transaction (om/get-query at/AddTransaction)})

  static om/IQuery
  (query [this]
    '[{:query/transactions [:transaction/title
                            :transaction/uuid
                            :transaction/amount
                            {:transaction/date [:date/ymd :date/timestamp]}
                            {:transaction/tags [:tag/name]}
                            {:transaction/currency [:currency/code]}
                            {:transaction/project [:project/uuid]}
                            {:transaction/type [:db/ident]}]}
      {:proxy/edit-transaction ?edit-transaction}])

  ;; TODO: Move web.utils to client utils.
  web.utils/ISyncStateWithProps
  (props->init-state [_ props]
    {:data-source          (transaction-list-data-source nil props)
     :selected-transaction nil
     :nav-state            {:key             "KEY FOO" :index 0 :children [{:key "list" :title "List"}]
                            ::__nav-state-id (hash props)}})

  Object
  (initLocalState [this]
    (web.utils/props->init-state this (om/props this)))

  (componentWillReceiveProps [this next-props]
    (web.utils/sync-with-received-props this next-props))

  (componentWillUpdate [this next-props _]
    (om/update-state! this assoc :data-source
                      (transaction-list-data-source (om/props this) next-props)))

  (back [this]
    (om/update-state! this update :nav-state
                      #(cond-> %
                               (pos? (:index %))
                               (-> (update :index dec)
                                   (update :children pop)))))

  (exit-transaction [this]
    (.back this))

  (select-transaction [this transaction]
    (om/update-state! this #(-> %
                                (assoc :selected-transaction transaction)
                                (update-in [:nav-state :index] inc)
                                (update-in [:nav-state :children] conj #js {:key "selected" :title "Selected"}))))

  (render-transactions [this]
    (with-om-vars
      this
      (let [{:keys [query/transactions]} (om/props this)]
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
  (render-nav-bar [this props]
    (with-om-vars
      this
      (navigation-experimental-header
        (assoc (js->clj props) :renderTitleComponent #(navigation-experimental-header-title
                                                       {:title "Title" :children "needs to be a string?"})))))

  (render-selected [this]
    (with-om-vars
      this
      (when-not (:selected-transaction (om/get-state this))
        (warn "NO SELECTED TRANSACTION"))
      (view (styles :nav-bar->container)
        (at/->AddTransaction (-> (om/props this)
                                 (:proxy/edit-transaction)
                                 (om/computed
                                   {:on-saved    #(.exit-transaction this)
                                    :mode        :edit
                                    :transaction (:selected-transaction (om/get-state this))}))))))

  (render-scene [this]
    (if (zero? (get-in (om/get-state this) [:nav-state :index]))
      (.render-transactions this)
      (.render-selected this)))

  (render [this]
    (navigation-experimental-card-stack
      ;; VERY IMPORTANT: Needs to call .forceUpdate in onDidFocus.
      ;; When we don't update this component, it'll render with stale props.
      {:onNavigate      #(.back this %)
       ;; :onDidFocus      #(.forceUpdate this)
       :renderOverlay   #(.render-nav-bar this %)
       :navigationState (-> (:nav-state (om/get-state this))
                            clj->js)
       :style           {:flex 1}
       :renderScene     #(.render-scene this)})))


(def ->Transactions (om/factory Transactions))