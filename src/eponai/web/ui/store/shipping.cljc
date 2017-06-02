(ns eponai.web.ui.store.shipping
  (:require
    [clojure.spec :as s]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.components.select :as select]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [taoensso.timbre :refer [debug]]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.parser.message :as msg]
    [clojure.string :as string]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.common :as c]))

(def form-inputs
  {:shipping.rate/title       "shipping.rate.title"
   :shipping.rate/first       "shipping.rate.first"
   :shipping.rate/additional  "shipping.rate.additional"
   :shipping.rate/offer-free? "shipping.rate.offer-free?"
   :shipping.rate/free-above  "shipping.rate.free-above"})

(s/def :shipping.rate/title (s/and string? #(not-empty %)))
(s/def :shipping.rate/first (s/or :pos #(pos? (c/parse-long-safe %)) :zero #(zero? (c/parse-long-safe %))))
(s/def :shipping.rate/additional (s/or :pos #(pos? (c/parse-long-safe %)) :zero #(zero? (c/parse-long-safe %))))
(s/def :shipping.rate/free-above (s/or :value #(and (number? (c/parse-long-safe %)) (<= 0 (c/parse-long-safe %))) :empty nil?))

(s/def :shipping.rule/rate (s/keys :req [:shipping.rate/first :shipping.rate/additional :shipping.rate/title]
                                   :opt [:shipping.rate/free-above]))
(s/def :shipping.rule/rates (s/coll-of :shipping.rule/rate))
(s/def ::shipping (s/keys :req [:shipping.rule/rates]))

(defn validate
  [spec m]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (some #(get form-inputs %) p))
                             (map :path problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))

(defn add-shipping-destination [component selection used-country-codes]
  (let [{:query/keys [countries]} (om/props component)
        countries-by-continent (group-by :country/continent countries)
        {:keys [selected-countries]} (om/get-state component)]
    (debug "Selection: " selection)
    (if-let [selected-continent (some #(when (and (= (:value selection) (:continent/code %))
                                                  (= (:label selection) (:continent/name %))) %) (keys countries-by-continent))]
      (let [new-countries (remove (fn [c]
                                    (or (some #(= (:country/code %)
                                                  (:country/code c))
                                              selected-countries)
                                        (contains? used-country-codes (:country/code c)))) (get countries-by-continent selected-continent))]
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
                                                    (assoc :selected-countries []
                                                           :shipping-rule/section :shipping-rule.section/destinations))))
        {:query/keys [countries store]} (om/props component)
        countries-by-continent (group-by :country/continent countries)
        used-countries (reduce #(into %1 (:shipping.rule/destinations %2)) [] (get-in store [:store/shipping :shipping/rules]))
        {:keys [input-validation selected-countries shipping-rule/section shipping-rule/offer-free?]} (om/get-state component)
        used-country-codes (set (map :country/code used-countries))]
    (debug "Used ccountries: " used-countries)
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
                                                                                     :disabled  (or (some #(= (:country/code %)
                                                                                                              (:country/code c))
                                                                                                          selected-countries)
                                                                                                    (contains? used-country-codes (:country/code c)))})
                                                                                  (sort-by :country/name cs)))))
                                                             []
                                                             countries-by-continent)}
                                           {:on-change #(add-shipping-destination component % used-country-codes)}))
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
              (v/input {:type        "text"
                        :id          (:shipping.rate/title form-inputs)
                        :placeholder "e.g USPS Priority, FedEx 3-day"}
                       input-validation)
              (dom/small nil "This will be visible to your customers at checkout.")))

          (grid/row
            ;(css/add-class :collapse)
            nil
            (grid/column
              nil
              (dom/label nil "Rate first item")
              (v/input {:type         "number"
                        :id           (:shipping.rate/first form-inputs)
                        :defaultValue 0
                        :min          0}
                       input-validation))
            (grid/column
              nil
              (dom/label nil "Rate additional item")
              (v/input {:type         "number"
                        :id           (:shipping.rate/additional form-inputs)
                        :defaultValue 0
                        :min          0}
                       input-validation)))

          ;(dom/p (css/add-class :header) (dom/span nil "Offers"))

          (dom/div
            (css/add-class :switch-container)
            (dom/div
              (css/add-classes [:switch :tiny])
              (dom/input {:classes ["switch-input"]
                          :id      (:shipping.rate/offer-free? form-inputs)
                          :type    "checkbox"
                          :name    "free-shipping-switch"
                          :onClick #(om/update-state! component assoc :shipping-rule/offer-free? (.-checked (.-target %)))})
              (dom/label
                (css/add-class :switch-paddle {:htmlFor (:shipping.rate/offer-free? form-inputs)})
                (dom/span (css/show-for-sr) "Offer free shipping")))
            (dom/label nil "Offer free shipping"))
          (when offer-free?
            (grid/row
              nil
              (grid/column
                nil
                (dom/label nil "When cart total is above amount")
                (v/input {:type         "number"
                          :id           (:shipping.rate/free-above form-inputs)
                          :defaultValue 0
                          :min          0}
                         input-validation))
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
              (dom/i {:classes ["fa fa-chevron-left  fa-fw"]})
              (dom/span nil "Back"))
            (button/default
              (button/small {:onClick #(.save-shipping-rule component)})
              (dom/span nil "Save"))))))))

