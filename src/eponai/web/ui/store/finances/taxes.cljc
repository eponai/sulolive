(ns eponai.web.ui.store.finances.taxes
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.switch-input :as switch-input]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.button :as button]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]
    [eponai.common.ui.common :as common]))

(def form-inputs
  {::automatic-tax? "sulo.switch.tax.automatic"

   ::tax-rate       "sulo.tax.rate"
   ::tax-shipping?  "sulo.tax-shipping"})

(defn render-tax-rate-modal [component]
  (let [{:query/keys [store]} (om/props component)
        {:tax/keys [tax-shipping?]} (om/get-state component)
        tax-rule (first (get-in store [:store/tax :tax/rules]))
        on-close #(om/update-state! component dissoc :modal :tax/tax-shipping?)]


    (common/modal
      {:on-close on-close
       :size     "tiny"}
      (dom/h4 nil "Change tax rate")
      (dom/label nil "Tax rate in %")
      (dom/input {:type         "number"
                  :id           (::tax-rate form-inputs)
                  :defaultValue (* 100 (:tax.rule/rate tax-rule))
                  :step         0.01
                  :placeholder  "12.00"})
      (dom/div
        nil
        (dom/input {:type     "checkbox"
                    :checked  (first (keep identity [tax-shipping? (:tax.rule/include-shipping? tax-rule)]))
                    :onChange #(om/update-state! component assoc :tax/tax-shipping? (.-checked (.-target %)))
                    :id       (::tax-shipping? form-inputs)})

        (dom/label {:htmlFor (::tax-shipping? form-inputs)} "Apply tax to shipping")
        )
      (dom/div
        (css/add-class :action-buttons)
        (button/cancel {:onClick on-close})
        (button/save {:onClick #(do (.save-custom-tax component)
                                    (on-close))})))))

(defui FinancesTaxes
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/store [:db/id
                    {:store/tax [:tax/automatic?
                                 {:tax/rules [:tax.rule/rate
                                              :tax.rule/include-shipping?]}]}]}
     :query/messages])

  Object
  (automate-taxes [this automate?]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)
             taxes (:store/tax store)]
         (debug "Tax rules: " (:store/tax store))

         (msg/om-transact! this [(list 'store/update-tax {:tax      {:db/id          (:db/id taxes)
                                                                     :tax/automatic? automate?}
                                                          :store-id (:db/id store)})
                                 :query/store]))))
  (save-custom-tax [this]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)
             taxes (:store/tax store)

             rate (web-utils/input-value-by-id (::tax-rate form-inputs))
             include-shipping? (web-utils/input-checked-by-id? (::tax-shipping? form-inputs))]
         (msg/om-transact! this [(list 'store/update-tax {:tax      (merge taxes {:tax/rules [{:tax.rule/rate              rate
                                                                                               :tax.rule/include-shipping? include-shipping?}]})
                                                          :store-id (:db/id store)})
                                 :query/store]))))

  (componentDidMount [this]
    #?(:cljs
       (let [{:query/keys [store]} (om/props this)
             taxes (:store/tax store)]
         (when-let [switch-element (web-utils/element-by-id (::automatic-tax? form-inputs))]
           (set! (.-checked switch-element) (boolean (:tax/automatic? taxes)))))))

  (render [this]
    (let [{:query/keys [store]} (om/props this)
          {:keys [modal]} (om/get-state this)
          taxes (:store/tax store)
          tax-rule (first (:tax/rules taxes))]

      (dom/div
        nil
        (cond (= modal :modal/change-tax)
              (render-tax-rate-modal this))

        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Sales tax"))

        (callout/callout
          nil
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  (grid/column-size {:small 12 :medium 7})
                  (dom/label nil "Automatically calculate tax")
                  (dom/p nil (dom/small nil "Automatically calculate tax rate based on the postal code of your business and the location of your customer within Canada. No tax will be applied on international orders.")))
                (grid/column
                  (css/text-align :right)
                  (switch-input/switch
                    {:title    "Automatically calculate tax"
                     :id       (::automatic-tax? form-inputs)
                     :onChange #(.automate-taxes this (.-checked (.-target %)))}
                    (dom/span (css/add-class :switch-inactive) "No")
                    (dom/span (css/add-class :switch-active) "Yes")))))))
        (callout/callout
          (when (:tax/automatic? taxes)
            (css/add-class :disabled))
          (menu/vertical
            (css/add-class :section-list)

            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})

                  (dom/label nil "Tax rate")
                  (dom/p nil (dom/small nil "Select tax rate to charge your customers at checkout, or set the rate to 0 if you include sales tax in your prices. Tax is applied at checkout if the customer is located in Canada.")))
                (grid/column
                  (->> (grid/column-size {:small 12 :medium 6})
                       (css/text-align :right))

                  (dom/p nil (dom/span nil (c/two-decimal-percent (:tax.rule/rate tax-rule 0)))
                         (dom/br nil)
                         ;(dom/small nil "Include international")
                         ;(dom/br nil)
                         (if (:tax.rule/include-shipping? tax-rule)
                           (dom/small nil "Include shipping")
                           (dom/small nil "Exclude shipping")))
                  (button/store-setting-default
                    (cond->> {:onClick #(om/update-state! this assoc :modal :modal/change-tax)}
                             (:tax/automatic? taxes)
                             (css/add-class :disabled))
                    (dom/span nil "Change tax")))))))
        (dom/p nil (dom/small nil
                              (dom/strong nil "Note: ")
                              (dom/em nil "While SULO Live provides a means to apply taxes to your transactions, we do not warrant that these tax amounts will fully satisfy your sales and use tax reporting obligations. For a final determination on these matters, please seek assistance from your tax advisor.")))
        ))))

(def ->FinancesTaxes (om/factory FinancesTaxes))
