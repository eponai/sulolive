(ns eponai.common.ui.store.account-settings
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common :as c]))

(def form-elements
  {:stripe/business-name           "str-business-name"
   :stripe.address/street          "str-business-address-street"
   :stripe.address/postal          "str-business-address-postal"
   :stripe.address/locality        "str-business-address-locality"
   :stripe.addess/state            "str-business-address-state"
   :stripe.legal-entity/first-name "str-le-first-name"
   :stripe.legal-entity/last-name  "str-le-last-name"
   :stripe.legal-entity/dob-year   "str-le-dob-year"
   :stripe.legal-entity/dob-month  "str-le-dob-month"
   :stripe.legal-entity/dob-day    "str-le-dob-day"})

(defn legal-entity []
  #?(:cljs
     (let [{:stripe.legal-entity/keys [dob-year dob-month dob-day first-name last-name]} form-elements
           dob-y (c/parse-long-safe (utils/input-value-by-id dob-year))
           dob-m (c/parse-long-safe (utils/input-value-by-id dob-month))
           dob-d (c/parse-long-safe (utils/input-value-by-id dob-day))
           fn (utils/input-value-by-id first-name)
           ln (utils/input-value-by-id last-name)

           dob (not-empty (cond-> {}
                                  (some? dob-y)
                                  (assoc :year dob-y)
                                  (some? dob-m)
                                  (assoc :month dob-m)
                                  (some? dob-d)
                                  (assoc :day dob-d)))]
       (not-empty
         (cond-> {}
                 (not-empty fn)
                 (assoc :first_name fn)
                 (not-empty ln)
                 (assoc :last_name ln)
                 (not-empty dob)
                 (assoc :dob dob))))))

(defn personal-details [component]
  (let [{:query/keys [stripe-account]} (om/props component)]
    (my-dom/div
      (->> (css/grid-row)
           (css/align :center))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12 :medium 10 :large 8}))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 4 :medium 3 :large 2}))
            (dom/label nil "Legal Name"))
          (my-dom/div
            (css/grid-column)
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (css/grid-column)
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/first-name form-elements)
                                :placeholder  "First"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/first-name])}))
              (my-dom/div
                (css/grid-column)
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/last-name form-elements)
                                :placeholder  "Last"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/last-name])})))))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 4 :medium 3 :large 2}))
            (dom/label nil "Date of birth"))
          (my-dom/div
            (css/grid-column)
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (css/grid-column)
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/dob-day form-elements)
                                :placeholder  "Day"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/day])}))
              (my-dom/div
                (css/grid-column)
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/dob-month form-elements)
                                :placeholder  "Month"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/month])}))
              (my-dom/div
                (css/grid-column)
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/dob-year form-elements)
                                :placeholder  "Year"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/dob :stripe.legal-entity.dob/year])})))))))))

(defn account-details [component]

  ;(dom/fieldset
  ;  #js {:className "fieldset"}
  ;  (dom/legend nil "Account Details"))
  (my-dom/div
    (->> (css/grid-row)
         (css/align :center))
    (my-dom/div
      (->> (css/grid-column)
           (css/grid-column-size {:small 12 :medium 10 :large 8}))
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column)
               (css/grid-column-size {:small 4 :medium 3 :large 2}))
          (dom/label nil "Business Type"))
        (my-dom/div
          (css/grid-column)
          (dom/input #js {:type "radio" :name "entity-type" :id "entity-type-individual" :value "individual" :defaultChecked true})
          (dom/label #js {:htmlFor "entity-type-individual"} "Individual")
          (dom/input #js {:type "radio" :name "entity-type" :id "entity-type-company" :value "company"})
          (dom/label #js {:htmlFor "entity-type-company"} "Company")
          ))

      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column)
               (css/grid-column-size {:small 4 :medium 3 :large 2}))
          (dom/label nil "Legal Name"))
        (my-dom/div
          (css/grid-column)
          (dom/input #js {:type        "text"
                          :placeholder "Company, LTD"})))

      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column)
               (css/grid-column-size {:small 4 :medium 3 :large 2}))
          (dom/label nil "Business Address"))
        (my-dom/div
          (css/grid-column)
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (css/grid-column)
              (dom/input #js {:type        "text"
                              :id          (:stripe.address/street form-elements)
                              :placeholder "Street"})))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (css/grid-column)
              (dom/input #js {:type        "text"
                              :id          (:stripe.address/postal form-elements)
                              :placeholder "Postal Code"}))
            (my-dom/div
              (css/grid-column)
              (dom/input #js {:type        "text"
                              :id          (:stripe.address/locality form-elements)
                              :placeholder "City"}))
            (my-dom/div
              (css/grid-column)
              (dom/input #js {:type        "text"
                              :id          (:stripe.addess/state form-elements)
                              :placeholder "Province"}))))))))

(defn public-profile [component]
  (let [{:keys [store]} (om/get-computed component)]
    (my-dom/div
      (css/grid-row)
      (my-dom/div
        (->> (css/grid-column)
             (css/text-align :center)
             (css/grid-column-size {:small 6 :medium 4 :large 3}))
        ;(dom/div #js {:className "profile-photo-edit"})
        (photo/circle {:src (get-in store [:store/photo :photo/path])})
        (dom/a #js {:className "button hollow"} "Upload Photo"))
      (my-dom/div
        (css/grid-column)
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right)
                 (css/grid-column-size {:small 4 :medium 3 :large 2}))
            (dom/label nil "Store Name"))
          (my-dom/div
            (css/grid-column)
            (dom/input #js {:type         "text"
                            :defaultValue (:store/name store)})))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right)
                 (css/grid-column-size {:small 4 :medium 3 :large 2}))
            (dom/label nil "Category"))
          (my-dom/div
            (css/grid-column)
            (dom/select nil
                        (dom/option #js {:value "home"} "Home")
                        (dom/option #js {:value "kids"} "Kids")
                        (dom/option #js {:value "men"} "Men")
                        (dom/option #js {:value "women"} "Women")
                        (dom/option #js {:value "unisex"} "Unisex")))
          (my-dom/div
            (css/grid-column)
            (dom/select nil)))))))

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
             legal-entity-params (legal-entity)]
         (when legal-entity-params
           (om/transact! this `[(stripe/update-account ~{:params   {:legal_entity legal-entity-params}
                                                         :store-id (:db/id store)})
                                :query/stripe-account])))))
  (render [this]
    (let [{:query/keys [stripe-account]} (om/props this)
          {:keys [store]} (om/get-computed this)]
      (dom/div #js {:id "sulo-account-settings"}
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)
            (dom/h3 nil "Settings")

            (my-dom/div
              (css/add-class ::css/callout)
              (dom/h4 #js {:className "header"} "Public Profile")
              (public-profile this))

            (my-dom/div
              (css/add-class ::css/callout)
              (dom/h4 #js {:className "header"} "Account Details")
              (account-details this))

            (my-dom/div
              (css/add-class ::css/callout)
              (dom/h4 #js {:className "header"} "Personal Details")
              (personal-details this))))

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
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right))
            (dom/a #js {:className "button"
                        :onClick   #(.save-settings this)} "Save")))))))

(def ->AccountSettings (om/factory AccountSettings))
