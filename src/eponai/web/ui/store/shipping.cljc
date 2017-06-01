(ns eponai.web.ui.store.shipping
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.components.select :as select]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]))

(defn add-shipping-destination [component selection]
  (let [{:query/keys [countries]} (om/props component)
        countries-by-continent (group-by :country/continent countries)
        {:keys [selected-countries]} (om/get-state component)]
    (debug "Selection: " selection)
    (if-let [selected-continent (some #(when (and (= (:value selection) (:continent/code %))
                                                  (= (:label selection) (:continent/name %))) %) (keys countries-by-continent))]
      (let [new-countries (remove (fn [c]
                                    (some #(= (:country/code c)
                                              (:country/code %))
                                          selected-countries)) (get countries-by-continent selected-continent))]
        (debug "Selected continent: " selected-continent " with countries: " (into [] (get countries-by-continent selected-continent)))
        (om/update-state! component update :selected-countries into new-countries))
      (do
        (om/update-state! component update :selected-countries conj {:country/code (:value selection) :country/name (:label selection)})
        (debug "Selected country: " selection)))))

(defn remove-country [component country]
  (om/update-state! component update :selected-countries (fn [cs]
                                                           (into [] (remove #(= (:country/code %)
                                                                                (:country/code country))
                                                                            cs)))))

