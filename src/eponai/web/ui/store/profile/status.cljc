(ns eponai.web.ui.store.profile.status
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.common :as common]
    [eponai.web.ui.switch-input :as switch]
    [eponai.client.routes :as routes]))

(defn status-label [status]
  (get
    {:store.status/inactive "Your store is INACTIVE"
     :store.status/active   "Your store is ready to open"
     :store.status/disabled "Your store is DISABLED"
     :store.status/closed   "Your store is CLOSED"
     :store.status/open     "Your store is OPEN"}
    status
    "Your store is INACTIVE"))

(defn status-message [status]
  (get
    {:store.status/inactive "More information is needed before you can open your store."
     :store.status/active   "Open your store to make it appear in search results and enable purchases."
     :store.status/disabled "More information is needed before you can open your store."
     :store.status/closed   "Your store and products won't appear in search results and purchases cannot be made."
     :store.status/open     "Customers can find your store and products in search results, as well as make purchases."}
    status
    "More information is needed before you can open your store."))

(defn status-button [component status]
  (cond (#{:store.status/active
           :store.status/closed} status)
        (button/user-setting-default
          {:onClick #(om/update-state! component assoc :modal :payout-schedule)}
          (dom/span nil "Open store"))

        (#{:store.status/open} status)
        (button/user-setting-default
          {:onClick #(om/update-state! component assoc :modal :payout-schedule)}
          (dom/span nil "Close store"))

        :else
        (button/user-setting-default
          (css/add-class :disabled {:onClick #(om/update-state! component assoc :modal :payout-schedule)})
          (dom/span nil "Open store"))))

(defui StoreStatus
  static om/IQuery
  (query [_]
    [{:query/store [
                    ;:store/status [:store.status/type]
                    {:store/profile [:store.profile/photo
                                     :store.profile/cover
                                     :store.profile/email]}
                    :store/items]}
     {:query/stripe-account [:stripe/details-submitted?
                             :stripe/charges-enabled?
                             :stripe/payouts-enabled?
                             :stripe/verification]}
     :query/current-route])
  Object
  (render [this]
    (let [{:query/keys [store stripe-account current-route]} (om/props this)
          {:keys [route-params]} current-route
          status-type (get-in store [:store/status :store.status/type])
          {:stripe/keys [charges-enabled? payouts-enabled? verification]} stripe-account
          {:stripe.verification/keys [due-by fields-needed disabled-reason]} verification]
      (dom/div

        {:id "sulo-store-info-status"}
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Status"))

        (callout/callout
          nil
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/add-class :collapse)
                     (css/align :middle))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/label nil (status-label status-type))
                  (dom/p nil (dom/small nil (status-message status-type)))
                  (dom/p (css/add-class :store-status-reasons)
                         (when (nil? (get-in store [:store/profile :store.profile/photo]))
                           (dom/a {:href (routes/url :store-dashboard/profile route-params)} (dom/small nil "Upload store photo")))
                         (when (pos? (count (:store/items store)))
                           (dom/a {:href (routes/url :store-dashboard/create-product route-params)} (dom/small nil "Add your first product")))
                         (when (not-empty fields-needed)
                           (dom/a {:href (routes/url :store-dashboard/business#verify route-params)} (dom/small nil "Verify account")))))
                (grid/column
                  (css/text-align :right)
                  (status-button this status-type)
                  )))))

        ;(dom/div
        ;  (css/add-class :section-title)
        ;  (dom/h2 nil "Vacation mode"))
        ;(callout/callout
        ;  nil
        ;  (menu/vertical
        ;    (css/add-class :section-list)
        ;    (menu/item
        ;      nil
        ;      (grid/row
        ;        (->> (css/add-class :collapse)
        ;             (css/align :middle))
        ;        (grid/column
        ;          (grid/column-size {:small 12 :medium 6})
        ;          (dom/label nil "Vacation mode")
        ;          (dom/p nil (dom/small nil "Your store appears as usual to your customers.")))
        ;        (grid/column
        ;          (css/text-align :right)
        ;
        ;          (switch/switch
        ;            {:id "store.status"}
        ;            (dom/span (css/add-class :switch-inactive) "Off")
        ;            (dom/span (css/add-class :switch-active) "On"))
        ;          ;(dom/label nil "Status")
        ;          )))))
        ))))

(def ->StoreStatus (om/factory StoreStatus))
