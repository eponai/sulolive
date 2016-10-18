(ns eponai.web.ui.settings
  (:require
    [cljs.core.async :refer [chan <! put!]]
    [clojure.walk :refer [keywordize-keys]]
    [eponai.client.ui :refer [map-all] :refer-macros [opts]]
    [eponai.web.ui.select :as sel]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug trace]]
    [eponai.web.ui.format :as f])
  (:require-macros [cljs.core.async.macros :refer [go]]))

; Stripe helpers

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))

(defn checkout-loaded? []
  (boolean (aget js/window "StripeCheckout")))

(defn stripe-token-recieved-cb [component]
  (fn [token]
    (let [clj-token (keywordize-keys (js->clj token))]
      (debug "Recieved token from Stripe.")
      (trace "Recieved token from Stripe: " clj-token)
      (om/transact! component `[(stripe/update-card ~{:token clj-token})
                                :query/stripe]))))

(defn open-checkout [component email panel-label]
  (let [checkout (.configure js/StripeCheckout
                             (clj->js {:key    "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
                                       :locale "auto"
                                       :token  (stripe-token-recieved-cb component)}))]
    (.open checkout
           #js {:name            "JourMoney"
                :email           email
                :locale          "auto"
                :allowRememberMe false
                :opened          #(.show-stripe-loading component false) ; #(.show-loading component false)
                :closed          #(debug "StripeCheckout did close.")
                :panelLabel      panel-label
                })))

(defn get-loader [props]
  (get props [:ui/singleton :ui.singleton/loader]))
(defui Settings
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email
                           :user/name
                           {:user/currency [:currency/code
                                            :currency/name]}]}
     {[:ui/singleton :ui.singleton/loader] [:ui.singleton.loader/visible]}
     {:query/all-currencies [:currency/code
                             :currency/name]}
     {:query/stripe [:stripe/user
                     :stripe/info
                     {:stripe/subscription [:stripe.subscription/period-end
                                            :stripe.subscription/status]}]}
     {:query/fb-user [:fb-user/name
                      :fb-user/id
                      :fb-user/picture]}
     ])


  Object
  (initLocalState [_]
    (let [checkout-loaded (checkout-loaded?)]
      {:checkout-loaded?   checkout-loaded
       :load-checkout-chan (chan)
       :is-stripe-loading?        (not checkout-loaded)
       :tab                :payment}))
  (componentWillMount [this]
    (let [{:keys [load-checkout-chan
                  checkout-loaded?]} (om/get-state this)]
      (when-not checkout-loaded?
        (go (<! load-checkout-chan)
            (om/set-state! this {:checkout-loaded? true
                                 :is-stripe-loading? false}))
        (load-checkout load-checkout-chan))))

  (componentDidMount [this]
    (let [{:keys [query/current-user]} (om/props this)]
      (om/update-state! this assoc :input-currency (-> current-user
                                                       :user/currency
                                                       :currency/code))))
  (save-settings [this]
    (let [{:keys [query/current-user]} (om/props this)
          {:keys [input-currency]} (om/get-state this)
          {:keys [on-close]} (om/get-computed this)]
      (om/transact! this `[(settings/save ~{:currency input-currency
                                            :user     current-user})
                           :query/dashboard
                           :query/transactions])
      (when on-close
        (on-close))))
  (update-payment [this label]
    (let [{:keys [query/current-user]} (om/props this)]
      (.show-stripe-loading this true)
      (open-checkout this (:user/email current-user) label)))

  (show-stripe-loading [this is-loading?]
    (om/update-state! this assoc :is-stripe-loading? is-loading?))

  (render [this]
    (let [{:keys [query/current-user
                  query/all-currencies
                  query/fb-user
                  query/stripe] :as props} (om/props this)
          loader (get-loader props)
          {user-name :user/name
           :keys [user/email]} current-user
          {:keys [input-currency tab checkout-loaded? is-stripe-loading?]} (om/get-state this)
          {:keys [stripe/subscription stripe/info]} stripe
          {subscription-status :stripe.subscription/status} subscription]
      (debug "StripeCheckout loaded: " checkout-loaded?)
      (debug "Stripe information: " info)
      (html
        [:div#settings
         [:h4.header "Settings"]
         [:div.top-bar-container.subnav
          [:div.top-bar.menu
           [:a
            {:class    (when (= tab :general) "active")
             :on-click #(om/update-state! this assoc :tab :general)}
            "General"]
           [:a
            {:class    (when (= tab :social) "active")
             :on-click #(om/update-state! this assoc :tab :social)}
            "Social"]

           [:a
            {:class    (when (= tab :payment) "active")
             :on-click #(om/update-state! this assoc :tab :payment)}
            "Payment"]]]

         (cond (= tab :general)
               [:div.content.general
            [:div.content-section
             [:div.row.email
              [:div.column.small-4
               [:label "Email"]]
              [:div.column.small-8.text-right
               [:strong email]]]]
            [:div.content-section
             [:div.row.name
              [:div.column.small-4
               [:label "Name"]]
              [:div.column.small-6.small-offset-2
               [:input
                {:value (or user-name "")
                 :type  "text"}]]]]

            [:div.content-section
             [:div.row
              [:div.column.small-4
               [:label "Main Currency"]]
              [:div.column.small-3.small-offset-5.text-right
               [:div.text-left
                (sel/->Select (om/computed
                                {:value   {:label (:currency/code (:user/currency current-user))
                                           :value (:db/id (:user/currency current-user))}
                                 :options (map (fn [{:keys [currency/code db/id]}]
                                                 {:label code
                                                  :value id})
                                               all-currencies)}
                                {:on-select #(om/update-state! this assoc :input-currency (:label %))}))]]
              ]]
            [:div.content-section.clearfix
             [:a.button.hollow.float-right
              {:on-click #(.save-settings this)}
              [:span.small-caps "Save"]]]]

               (= tab :social)
               [:div.content
                [:div.content-section.facebook
                 [:div.row
                  [:div.column.small-4
                   [:label "Facebook"]]
                  [:div.column.small-8.text-right
                   (if (nil? fb-user)
                     [:a.button.facebook [:i.fa.fa-facebook.fa-fw] "Connect to Facebook"]
                     [:div.row.collapse.align-middle
                      [:div.column.small-10.text-right
                       [:div (:fb-user/name fb-user)]
                       [:a.link [:small "disconnect"]]]
                      [:div.column.small-2.text-left
                       [:img.avatar {:src (:fb-user/picture fb-user)}]]])]]]

                [:div.content-section.twitter
                 [:div.row
                  [:div.column.small-4
                   [:label "Twitter"]]
                  [:div.column.small-8.text-right
                   [:a.button.twitter [:i.fa.fa-twitter.fa-fw] "Connect to Twitter"]]]]

                [:div.content-section.twitter
                 [:div.row
                  [:div.column.small-4
                   [:label "Google+"]]
                  [:div.column.small-8.text-right
                   [:a.button.google [:i.fa.fa-google.fa-fw] "Connect to Google+"]]]]]


               (= tab :payment)
               [:div.content#payment
                [:div.content-section
                 [:div.row.column
                  [:label "Pay what you want"]]
                 [:div.row
                  [:div.column.small-8"$1.00"]]]
                [:div.content-section
                 [:div.row.align-middle
                  [:div.column.small-4
                   [:label "Payment Method"]]
                  (if (:card info)
                    [:div.column.small-8
                     [:div.row.align-middle
                      [:div.column.small-8.text-right
                       [:div.row
                        [:div.column.small-12.text-right
                         [:span (get-in info [:card :brand])]]
                        [:div.column.small-12.text-right
                         [:span (str "**** **** ****" (get-in info [:card :last4]))]]]]
                      [:div.column.small-4
                       [:a.button.hollow.expanded
                        {:on-click #(.update-payment this "Update Card")}
                        (if is-stripe-loading?
                          [:i.fa.fa-spinner.fa-spin.fa-fw]
                          [:span "Update"])
                        ]]]]
                    [:div.column
                     [:div.row.align-middle
                      [:div.column.small-3
                       [:a.button
                        {:on-click #(.update-payment this "Add Card")}
                        "+ Add Card"]]
                      [:div.column
                       (when (= subscription-status :trialing)
                         [:small.trial-label (str "You have " (f/days-until (:stripe.subscription/period-end subscription)) " days left on your trial.")])]]])]]])]
        ;[:div
        ; [:div#settings-general.row.column.small-12.medium-6
        ;  [:div.callout.clearfix
        ;   [:h4.small-caps
        ;    "General"]
        ;   [:hr]
        ;   [:div.row
        ;    [:div.columns.small-2.text-right
        ;     [:span.small-caps
        ;      "Email:"]]
        ;    [:div.columns.small-10
        ;     [:p [:strong email]]]]
        ;
        ;   [:div.row
        ;    [:div.columns.small-2.text-right
        ;     [:span.small-caps
        ;      "Name:"]]
        ;    [:div.columns.small-10
        ;     [:input
        ;      {:value (or user-name "")
        ;       :type  "text"}]]]
        ;   [:div.row
        ;
        ;    [:div.columns.small-2.text-right
        ;     [:span.small-caps
        ;      "Currency:"]]
        ;    [:div.columns.small-10
        ;     [:input
        ;      {:value     (or input-currency "")
        ;       :type      "text"
        ;       :on-change #(om/update-state! this assoc :input-currency (.-value (.-target %)))
        ;       :list      "currency-name"}]]
        ;    [:datalist#currency-name
        ;     (map-all
        ;       all-currencies
        ;       (fn [{:keys [currency/code currency/name]}]
        ;         [:option (opts {:key   [code]
        ;                         :value code})
        ;          name]))]]
        ;
        ;   [:a.button.primary.hollow.float-right
        ;    {:on-click #(om/transact! this `[(settings/save ~{:currency      input-currency
        ;                                                      :user          current-user})
        ;                                     :query/dashboard
        ;                                     :query/transactions])}
        ;    [:span.small-caps "Save"]]]]
        ; [:div.row.column.small-12.medium-6
        ;  [:div.callout
        ;   [:h4.small-caps "Account"]
        ;   [:hr]
        ;   [:div.row
        ;    [:div.columns.small-2.text-right
        ;     [:span.small-caps
        ;      "Plan:"]]
        ;    (if (= subscription-status :active)
        ;      [:div.columns.small-10
        ;       [:div
        ;        [:strong "Monthly"]
        ;        [:span
        ;         "Account active until: " (f/to-str (:stripe.subscription/period-end subscription) "yyyyMMdd")]]
        ;       (when (:ui.singleton.loader/visible loader)
        ;         (utils/loader))]
        ;
        ;      [:div.columns.small-10
        ;       ;[:p "You have "
        ;       ; (f/days-until (:stripe.subscription/period-end subscription)) " days left on your trial."]
        ;       ;[:div.columns.small-12.medium-6.text-center]
        ;       (utils/upgrade-button)
        ;       ;[:div [:small "You have "
        ;       ;       (f/days-until (:stripe.subscription/period-end subscription)) " days left on your trial."]]
        ;       ])]
        ;   (when (= subscription-status :active)
        ;     [:div.row
        ;      [:div.columns.small-2.text-right]
        ;      [:div.columns.small-10
        ;       [:a.link
        ;        (opts {:style    {:margin "1em 0"}
        ;               :on-click #(om/transact! this `[(stripe/cancel) :query/stripe])})
        ;        [:small
        ;         "Cancel"]]
        ;       [:a.link
        ;        (opts {:style    {:margin "1em"}})
        ;        [:small
        ;         "Edit"]]]])
        ;   ;[:hr]
        ;   ;[:h5 "Payment method"]
        ;   ;[:div.payment
        ;   ; [:div.row
        ;   ;  [:div.columns.small-2.text-right
        ;   ;   [:label
        ;   ;    "Name:"]]
        ;   ;  [:div.columns.small-10
        ;   ;   (get-in info [:card :brand])]]
        ;   ; [:div.row
        ;   ;  [:div.columns.small-2.text-right
        ;   ;   [:label
        ;   ;    "Number:"]]
        ;   ;  [:div.columns.small-10
        ;   ;   (str "**** **** **** " (get-in info [:card :last4]))]]
        ;   ; [:div.row
        ;   ;  [:div.columns.small-2.text-right
        ;   ;   [:label "Expires: "]]
        ;   ;  [:div.columns.small-10
        ;   ;   (str (get-in info [:card :exp-month])
        ;   ;        " / "
        ;   ;        (get-in info [:card :exp-year]))]]]
        ;   ]]
        ; [:div.row.column.small-12.medium-6
        ;  [:div.callout
        ;   [:h4.small-caps "Social"]
        ;   [:hr]
        ;   [:div#facebook-connect
        ;    (if (nil? fb-user)
        ;      [:div.row
        ;       [:div.column
        ;        [:a.button.btn-facebook [:i.fa.fa-facebook.fa-fw] "Connect to Facebook"]]]
        ;      [:div.row.collapse
        ;       [:div.columns.small-1
        ;        [:i.fa.fa-facebook.fa-fw]]
        ;       [:div.columns.small-8
        ;        [:strong (:fb-user/name fb-user)]]
        ;
        ;       [:div.columns.small-3
        ;        [:a.link
        ;         [:small
        ;          "Disconnect"]]]])]]]
        ; ]
        ))))

(def ->Settings (om/factory Settings))

