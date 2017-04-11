(ns eponai.common.ui.store.account-settings
  (:require
    #?(:cljs [cljs.spec :as s]
       :clj [clojure.spec :as s])
            [eponai.common.ui.dom :as dom]
            [eponai.common.ui.elements.css :as css]
            [eponai.common.format :as f]
            [eponai.common.ui.om-quill :as quill]
            [eponai.common.ui.elements.input-validate :as validate]
    #?(:cljs
       [eponai.web.utils :as utils])
            [om.next :as om :refer [defui]]
            [taoensso.timbre :refer [debug]]
            [eponai.common.ui.elements.menu :as menu]
            [eponai.common.ui.elements.photo :as photo]
            [eponai.common :as c]
            [eponai.common.ui.elements.grid :as grid]
            [eponai.common.ui.icons :as icons]
            [eponai.common.ui.common :as common]))

(def form-elements
  {:stripe/business-name           "str-business-name"
   :stripe.address/street          "str-business-address-street"
   :stripe.address/postal          "str-business-address-postal"
   :stripe.address/locality        "str-business-address-locality"
   :stripe.addess/state            "str-business-address-state"
   :stripe.legal-entity/first-name "str-le-first-name"
   :stripe.legal-entity/last-name  "str-le-last-name"
   :stripe.legal-entity.dob/year   "str-le-dob-year"
   :stripe.legal-entity.dob/month  "str-le-dob-month"
   :stripe.legal-entity.dob/day    "str-le-dob-day"

   :store.info/name                "store-info-name"
   :store.into/tagline             "store-info-tagline"})

(s/def :stripe.legal-entity.dob/day number?)
(s/def :stripe.legal-entity.dob/month number?)
(s/def :stripe.legal-entity.dob/year number?)
(s/def :stripe.legal-entity/first-name (s/and string? #(not-empty %)))
(s/def :stripe.legal-entity/last-name (s/and string? #(not-empty %)))
(s/def :stripe.legal-entity/dob (s/keys :req [:stripe.legal-entity.dob/year
                                              :stripe.legal-entity.dob/month
                                              :stripe.legal-entity.dob/day]))
(s/def :stripe/legal-entity (s/keys :opt [:stripe.legal-entity/dob
                                          :stripe.legal-entity/first_name
                                          :stripe.legal-entity/last_name]))

(defn legal-entity []
  #?(:cljs
     (let [{:stripe.legal-entity/keys     [first-name last-name]
            :stripe.legal-entity.dob/keys [year month day]} form-elements
           y (c/parse-long-safe (utils/input-value-by-id year))
           m (c/parse-long-safe (utils/input-value-by-id month))
           d (c/parse-long-safe (utils/input-value-by-id day))
           fn (utils/input-value-by-id first-name)
           ln (utils/input-value-by-id last-name)

           dob (when (or y m d)
                 {:stripe.legal-entity.dob/year  y
                  :stripe.legal-entity.dob/month m
                  :stripe.legal-entity.dob/day   d})]
       (not-empty
         (f/remove-nil-keys
           {:stripe.legal-entity/first_name fn
            :stripe.legal-entity/last_name  ln
            :stripe.legal-entity/dob        dob})))))

(defn personal-details [component]
  (let [{:query/keys [stripe-account]} (om/props component)
        {:keys [validation-error]} (om/get-state component)]
    (grid/row-column
      (css/align :center)
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Legal Name"))
        (grid/column
          nil
          (grid/row
            nil
            (grid/column
              nil
              (dom/input {:type         "text"
                          :id           (:stripe.legal-entity/first-name form-elements)
                          :placeholder  "First"
                          :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/first-name])}))
            (grid/column
              nil
              (dom/input {:type         "text"
                          :id           (:stripe.legal-entity/last-name form-elements)
                          :placeholder  "Last"
                          :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/last-name])})))))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :medium 3 :large 2})
          (dom/label nil "Date of birth"))
        (grid/column
          nil
          (grid/row
            nil
            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/day
                {:id           (:stripe.legal-entity.dob/day form-elements)
                 :type         "number"
                 :placeholder  "Day"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/day])}
                validation-error)
              ;(dom/input #js {:type         "text"
              ;                :id           (:stripe.legal-entity.dob/day form-elements)
              ;                :placeholder  "Day"
              ;                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/day])})
              )
            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/month
                {:type         "number"
                 :id           (:stripe.legal-entity.dob/month form-elements)
                 :placeholder  "Month"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/month])}
                validation-error))

            (grid/column
              nil
              (validate/input
                :stripe.legal-entity.dob/year
                {:type         "number"
                 :id           (:stripe.legal-entity.dob/year form-elements)
                 :placeholder  "Year"
                 :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/year])}
                validation-error))))))))