(defui StoreShipping
  static om/IQuery
  (query [_]
    [:query/current-route
     :query/messages
     {:query/countries [:country/code
                        :country/name
                        {:country/continent [:continent/code
                                             :continent/name]}]}
     {:query/store [{:store/shipping [{:shipping/rules [:shipping.rule/title
                                                        {:shipping.rule/rates [:shipping.rate/title
                                                                               :shipping.rate/first
                                                                               :shipping.rate/additional
                                                                               :shipping.rate/free-above]}
                                                        {:shipping.rule/destinations [:country/code :country/name]}]}]}]}])
  Object
  (initLocalState [_]
    {:selected-countries    []
     :shipping-rule/section :shipping-rule.section/destinations})
  (save-shipping-rule [this]
    #?(:cljs
       (let [rate-first (utils/input-value-by-id (:shipping.rate/first form-inputs))
             rate-additional (utils/input-value-by-id (:shipping.rate/additional form-inputs))
             offer-free? (utils/input-checked-by-id? (:shipping.rate/offer-free? form-inputs))
             free-above (when offer-free? (utils/input-value-by-id (:shipping.rate/free-above form-inputs)))

             rule-title (utils/input-value-by-id (:shipping.rate/title form-inputs))
             {:query/keys [current-route store]} (om/props this)
             {:keys [selected-countries]} (om/get-state this)

             input-map {:shipping.rule/destinations selected-countries
                        :shipping.rule/rates        [{:shipping.rate/first      rate-first
                                                      :shipping.rate/title      rule-title
                                                      :shipping.rate/additional rate-additional
                                                      :shipping.rate/free-above free-above}]}
             input-validation (validate ::shipping input-map)]
         (debug "input: " input-map)
         (debug "validation: " input-validation)
         (when (nil? input-validation)
           (msg/om-transact! this [(list 'store/save-shipping-rule {:shipping-rule input-map
                                                                    :store-id      (:store-id (:route-params current-route))})
                                   :query/store]))
         (om/update-state! this assoc :input-validation input-validation)))
    )
  (componentDidUpdate [this _ _]
    (let [rule-message (msg/last-message this 'store/save-shipping-rule)]
      (when (and (msg/final? rule-message)
                 (msg/success? rule-message))
        (msg/clear-one-message! this 'store/save-shipping-rule)
        (om/update-state! this (fn [st]
                                 (-> st
                                     (dissoc :modal)
                                     (assoc :selected-countries []
                                            :shipping-rule/section :shipping-rule.section/destinations)))))))
  (render [this]
    (let [{:keys [modal selected-countries]} (om/get-state this)
          {:query/keys [store]} (om/props this)]
      (debug "Got props: " (om/props this))
      (dom/div
        {:id "sulo-shipping-settings"}
        (dom/h1 (css/show-for-sr) "Shipping settings")
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Shipping address"))
        (callout/callout
          (css/add-classes [:section-container :section-container--shipping-address])
          (grid/row
            (css/add-class :collapse)
            (grid/column
              nil
              (dom/p nil (dom/span nil "This is the address from which your products will ship.")))
            (grid/column
              (->> (css/text-align :right)
                   (grid/column-size {:small 12 :medium 4}))
              (button/default-hollow
                {:onClick #(om/update-state! this assoc :modal :modal/add-shipping-rule)}
                (dom/span nil "Add shipping address")))))
        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Shipping rules"))
        (cond (= modal :modal/add-shipping-rule)
              (add-shipping-rule-modal this))
        (callout/callout
          (css/add-classes [:section-container :section-container--shipping-rules])
          (grid/row
            (css/add-class :collapse)
            (grid/column
              nil
              (dom/p nil
                     (dom/span nil "Create shipping rules to define where you will ship your products and shipping rates for these orders. ")
                     (dom/a nil (dom/span nil "Learn more"))))
            (grid/column
              (->> (css/text-align :right)
                   (grid/column-size {:small 12 :medium 4}))
              (button/default-hollow
                {:onClick #(om/update-state! this assoc :modal :modal/add-shipping-rule)}
                (dom/span nil "Add shipping rule"))))

          (map
            (fn [sr]
              (let [{:shipping.rule/keys [destinations rates]} sr
                    sorted-dest (sort-by :country/name destinations)]
                (dom/div
                  (css/add-class :shipping-rule-card)
                  (grid/row
                    (css/add-classes [:shipping-rule-card-header :collapse])
                    (grid/column
                      (css/add-class :shipping-rule-card-header--title)
                      (dom/label nil
                                 (string/join ", " (map :country/name (take 3 sorted-dest))))
                      (when (< 0 (- (count destinations) 3))
                        (dom/label nil (str " & " (- (count destinations) 3) " more"))))
                    (grid/column
                      (->> (css/add-class :shipping-rule-card-header--add-new)
                           (grid/column-size {:small 12 :medium 4}))
                      (button/default-hollow
                        (button/small) (dom/span nil "Add shipping rate"))))
                  (menu/vertical
                    nil
                    (map (fn [r]
                           (menu/item
                             nil
                             (table/table
                               nil
                               (table/thead
                                 nil
                                 (table/thead-row
                                   nil
                                   (table/th nil "Name")
                                   (table/th nil "Rate")
                                   (table/th nil "Free shipping")))
                               (table/tbody
                                 nil
                                 (table/tbody-row
                                   nil
                                   (table/td nil (dom/span nil (:shipping.rate/title r)))
                                   (table/td
                                     nil
                                     (dom/span nil (ui-utils/two-decimal-price (:shipping.rate/first r))))
                                   (table/td
                                     nil
                                     (let [free-above (:shipping.rate/free-above r)]
                                       (if (some? free-above)
                                         (dom/span nil (str "> " (ui-utils/two-decimal-price free-above)))
                                         (dom/span nil "No"))))))))) rates))
                  ;(dom/div
                  ;  (css/add-class :shipping-rule-card-footer)
                  ;  (button/default-hollow
                  ;    (button/small) (dom/span nil "Add shipping rate")))
                  )))
            (get-in store [:store/shipping :shipping/rules])))))))
