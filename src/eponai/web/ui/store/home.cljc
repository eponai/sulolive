(ns eponai.web.ui.store.home
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.web.ui.button :as button]
    [om.next :as om :refer [defui]]
    [eponai.common.format.date :as date]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.common.ui.utils :refer [two-decimal-price]]
    [eponai.web.ui.photo :as photo]
    [taoensso.timbre :refer [debug]]
    [clojure.string :as string]
    [eponai.web.ui.store.profile.options :as options]))

(defn verification-status-element [component]
  (let [{:query/keys [stripe-account current-route]} (om/props component)
        {:stripe/keys [charges-enabled? payouts-enabled? verification]} stripe-account
        {:stripe.verification/keys [due-by fields-needed disabled-reason]} verification
        store-id (get-in current-route [:route-params :store-id])
        is-alert? (or (false? charges-enabled?) (false? payouts-enabled?) (when (some? due-by) (< due-by (date/current-secs))))
        is-warning? (and (not-empty fields-needed) (some? due-by))

        disabled-labels {:fields_needed (dom/p nil (dom/span nil "More information is needed to verify your account. Please ")
                                               (dom/a nil (dom/span nil "provide the required information")) (dom/span nil " to re-enable the account."))}]
    (debug "Stripe account: " stripe-account)
    (debug "Notification: " (or is-alert? is-warning? (not-empty fields-needed)))
    (when (or is-alert? is-warning? (not-empty fields-needed))
      (callout/callout
        (cond->> (css/add-class :account-status (css/add-class :notification))
                 (or is-alert? is-warning?)
                 (css/add-class :warning))
        (grid/row
          nil
          (grid/column
            (css/add-class :shrink)
            (dom/i {:classes ["fa fa-warning fa-fw"]}))
          (grid/column
            nil
            (cond
              ;; Stripe has disabled charges because required information is overdue
              (false? charges-enabled?)
              [(dom/p nil (dom/strong nil "Charges from this account are disabled"))
               (get disabled-labels (keyword disabled-reason))]

              ;; Stripe has disabled payouts because required information is overdue
              (false? payouts-enabled?)
              [(dom/p nil (dom/strong nil "Payouts to this account are disabled"))
               (get disabled-labels (keyword disabled-reason))]

              ;; Stripe has set a deadline to provide the information
              (some? due-by)
              [(dom/p nil (dom/strong nil "More information is needed to verify your account"))
               (dom/p nil
                      (dom/span nil "More information needs to be collected to keep this account enabled. Please ")
                      (dom/a {:href (routes/url :store-dashboard/business#verify {:store-id store-id})} "provide the required information")
                      (dom/span nil " to prevent disruption in service to this account."))]

              ;; Stripe might need more information, and added some fields for verification. This might never be required.
              (not-empty fields-needed)
              [(dom/p nil (dom/strong nil "More information may be needed to verify your account"))
               (dom/p nil
                      (dom/span nil "If this account continues to process more volume, more information may need to be collected. To prevent disruption in service to this account you can choose to ")
                      (dom/a {:href (routes/url :store-dashboard/business#verify {:store-id store-id})} "provide the information")
                      (dom/span nil " proactively."))])
            ))))))

(defn check-list-item [done? href & [content]]
  (menu/item
    (cond->> (css/add-class :getting-started-item)
             done?
             (css/add-class :done))
    (dom/a
      {:href href}
      (dom/p nil
             (dom/i {:classes ["fa fa-check fa-fw"]})
             content))))

(defn store-status [store]
  (let [{:store/keys [status]} store]
    (if status
      (string/upper-case (name (:status/type status)))
      "CLOSED")))

(defui StoreHome
  static om/IQuery
  (query [_]
    [{:query/store [:db/id
                    :store/uuid
                    :store/username
                    {:store/profile [:store.profile/name
                                     :store.profile/description
                                     {:store.profile/photo [:photo/path :photo/id]}]}
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    {:store/stripe [{:stripe/status [:status/type]}]}
                    {:store/status [:status/type]}
                    {:store/shipping [:shipping/rules]}
                    {:order/_store [:order/items]}
                    {:stream/_store [:stream/state]}]}
     :query/store-item-count
     {:query/stripe-account [:stripe/details-submitted?]}
     {:query/store-has-streamed [:ui.singleton.state/store-has-streamed?]}
     :query/current-route])
  Object
  (render [this]
    (let [{:query/keys [store store-item-count current-route stripe-account store-has-streamed]} (om/props this)
          {:keys [store-id]} (:route-params current-route)
          {:keys [route route-params]} current-route
          store-item-count (or store-item-count 0)]
      (debug "Store: " store)
      (debug "Store has streamed: " store-has-streamed)
      (dom/div
        {:id "sulo-main-dashboard"}

        (dom/div
          (css/add-class :section-title)
          (dom/h1 nil "Home"))

        ;(dom/div
        ;  (->> {:id "store-navbar"}
        ;       (css/add-class :navbar-container))
        ;  (dom/nav
        ;    (->> (css/add-class :navbar)
        ;         (css/add-class :top-bar))
        ;    (menu/horizontal
        ;      nil
        ;      (menu/item
        ;        (when (= route :store-dashboard)
        ;          (css/add-class :is-active))
        ;        (dom/a {:href (routes/url :store-dashboard route-params)}
        ;               (dom/span nil "Overview"))))))
        (callout/callout-small
          (css/add-class :section-info)
          (grid/row
            (->> (css/align :center)
                 (css/align :middle))

            (grid/column
              (css/text-align :center)
              (dom/h3 nil (get-in store [:store/profile :store.profile/name]))
              (photo/store-photo store {:transformation :transformation/thumbnail})
              (button/store-navigation-default
                {:href    (routes/store-url store :store-dashboard/profile)
                 :onClick #(mixpanel/track-key ::mixpanel/go-to-store-info {:source "store-dashboard"})}
                (dom/span nil "Store info")
                (dom/i {:classes ["fa fa-chevron-right"]})))

            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Status")
              (dom/p nil (dom/span (css/add-classes [:stat])
                                   (store-status store)))
              (when (options/store-is-closed? store)
                (if (options/store-can-open? store)
                  (dom/p (css/add-class :text-sulo-dark) (dom/small nil (dom/strong nil "Your store can be opened!")))
                  (dom/p (css/add-class :text-warning) (dom/small nil (dom/strong nil "Action needed before store can open")))))
              ;(dom/p nil (dom/span (css/add-class "icon icon-opened")))
              (when-not (options/store-is-open? store)
                (button/store-navigation-default
                  {:href    (routes/store-url store :store-dashboard/profile#options)
                   :onClick #(mixpanel/track-key ::mixpanel/update-status {:source "store-dashboard"})}
                  (dom/span nil "Options")
                  (dom/i {:classes ["fa fa-chevron-right"]}))))))

        (callout/callout
          nil
          (grid/row
            nil

            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Balance")
              (dom/p (css/add-class :stat) "$0.00")
              (button/store-navigation-default
                {:href    (routes/store-url store :store-dashboard/finances)
                 :onClick #(mixpanel/track-key ::mixpanel/go-to-finances {:source "store-dashboard"})}
                (dom/span nil "Finances")
                (dom/i {:classes ["fa fa-chevron-right"]})))
            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Products")
              (dom/p (css/add-class :stat) store-item-count)
              (button/store-navigation-default
                {:href    (routes/store-url store :store-dashboard/product-list)
                 :onClick #(mixpanel/track-key ::mixpanel/go-to-products {:source "store-dashboard"})}
                (dom/span nil "Products")
                (dom/i {:classes ["fa fa-chevron-right"]})))
            (grid/column
              (css/text-align :center)
              (dom/h3 nil "Orders")
              (dom/p (css/add-class :stat) (count (:order/_store store)))
              (button/store-navigation-default
                {:href    (routes/store-url store :store-dashboard/order-list)
                 :onClick #(mixpanel/track-key ::mixpanel/go-to-orders {:source "store-dashboard"})}
                (dom/span nil "Orders")
                (dom/i {:classes ["fa fa-chevron-right"]})))))
        ;(callout/callout
        ;  nil
        ;  (grid/row
        ;    (grid/columns-in-row {:small 3})
        ;    (grid/column
        ;      (css/text-align :center)
        ;      (dom/h3 nil "Balance")
        ;      (dom/p (css/add-class :stat) (two-decimal-price 0)))
        ;    (grid/column
        ;      (css/text-align :center)
        ;      (dom/h3 nil "Customers")
        ;      (dom/p (css/add-class :stat) 0))
        ;    (grid/column
        ;      (css/text-align :center)
        ;      (dom/h3 nil "Payments")
        ;      (dom/p (css/add-class :stat) 0))))

        (let [described-store? (some? (:store.profile/description (:store/profile store)))
              defined-shipping? (not-empty (get-in store [:store/shipping :shipping/rules]))
              has-products? (boolean (pos? store-item-count))
              did-stream? (get-in store-has-streamed [:ui.singleton.state/store-has-streamed?])
              did-verify? (:stripe/details-submitted? stripe-account)
              did-open? (= :status.type/open (get-in store [:store/status :status/type]))]
          (when (some false? [described-store? defined-shipping? has-products? did-stream? did-verify? did-stream?])
            [
             (dom/div
               (css/add-class :section-title)
               (dom/h2 nil "Getting started"))
             (callout/callout
               nil
               (menu/vertical
                 (css/add-class :section-list)

                 (check-list-item
                   described-store?
                   (routes/store-url store :store-dashboard/profile)
                   (dom/span nil "Describe your store."))

                 (check-list-item
                   defined-shipping?
                   (routes/store-url store :store-dashboard/shipping)
                   (dom/span nil "Specify shipping options."))

                 (check-list-item
                   has-products?
                   (routes/store-url store :store-dashboard/create-product)
                   (dom/span nil "Add your first product."))

                 (check-list-item
                   did-stream?
                   (routes/store-url store :store-dashboard/stream)
                   (dom/span nil "Setup your first stream."))

                 (check-list-item
                   did-verify?
                   (routes/store-url store :store-dashboard/business#verify)
                   (dom/span nil "Verify your account, so we know you're real."))
                 (check-list-item
                   did-open?
                   (routes/store-url store :store-dashboard/profile#options)
                   (dom/span nil "Open your store and let customers find you"))))]))





        (grid/row
          (css/add-class :collapse)
          ;(grid/column
          ;  nil
          ;
          ;  (dom/div
          ;    (css/add-class :section-title)
          ;    (dom/h2 nil "Getting started"))
          ;
          ;  (callout/callout
          ;    nil
          ;    ))
          (if (:stripe/details-submitted? stripe-account)
            (when-let [verification-el (verification-status-element this)]
              (grid/column
                nil
                (dom/div
                  (css/add-class :section-title)
                  (dom/h2 nil "Notifications"))
                verification-el))
            (grid/column
              nil
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Notifications"))
              (callout/callout
                (->> (css/add-class :notification)
                     (css/add-class :action))
                (grid/row
                  nil
                  (grid/column
                    (css/add-class :shrink)
                    (dom/i {:classes ["fa fa-info fa-fw"]}))
                  (grid/column
                    nil
                    (callout/header nil "Verify your account")))
                (dom/p nil
                       (dom/span nil "Before ")
                       (dom/a {:href (routes/store-url store :store-dashboard/business#verify)} (dom/span nil "verifying your account"))
                       (dom/span nil ", you can only use SULO Live in test mode. You can manage your store, but it'll not be visible to the public."))
                (dom/p nil
                       (dom/span nil "Once you've verified your account you'll immediately be able to use all features of SULO Live. Your account details are reviewed with Stripe to ensure they comply with our ")
                       (dom/a {:href (routes/url :tos)} (dom/span nil "terms of service"))
                       (dom/span nil ". If there is a problem, we'll get in touch right away to resolve it as quickly as possible."))))))

        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Questions?"))
        (callout/callout
          nil
          (dom/p nil
                 (dom/span nil "We love to hear from you! Give us feedback, report problems, or just say hi, at ")
                 (dom/a {:href "mailto:hello@sulo.live"} "hello@sulo.live")
                 (dom/span nil ". Miriam, Diana or Petter will help you out.")))






        ;(grid/row
        ;  (->> (css/align :center)
        ;       (css/add-class :expanded)
        ;       (css/add-class :collapse)
        ;       (grid/columns-in-row {:small 1 :medium 2}))
        ;  (grid/column
        ;    nil
        ;    (store-info-element this))
        ;  (grid/column
        ;    nil
        ;
        ;    (if (:stripe/details-submitted? stripe-account)
        ;      (verification-status-element this)
        ;      (callout/callout
        ;        (->> (css/add-class :notification)
        ;             (css/add-class :action))
        ;        (grid/row
        ;          nil
        ;          (grid/column
        ;            (css/add-class :shrink)
        ;            (dom/i {:classes ["fa fa-info fa-fw"]}))
        ;          (grid/column
        ;            nil
        ;            (callout/header nil "Activate your account")))
        ;        (dom/p nil
        ;               (dom/span nil "Before ")
        ;               (dom/a {:href (routes/url :store-dashboard/settings#activate {:store-id store-id})} (dom/span nil "activating your account"))
        ;               (dom/span nil ", you can only use SULO Live in test mode. You can manage your store, but it'll not be visible to the public."))
        ;        (dom/p nil
        ;               "Once you've activated you'll immediately be able to use all features of SULO Live. Your account details are reviewed with Stripe to ensure they comply with our terms of service. If there is a problem, we'll get in touch right away to resolve it as quickly as possible.")))))
        ))))