(defn account-details [component]
  (grid/row-column
    (css/align :center)
    (dom/div
      (css/grid-row)
      (dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12 :medium 3 :large 2}))
        (dom/label nil "Business Type"))
      (dom/div
        (css/grid-column)
        (dom/input {:type "radio" :name "entity-type" :id "entity-type-individual" :value "individual" :defaultChecked true})
        (dom/label {:htmlFor "entity-type-individual"} "Individual")
        (dom/input {:type "radio" :name "entity-type" :id "entity-type-company" :value "company"})
        (dom/label {:htmlFor "entity-type-company"} "Company")
        ))

    (dom/div
      (css/grid-row)
      (dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12 :medium 3 :large 2}))
        (dom/label nil "Legal Name"))
      (dom/div
        (css/grid-column)
        (dom/input {:type        "text"
                    :placeholder "Company, LTD"})))

    (dom/div
      (css/grid-row)
      (dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12 :medium 3 :large 2}))
        (dom/label nil "Business Address"))
      (dom/div
        (css/grid-column)
        (dom/div
          (css/grid-row)
          (dom/div
            (css/grid-column)
            (dom/input {:type        "text"
                        :id          (:stripe.address/street form-elements)
                        :placeholder "Street"})))
        (dom/div
          (css/grid-row)
          (dom/div
            (css/grid-column)
            (dom/input {:type        "text"
                        :id          (:stripe.address/postal form-elements)
                        :placeholder "Postal Code"}))
          (dom/div
            (css/grid-column)
            (dom/input {:type        "text"
                        :id          (:stripe.address/locality form-elements)
                        :placeholder "City"}))
          (dom/div
            (css/grid-column)
            (dom/input {:type        "text"
                        :id          (:stripe.addess/state form-elements)
                        :placeholder "Province"})))))))