(defn add-shipping-rule-modal [component]
  (let [on-close #(om/update-state! component (fn [st]
                                                (-> st
                                                    (dissoc :modal)
                                                    (assoc :selected-countries []))))
        {:query/keys [countries]} (om/props component)
        countries-by-continent (group-by :country/continent countries)
        {:keys [selected-countries shipping-rule/section shipping-rule/offer-free?]} (om/get-state component)]
    (common/modal
      {
       ;:on-close       on-close
       ;:require-close? true
       }
      (dom/div
        (css/add-class :section-title)
        (cond (= section :shipping-rule.section/destinations)
              (dom/p (css/add-class :header) (dom/span nil "Add destinations"))
              (= section :shipping-rule.section/rates)
              (dom/p (css/add-class :header) (dom/span nil "Add shipping rate")))
        (dom/a
          (css/add-class :close-button {:onClick on-close})
          (dom/span nil "Cancel")))

      (dom/div
        (css/add-classes [:section-container (name (or section ""))])

        (dom/div
          (cond->> (css/add-classes [:section-content :section-content--destinations])
                   (= section :shipping-rule.section/destinations)
                   (css/add-class :is-active))
          (dom/small nil "Select which countries you want to ship to. You'll add rates in the next step.")
          (select/->SelectOne (om/computed {:options (reduce (fn [l [con cs]]
                                                               (into l (into [{:value     (:continent/code con)
                                                                               :label     (:continent/name con)
                                                                               :className "continent"}]
                                                                             (map (fn [c]
                                                                                    {:value     (:country/code c)
                                                                                     :label     (:country/name c)
                                                                                     :className "country"
                                                                                     :disabled  (some #(= (:country/code %)
                                                                                                          (:country/code c))
                                                                                                      selected-countries)})
                                                                                  (sort-by :country/name cs)))))
                                                             []
                                                             countries-by-continent)}
                                           {:on-change #(add-shipping-destination component %)}))
          ;(grid/row
          ;  (css/add-class :collapse)
          ;  (grid/column
          ;    nil
          ;    )
          ;  (grid/column
          ;    (css/add-class :shrink)
          ;    ))

          (when (not-empty selected-countries)
            [
             (menu/vertical
               nil
               (map
                 (fn [c]
                   (menu/item
                     nil
                     (dom/span nil (:country/name c))
                     (dom/a
                       (->> {:onClick #(remove-country component c)}
                            (css/add-classes [:icon :icon-delete])))))
                 (sort-by :country/name selected-countries)))
             (dom/div
               (css/add-class :action-buttons)
               (button/default
                 (cond->> (button/small {:onClick #(om/update-state! component assoc :shipping-rule/section :shipping-rule.section/rates)})
                          (empty? selected-countries)
                          (css/add-class :disabled))
                 (dom/span nil "Next")))

             ])
          ;(dom/select nil
          ;            (map (fn [[continent cs]]
          ;                   (debug "Entry: " {:continent continent
          ;                                     :countries cs})
          ;                   (dom/optgroup
          ;                     {:label (:continent/name continent)}
          ;                     (map (fn [c]
          ;                            (dom/option {:value (:country/code c)} (:country/name c)))
          ;                          cs)))
          ;                 countries-by-continent))
          )
        (dom/div
          (cond->> (css/add-classes [:section-content :section-content--rates])
                   (= section :shipping-rule.section/rates)
                   (css/add-class :is-active))
          ;(menu/horizontal
          ;  (css/add-class :shipping-types)
          ;  (menu/item
          ;    nil
          ;    (dom/a
          ;      {:onClick #(om/update-state! component assoc :shipping-rule/selected-type :flat-rate)}
          ;      (dom/label
          ;        (css/add-class :shipping-type-option)
          ;        (dom/input {:type    "radio"
          ;                    :name    "sulo-select-shipping-type"
          ;                    :onClick #(om/update-state! component assoc :shipping-rule/selected-type :flat-rate)
          ;                    :checked (= selected-type :flat-rate)})
          ;        (dom/span nil "Flat rate"))))
          ;  (menu/item
          ;    nil
          ;    (dom/a
          ;      {:onClick #(om/update-state! component assoc :shipping-rule/selected-type :free-shipping)}
          ;      (dom/label
          ;        (css/add-class :shipping-type-option)
          ;        (dom/input {:type    "radio"
          ;                    :name    "sulo-select-shipping-type"
          ;                    :onClick #(om/update-state! component assoc :shipping-rule/selected-type :free-shipping)
          ;                    :checked (= selected-type :free-shipping)})
          ;        (dom/span nil "Free shipping")))))
          (grid/row
            (css/add-class :collapse)
            (grid/column
              nil
              (dom/label nil "Title")
              (dom/input {:type        "text"
                          :placeholder "e.g USPS Priority, FedEx 3-day"})
              (dom/small nil "This will be visible to your customers at checkout.")))

          (grid/row
            ;(css/add-class :collapse)
            nil
            (grid/column
              nil
              (dom/label nil "Rate first item")
              (dom/input {:type        "number"
                          :defaultValue 0}))
            (grid/column
              nil
              (dom/label nil "Rate additional item")
              (dom/input {:type        "number"
                          :defaultValue 0})))

          ;(dom/p (css/add-class :header) (dom/span nil "Offers"))

          (dom/div
            (css/add-class :switch-container)
            (dom/div
              (css/add-classes [:switch :tiny])
              (dom/input {:classes ["switch-input"]
                          :id      "free-shipping-switch"
                          :type    "checkbox"
                          :name    "free-shipping-switch"
                          :onClick #(om/update-state! component assoc :shipping-rule/offer-free? (.-checked (.-target %)))})
              (dom/label
                (css/add-class :switch-paddle {:htmlFor "free-shipping-switch"})
                (dom/span (css/show-for-sr) "Offer free shipping")))
            (dom/label nil "Offer free shipping"))
          (when offer-free?
            (grid/row
              nil
              (grid/column
                nil
                (dom/label nil "When cart total is above amount")
                (dom/input {:type "number"
                            :defaultValue 0}))
              (grid/column nil)))
          ;<div class= "switch" >
          ;<input class= "switch-input" id= "exampleSwitch" type= "checkbox" name= "exampleSwitch" >
          ;<label class= "switch-paddle" for= "exampleSwitch" >
          ;<span class= "show-for-sr" >Download Kittens</span>
          ;</label>
          ;</div>
          (dom/div
            (css/add-class :action-buttons)
            (button/default-hollow
              (button/small {:onClick #(om/update-state! component assoc :shipping-rule/section :shipping-rule.section/destinations)})
              (dom/i {:classes ["fa fa-chevron-left fa-fw"]})
              (dom/span nil "Back"))
            (button/default
              (button/small)
              (dom/span nil "Save"))))))))

(defui StoreShipping
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/countries [:country/code
                        :country/name
                        {:country/continent [:continent/code
                                             :continent/name]}]}])
  Object
  (initLocalState [_]
    {:selected-countries    []
     :shipping-rule/section :shipping-rule.section/destinations})
  (render [this]
    (let [{:keys [modal selected-countries]} (om/get-state this)]
      (dom/div
        {:id "sulo-shipping-settings"}
        (dom/h1 (css/show-for-sr) "Shipping settings")
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Shipping address"))
        (callout/callout
          nil
          (grid/row
            (css/align :middle)
            (grid/column
              nil
              (dom/p nil (dom/span nil "This is the address from which your products will ship.")))
            (grid/column
              (css/text-align :right)
              (button/default-hollow
                {:onClick #(om/update-state! this assoc :modal :modal/add-shipping-rule)}
                (dom/span nil "Add shipping address")))))
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Shipping rules"))
        (cond (= modal :modal/add-shipping-rule)
              (add-shipping-rule-modal this))
        (callout/callout
          nil
          (grid/row
            (css/align :middle)
            (grid/column
              nil
              (dom/p nil (dom/span nil "Create shipping rules to define where you will ship your products and shipping rates for these orders. ")
                     (dom/a nil (dom/span nil "Learn more"))))
            (grid/column
              (css/text-align :right)
              (button/default-hollow
                {:onClick #(om/update-state! this assoc :modal :modal/add-shipping-rule)}
                (dom/span nil "Add shipping rule")))))))))
