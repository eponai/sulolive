(ns eponai.common.ui.store.account-settings
  (:require
    #?(:cljs [cljs.spec :as s]
       :clj [clojure.spec :as s])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.format :as f]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.ui.elements.input-validate :as validate]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common :as c]
    [eponai.common.ui.elements.grid :as grid]))

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

   :store.info/name                "store-info-name"})

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
     (let [{:stripe.legal-entity/keys [first-name last-name]
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
            :stripe.legal-entity/last_name ln
            :stripe.legal-entity/dob dob})))))

(defn personal-details [component]
  (let [{:query/keys [stripe-account]} (om/props component)
        {:keys [validation-error]} (om/get-state component)]
    (grid/row
      (css/align :center)
      (grid/column
        (grid/column-size {:small 12 :medium 10 :large 8})

        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 4 :medium 3 :large 2})
            (dom/label nil "Legal Name"))
          (grid/column
            nil
            (grid/row
              nil
              (grid/column
                nil
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/first-name form-elements)
                                :placeholder  "First"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/first-name])}))
              (grid/column
                nil
                (dom/input #js {:type         "text"
                                :id           (:stripe.legal-entity/last-name form-elements)
                                :placeholder  "Last"
                                :defaultValue (get-in stripe-account [:stripe/legal-entity :stripe.legal-entity/last-name])})))))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 4 :medium 3 :large 2})
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
                  validation-error)))))))))

(defn account-details [component]
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
  (let [{:keys [store]} (om/get-computed component)
        {:store/keys [description]} store]
    (grid/row
      (css/align :center)
      (grid/column
        (->> (css/text-align :center)
             (grid/column-size {:small 6 :medium 4 :large 3}))
        ;(dom/div #js {:className "profile-photo-edit"})
        (photo/store-photo store)
        (dom/a #js {:className "button hollow"} "Upload Photo"))
      (grid/column
        (grid/column-size {:small 12 :large 9})
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "Store Name"))
          (grid/column
            nil
            (my-dom/input {:type         "text"
                            :id           (:store.info/name form-elements)
                            :defaultValue (:store/name store)})))
        ;(my-dom/div
        ;  (css/grid-row)
        ;  (my-dom/div
        ;    (->> (css/grid-column)
        ;         (css/text-align :right)
        ;         (css/grid-column-size {:small 4 :medium 3 :large 2}))
        ;    (dom/label nil "Category"))
        ;  (my-dom/div
        ;    (css/grid-column)
        ;    (dom/select nil
        ;                (dom/option #js {:value "home"} "Home")
        ;                (dom/option #js {:value "kids"} "Kids")
        ;                (dom/option #js {:value "men"} "Men")
        ;                (dom/option #js {:value "women"} "Women")
        ;                (dom/option #js {:value "unisex"} "Unisex")))
        ;  (my-dom/div
        ;    (css/grid-column)
        ;    (dom/select nil)))
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 2})
            (dom/label nil "About"))
          (grid/column
            nil
            (quill/->QuillEditor (om/computed {:content (f/bytes->str description)
                                               :placeholder "What's your story?"}
                                              {:on-editor-created #(om/update-state! component assoc :quill-editor %)}))))))))

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
         (debug "Legal entity: " le)
         (debug "LEGAL ENTITY VALIDATION: " validation-error)
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
    {:active-tab :profile})
  (render [this]
    (let [{:query/keys [stripe-account]} (om/props this)
          {:keys [store]} (om/get-computed this)
          {:keys [active-tab]} (om/get-state this)]
      (dom/div #js {:id "sulo-account-settings"}
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)
            (dom/h3 nil "Settings")

            (menu/horizontal
              (css/add-class :tabs)
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :profile)
                         (css/add-class ::css/is-active))
                (my-dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :profile)}
                  (my-dom/small nil "Profile")))
              (menu/item
                (cond->> (css/add-class :tabs-title)
                         (= active-tab :business-settings)
                         (css/add-class ::css/is-active))
                (my-dom/a
                  {:onClick #(om/update-state! this assoc :active-tab :business-settings)}
                  (my-dom/small nil "Business Settings"))))


            (my-dom/div
               (css/add-class :tabs-content)
              (my-dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :profile)
                         (css/add-class ::css/is-active))
                (public-profile this))
              (my-dom/div
                (cond->> (css/add-class :tabs-panel)
                         (= active-tab :business-settings)
                         (css/add-class ::css/is-active))
                (account-details this)
                (personal-details this)))

            ;(my-dom/div
            ;  (css/add-class ::css/callout)
            ;  (dom/h4 #js {:className "header"} "Public Profile")
            ;  (public-profile this))
            ;
            ;(my-dom/div
            ;  (css/add-class ::css/callout)
            ;  (dom/h4 #js {:className "header"} "Account Details")
            ;  (account-details this))
            ;
            ;(my-dom/div
            ;  (css/add-class ::css/callout)
            ;  (dom/h4 #js {:className "header"} "Personal Details")
            ;  (personal-details this))
            ))

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
        ))))

(def ->AccountSettings (om/factory AccountSettings))