(defn public-profile [component]
  (let [{:keys [store]} (om/get-computed component)
        {:store/keys [description]} store]
    (grid/row
      (css/align :center)
      (grid/column
        (->> (css/text-align :center)
             (grid/column-size {:small 6 :medium 4 :large 3}))
        (photo/store-photo store)
        (dom/a (css/button-hollow) "Upload Photo"))
      (grid/column
        (grid/column-size {:small 12})
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "Store name"))
          (grid/column
            nil
            (dom/input {:type         "text"
                        :id           (:store.info/name form-elements)
                        :defaultValue (:store/name store)})))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "Short description"))
          (dom/div
            (css/grid-column)
            (dom/input
              (->> {:type         "text"
                    :placeholder  "Keep calm and wear pretty jewelry"
                    :id           (:store.info/tagline form-elements)
                    :defaultValue (:store/tagline store)}
                   (css/add-class :tagline-input)))))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "About"))
          (grid/column
            nil
            (quill/->QuillEditor (om/computed {:content     (f/bytes->str description)
                                               :placeholder "What's your story?"}
                                              {:on-editor-created #(om/update-state! component assoc :quill-editor %)}))))))))

(defn shipping-options [component]
  (dom/div
    nil
    ;(grid/row-column
    ;  nil)

    ;(grid/row
    ;  nil
    ;  (grid/column
    ;    (grid/column-size {:small 12 :medium 3 :large 2})
    ;    (dom/label nil "Allow free pickup"))
    ;  (grid/column
    ;    nil
    ;    (dom/div
    ;      (css/add-class :switch)
    ;      (dom/input
    ;        (->> {:id   "shipping-switch"
    ;              :type "checkbox"
    ;              :name "shipping-switch"}
    ;             (css/add-class :switch-input)))
    ;      (dom/label
    ;        (->> {:htmlFor "shipping-switch"}
    ;             (css/add-class :switch-paddle))
    ;        (dom/span (css/add-class :show-for-sr) "Allow Free Pickup")
    ;        (dom/span (css/add-class :switch-inactive) "No")
    ;        (dom/span (css/add-class :switch-active) "Yes")))))
    (grid/row-column
      nil
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Shipping rate"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :defaultValue "0.00"})))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Delivery time (days)"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :step         1
                      :defaultValue "1"}))
        (grid/column
          (css/add-class :shrink)
          (dom/span nil "-"))
        (grid/column
          nil
          (dom/input {:type         "number"
                      :defaultValue "2"})))
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Additional info"))
        (grid/column
          nil
          (dom/input {:type "text"}))))
    ;(dom/div
    ;  (css/grid-column)
    ;  (dom/input {:type "radio" :name "entity-type" :id "entity-type-individual" :value "individual" :defaultChecked true})
    ;  (dom/label {:htmlFor "entity-type-individual"} "Individual")
    ;  (dom/input {:type "radio" :name "entity-type" :id "entity-type-company" :value "company"})
    ;  (dom/label {:htmlFor "entity-type-company"} "Company")
    ;  )
    (grid/row-column
      (css/text-align :right)
      (dom/a
        (css/button)
        (dom/span nil "Save")))))

