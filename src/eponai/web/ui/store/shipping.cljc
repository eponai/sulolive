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
        {:keys [selected-countries]} (om/get-state component)]
    (common/modal
      {
       ;:on-close       on-close
       :require-close? true}

      (dom/div
        nil
        (dom/div
          (css/add-class :section-title)
          (dom/p (css/add-class :header) (dom/span nil "Add destinations"))
          (dom/a
            (css/add-class :close-button {:onClick on-close})
            (dom/span nil "Cancel")))



        (select/->SelectOne (om/computed {:options (reduce (fn [l [con cs]]
                                                             (into l (into [{:value     (:continent/code con)
                                                                             :label     (:continent/name con)
                                                                             :className "continent"}]
                                                                           (map (fn [c]
                                                                                  {:value     (:country/code c)
                                                                                   :label     (:country/name c)
                                                                                   :className "country"
                                                                                   :disabled (some #(= (:country/code %)
                                                                                                       (:country/code c))
                                                                                                   selected-countries)})
                                                                                (sort-by :country/name cs)))))
                                                           []
                                                           countries-by-continent)}
                                         {:on-change #(add-shipping-destination component %)}))
        (grid/row
          (css/add-class :collapse)
          (grid/column
            nil
            (dom/p nil (dom/small nil "Select which countries you want to ship to. You'll add rates in the next step.")))
          (grid/column
            (css/add-class :shrink)
            (dom/div
              (css/add-class :action-buttons)
              (button/button-small
                (when (empty? selected-countries)
                  (css/add-class :disabled))
                (dom/span nil "Next")))))

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
        ))))

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
    {:selected-countries []})
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