(defn payouts [component]
  (let [{:keys [modal]} (om/get-state component)
        {:query/keys [stripe-account]} (om/props component)
        {:stripe/keys [external-accounts]} stripe-account]
    (dom/div
      nil
      (grid/row
        nil
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Bank Account"))
        (grid/column
          nil
          (if (not-empty external-accounts)
            (map-indexed
              (fn [i bank-acc]
                (let [{:stripe.external-account/keys [bank-name currency last4 country]} bank-acc]
                  (grid/row
                    nil
                    (grid/column
                      {:classes [:bank-account]}
                      (dom/div {:classes ["bank-detail bank-name"]}
                               (dom/span nil bank-name))
                      (dom/div {:classes ["bank-detail"]}
                               (dom/span nil "/"))
                      (dom/div {:classes ["bank-detail account"]}
                               (map (fn [i]
                                      (dom/span nil "•"))
                                    (take 4 (range)))
                               (dom/span nil last4))
                      (dom/div {:classes ["bank-detail currency"]}
                               (dom/span nil currency))
                      (dom/div {:classes ["bank-detail country"]}
                               (dom/span nil (str "(" country ")"))))
                    (grid/column
                      nil
                      (dom/a
                        (->> {:onClick #(om/update-state! component assoc :modal :bank-account)}
                          (css/button-hollow)) (dom/span nil "Update"))
                      ))))
              external-accounts)
            (dom/a
              (css/button-hollow)
              (dom/span nil "Add bank account..."))))
        (when (= modal :bank-account)
          (common/modal
            {:on-close #(om/update-state! component dissoc :modal)}
            (dom/div
              nil
              (dom/h4 (css/add-class :header) "Your bank account")
              (dom/p (css/text-align :center) (dom/small nil "Your bank account must be a checking account."))
              (grid/row-column
                nil
                (grid/row
                  nil
                  (grid/column
                    (grid/column-size {:small 12 :large 3})
                    (dom/label nil "Currency"))
                  (grid/column
                    nil
                    (dom/select {:defaultValue "usd"}
                                (dom/option {:value "usd"} "USD")
                                (dom/option {:value "cad"} "CAD")
                                (dom/option {:value "sek"} "SEK"))))

                (grid/row
                  nil
                  (grid/column
                    (grid/column-size {:small 12 :large 3})
                    (dom/label nil "Bank country"))
                  (grid/column
                    nil
                    (dom/select {:defaultValue "us"}
                                (dom/option {:value "us"} "United States")
                                (dom/option {:value "ca"} "Canada")
                                (dom/option {:value "se"} "Sweden")))))

              (grid/row-column
                nil
                (grid/row
                  nil
                  (grid/column
                    (grid/column-size {:small 12 :large 3})
                    (dom/label nil "Transit number"))
                  (grid/column
                    nil
                    (dom/input {:placeholder "12345"
                                :type        "text"})))
                (grid/row
                  nil
                  (grid/column
                    (grid/column-size {:small 12 :large 3})
                    (dom/label nil "Institution number"))
                  (grid/column
                    nil
                    (dom/input {:placeholder "000"
                                :type        "text"})))
                (grid/row
                  nil
                  (grid/column
                    (grid/column-size {:small 12 :large 3})
                    (dom/label nil "Account number"))
                  (grid/column
                    nil
                    (dom/input {:type "text"}))))



              (dom/div
                (css/text-align :right)
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(om/update-state! component dissoc :modal)}
                       (css/button)) (dom/span nil "Save")))))))
      (grid/row
        (css/align :middle)
        (grid/column
          (grid/column-size {:small 12 :large 2})
          (dom/label nil "Payout schedule"))

        (grid/column
          (css/add-class :payout-schedule)
          (dom/strong nil "Daily ")
          (dom/span nil " — 7 day rolling basis"))
        (grid/column
          (css/align :right)
          (dom/a
            (->> {:onClick #(om/update-state! component assoc :modal :payout-schedule)}
                 (css/button-hollow))
            (dom/span nil "Change schedule")))
        (when (= modal :payout-schedule)
          (common/modal
            {:on-close #(om/update-state! component dissoc :modal)}
            (dom/div
              nil
              (dom/h4 (css/add-class :header) "Change payout schedule")
              (dom/p nil (dom/small nil "Every day, we'll bundle your transactions for the day and deposit them in your bank account 7 days later. The very first payout Stripe makes to your bank can take up to 10 days to post outside of the US or Canada."))
              (dom/select {:defaultValue "daily"}
                          (dom/option {:value "daily"} "Daily")
                          (dom/option {:value "weekly"} "Weekly")
                          (dom/option {:value "monthly"} "Monthly"))
              (dom/div
                (css/text-align :right)
                (dom/a (->> {:onClick #(om/update-state! component dissoc :modal)}
                            (css/button-hollow)) (dom/span nil "Cancel"))
                (dom/a
                  (->> {:onClick #(om/update-state! component dissoc :modal)}
                       (css/button)) (dom/span nil "Save"))))))))))
(defui AccountSettings
  static om/IQuery
  (query [_]
    [{:query/stripe-account [:stripe/id
                             :stripe/country
                             :stripe/verification
                             :stripe/legal-entity
                             :stripe/external-accounts]}])

  Object
  #?(:cljs
     (save-settings
       [this]
       (let [{:keys [store]} (om/get-computed this)
             {:keys [quill-editor]} (om/get-state this)
             le (legal-entity)
             validation-error (s/explain-data :stripe/legal-entity le)]


         (when (nil? validation-error)
           (om/transact! this `[(store/update-info ~(-> store
                                                        (assoc :store/description (quill/get-HTML quill-editor))
                                                        (assoc :store/name (utils/input-value-by-id (:store.info/name form-elements)))))
                                (stripe/update-account ~{:params   {:legal_entity le}
                                                         :store-id (:db/id store)})
                                :query/store
                                :query/stripe-account]))
         (om/update-state! this assoc :validation-error validation-error))))
  #?(:cljs
     (validate-input
       [this]
       (om/update-state! this assoc :validation-error (s/explain-data :stripe/legal-entity (legal-entity)))))
  (initLocalState [_]
    {:active-tab :shipping})
  (render [this]
    (let [{:query/keys [stripe-account]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)]
      (grid/row-column
        {:id "sulo-account-settings"}
        (dom/h3 nil "Settings")
        (grid/row
          (css/add-class :collapse)
          (grid/column
            (grid/column-size {:small 12 :medium 3 :large 2})
            (menu/vertical
              (css/add-class :tabs)
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :profile)
                         (css/add-class ::css/is-active))
                (dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :profile)}
                  (dom/span nil "General")))
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :shipping)
                         (css/add-class ::css/is-active))
                (dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :shipping)}
                  (dom/span nil "Shipping")))
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :payments)
                         (css/add-class ::css/is-active))
                (dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :payments)}
                  (dom/span nil "Payments")))
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :business-settings)
                         (css/add-class ::css/is-active))
                (dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :business-settings)}
                  (dom/span nil "Business Settings")))))
          (grid/column
            nil
            (dom/div
              (->> (css/add-class :tabs-content)
                   (css/add-class ::css/vertical))
              (dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :profile)
                         (css/add-class ::css/is-active))
                (public-profile this))

              (dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :shipping)
                         (css/add-class ::css/is-active))
                (dom/div
                  (css/callout)
                  (dom/p (css/add-class :header) "Shipping")
                  (shipping-options this)))

              (dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :payments)
                         (css/add-class ::css/is-active))
                (dom/div
                  (css/callout)
                  (dom/p (css/add-class :header) "Payment methods")
                  (dom/div
                    (css/add-class :payment-methods)
                    (icons/visa-card)
                    (icons/mastercard)
                    (icons/american-express))
                  (dom/span nil "SULO Live currently supports the above payment methods. We'll keep working towards adding support for more options."))
                (dom/div
                  (css/callout)
                  (dom/p (css/add-class :header) "Payouts")
                  (payouts this)))


              (dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :business-settings)
                         (css/add-class ::css/is-active))
                (dom/div
                  (css/callout)
                  (dom/p (css/add-class :header) "Business details")
                  (account-details this))
                (dom/div
                  (css/callout)
                  (dom/p (css/add-class :header) "Personal details")
                  (personal-details this)))))))

      ;(dom/fieldset #js {:className "fieldset"}
      ;              (dom/legend nil "Payments")
      ;              (my-dom/div
      ;                (css/grid-row)
      ;                (my-dom/div
      ;                  (->> (css/grid-column)
      ;                       (css/grid-column-size {:small 4 :medium 3 :large 2}))
      ;                  (dom/label nil "Bank Accounts"))
      ;                (my-dom/div
      ;                  (css/grid-column)
      ;                  (menu/vertical
      ;                    nil
      ;                    (map-indexed
      ;                      (fn [i bank-acc]
      ;                        (let [{:stripe.external-account/keys [bank-name currency last4 country]} bank-acc]
      ;                          (menu/item {:classes [:bank-account]}
      ;
      ;                                     (dom/div #js {:className "bank-detail bank-name"}
      ;                                       (dom/span nil bank-name))
      ;                                     ;(dom/div #js {:className "bank-detail"}
      ;                                     ;  (dom/span nil "/"))
      ;                                     (dom/div #js {:className "bank-detail account"}
      ;                                       (map (fn [i]
      ;                                              (dom/span nil "•"))
      ;                                            (take 4 (range)))
      ;                                       (dom/span nil last4))
      ;                                     (dom/div #js {:className "bank-detail currency"}
      ;                                       (dom/span nil currency))
      ;                                     (dom/div #js {:className "bank-detail country"}
      ;                                       (dom/span nil (str "(" country ")"))))))
      ;                      (:stripe/external-accounts stripe-account))))))
      ;(my-dom/div
      ;  (css/grid-row)
      ;  (my-dom/div
      ;    (->> (css/grid-column)
      ;         (css/text-align :right))
      ;    (dom/a #js {:className "button"
      ;                :onClick   #(.save-settings this)} "Save")))
      )))

(def ->AccountSettings (om/factory AccountSettings))
