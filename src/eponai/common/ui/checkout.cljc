(ns eponai.common.ui.checkout
  (:require
    #?(:cljs
       [eponai.common.ui.checkout.stripe :as stripe])
    #?(:cljs
       [eponai.common.ui.checkout.google-places :as places])
    [eponai.common.ui.dom :as my-dom]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.utils :as utils]
    #?(:cljs [eponai.web.utils :as web-utils])
    ;[eponai.client.routes :as routes]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]
    [eponai.common :as c]))

(defn compute-item-price [items]
  (reduce + (map #(get-in % [:store.item/_skus :store.item/price]) items)))

(defn store-element [s]
  (debug "Store element: " s)
  (let [{:store/keys [photo] store-name :store/name} s]
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center)
           )
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 3 :medium 2}))
        (photo/circle {:src (:photo/path photo)}))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12})
             (css/text-align :center))
        (dom/div nil (dom/p nil (dom/strong #js {:className "store-name"} store-name)))))))

(defn confirm-element [component]
  (let [{:checkout/keys [shipping payment]} (om/get-state component)
        {:keys [card]} payment
        {:query/keys [cart]} (om/props component)
        {:keys [cart/items]} cart
        item-count (count items)]
    (dom/div nil
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (css/grid-column)
          (dom/h3 nil "Review & Confirm")))
      (dom/div #js {:className "callout"}

        (let [{:address/keys [street1 postal region locality country full-name]} shipping]
          (my-dom/div
            (->> (css/grid-row)
                 (css/align :bottom))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 3 :medium 2}))
              (dom/h4 nil "Ship to")
              (dom/i #js {:className "fa fa-truck fa-2x"}))
            (my-dom/div
              (css/grid-column)
              (dom/div nil
                (dom/div nil (dom/strong nil full-name))
                ;(dom/div nil (dom/span nil street1))
                (dom/div nil (dom/span nil (clojure.string/join ", " (filter some? [ street1 postal locality region country]))))))
            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class :shrink))
              (dom/a #js {:className "button hollow"}
                     (dom/i #js {:className "fa fa-pencil fa-fw"}))))))
      (dom/div #js {:className "callout"}
        (let [{:keys [last4 exp_year exp_month brand]} (:card payment)]
          (my-dom/div
            (->> (css/grid-row)
                 (css/align :bottom))
            (my-dom/div
              (->> (css/grid-column)
                   (css/grid-column-size {:small 3 :medium 2}))
              (dom/h4 nil "Payment")
              (dom/i #js {:className "fa fa-credit-card fa-2x"}))
            (my-dom/div
              (css/grid-column)
              (dom/div nil
                (dom/div nil (dom/span nil brand))
                (dom/div nil
                  (dom/span nil "**** **** **** "
                            (dom/strong nil last4)
                            " "
                            (dom/small nil (str exp_month "/" exp_year))))))
            (my-dom/div
              (->> (css/grid-column)
                   (css/add-class :shrink))
              (dom/a #js {:className "button hollow"}
                     (dom/i #js {:className "fa fa-pencil fa-fw"}))))))
      (dom/div #js {:className "callout"}
        (let [store (:store/_items (:store.item/_skus (first items)))]
          (debug "store: " store)
          (store-element store))
        (dom/div #js {:className "items"}
          (map (fn [sku]
                 (let [{:store.item/keys [price photos]
                        product-id       :db/id
                        item-name        :store.item/name} (get sku :store.item/_skus)]
                   (my-dom/div
                     (->> (css/grid-row)
                          ;(css/add-class :collapse)
                          (css/align :middle)
                          ;(css/add-class :callout)
                          (css/add-class :transparent)
                          (css/add-class :item))

                     (my-dom/div
                       (->> (css/grid-column)
                            (css/grid-column-size {:small 4 :medium 2}))
                       (photo/square
                         {:src (:photo/path (first photos))}))

                     ;(my-dom/div
                     ;  (->> (css/grid-column))
                     ;  (dom/a #js {:className "close-button"} (dom/small nil "x")))
                     (my-dom/div
                       (->> (css/grid-column))

                       (dom/div #js {:className ""}
                         (dom/a #js {
                                     ;:href      (routes/url :product {:product-id product-id})
                                     :className "name"}
                                (dom/span nil item-name)))
                       (dom/div #js {:className ""}
                         (dom/span nil (:store.item.sku/value sku))))

                     (my-dom/div
                       (->> (css/grid-column)
                            (css/align :right)
                            ;(css/add-class :shrink)
                            (css/grid-column-size {:small 3 :medium 2})
                            ;(css/grid-column-offset {:small 3 :large 0})
                            )
                       (dom/input #js {:type         "number"
                                       :defaultValue 1})
                       )
                     (my-dom/div
                       (->> (css/grid-column)
                            (css/text-align :right)
                            (css/add-class :shrink)
                            )
                       (dom/div #js {:className ""}
                         (dom/span #js {:className "price"}
                                   (utils/two-decimal-price price)))))))
               items))
        (dom/div #js {:className "receipt"}
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Subtotal"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price (compute-item-price items)))))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Shipping"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price 5))))
          (my-dom/div
            (css/grid-row)
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil "Discount"))
            ;(my-dom/div
            ;  (->> (css/grid-column)
            ;       (css/text-align :right))
            ;  (dom/a nil "Add code"))
            (my-dom/div
              (->> (css/grid-column)
                   (css/text-align :right))
              (dom/span nil (utils/two-decimal-price 0))
              (dom/br nil)
              (dom/a nil "Add code")))
          (my-dom/div
            (css/add-class :total)
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/strong nil "Total"))
              (my-dom/div
                (->> (css/grid-column)
                     (css/text-align :right))
                (dom/strong nil (utils/two-decimal-price (+ 5 (compute-item-price items)))))))))
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column)
               (css/text-align :right))
          (dom/div #js {:className "button"
                        :onClick #(.place-order component)} "Place Order")))
      )))

(defn geo-locate [component]
  #?(:cljs
     (let [{:keys [autocomplete]} (om/get-state component)]
       (when autocomplete
         (if-let [geolocation (.-geolocation js/navigator)]
           (.getCurrentPosition geolocation
                                (fn [p]
                                  (debug "Position: " p)
                                  (let [geolocation #js {:lat (.. p -coords -latitude)
                                                         :lng (.. p -coords -longitude)}
                                        circle (js/google.maps.Circle. #js {:center geolocation
                                                                            :radius (.. p -coords -accuracy)})]
                                    (.setBounds autocomplete (.getBounds circle))))))))
     )
  )

(def shipping-elements
  {:address/full-name "sulo-shipping-full-name"
   :address/street1   "sulo-shipping-street-address-1"
   :address/street2   "sulo-shipping-street-address-2"
   :address/postal    "sulo-shipping-postal-code"
   :address/locality  "sulo-shipping-locality"
   :address/region    "sulo-shipping-region"
   :address/country   "sulo-shipping-country"})

#?(:cljs
   (defn prefill-address-form [place]
     (let [long-val (fn [k & [d]] (get-in place [k :long] d))
           short-val (fn [k & [d]] (get-in place [k :short] d))
           {:address/keys [street1 postal locality region country]} shipping-elements]
       (set! (.-value (web-utils/element-by-id street1)) (long-val :address))
       (set! (.-value (web-utils/element-by-id postal)) (long-val :postal_code))
       (set! (.-value (web-utils/element-by-id locality)) (long-val :locality))
       (set! (.-value (web-utils/element-by-id country)) (short-val :country))
       (set! (.-value (web-utils/element-by-id region)) (short-val :administrative_area_level_1)))))

(defn shipping-element [component]
  (dom/div nil
    (dom/h3 nil "Shipping")
    (my-dom/div
      (->> (css/add-class ::css/callout))
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column))
          (dom/label nil "Full name")
          (dom/input #js {:id           (:address/full-name shipping-elements)
                          :type         "text"
                          :name         "name"
                          :autocomplete "name"}))
        )
      (my-dom/div
        (css/grid-row)
        (my-dom/div
          (->> (css/grid-column))
          (dom/label nil "Address")
          (dom/input #js {:id   "auto-complete"
                          :type "text"
                          :onFocus #(geo-locate component)})))
      (dom/hr nil)
      (dom/div nil
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column))
            (dom/label nil "Country")
            (dom/select #js {:id           (:address/country shipping-elements)
                             :name         "ship-country"
                             :autocomplete "shipping country"
                             :required     true}
                        (dom/option #js {:value "CA"} "Canada")
                        (dom/option #js {:value "SE"} "Sweden")
                        (dom/option #js {:value "US"} "United States"))))


        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 12 :medium 8}))
            (dom/label nil "Street Address")
            (dom/input #js {:id           (:address/street1 shipping-elements)
                            :type         "text"
                            :name         "ship-address"
                            :autocomplete "shipping address-line2"
                            :required     true}))
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 12 :medium 4}))
            (dom/label nil "Apt/Suite/Other")
            (dom/input #js {:id           (:address/street2 shipping-elements)
                            :type         "text"
                            :name         "ship-address"
                            :autocomplete "shipping address-line2"})))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 12 :large 4}))
            (dom/label nil "City")
            (dom/input #js {:id           (:address/locality shipping-elements)
                            :type         "text"
                            :name         "ship-city"
                            :autocomplete "shipping locality"
                            :required     true}))
          (my-dom/div
            (->> (css/grid-column))
            (dom/label nil "Province")
            (dom/select #js {:id           (:address/region shipping-elements)
                             :name         "ship-state"
                             :autocomplete "shipping region"
                             :defaultValue ""}
                        (dom/option #js {:disabled true} "Select Province")
                        (dom/option #js {:value "bc"} "British Columbia")))
          (my-dom/div
            (css/grid-column)
            (dom/label nil "Postal code")
            (dom/input #js {:id           (:address/postal shipping-elements)
                            :type         "text"
                            :name         "ship-zip"
                            :autocomplete "shipping postal-code"
                            :required     true}))
          )
        )
      )
    (my-dom/div (css/text-align :right)
                (dom/a #js {:className "button"
                            :onClick #(.save-shipping component)}
                       "Next")))
  )

(defn payment-element [component & [{:keys [sources]}]]
  (let [{:keys [payment-error new-card? card]} (om/get-state component)]
    #?(:cljs
       (when-not card
         (om/update-state! component assoc :card (stripe/mount-payment-form {:element-id "sulo-card-element"}))))
    (dom/div nil
      (dom/h3 nil "Payment")
      (my-dom/div
        (css/add-class ::css/callout)
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column))
            (dom/label #js {:htmlFor "sulo-card-element"
                            :className (when-not new-card? "hide")} "Card")
            (dom/div #js {:id "sulo-card-element" :className (when-not new-card? "hide")})
            (dom/div #js {:id        "card-errors"
                          :className "text-center"}
              (dom/small nil payment-error))))
        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (->> (css/grid-column))
            (dom/a #js {:onClick   #(om/update-state! component assoc :new-card? true)
                        :className "button hollow"} "Add card"))))
      (my-dom/div (css/text-align :right)
                  (dom/a #js {:className "button"
                              :onClick #(.save-payment component)}
                         "Next")))))

(defui Checkout
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/value
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [:db/id
                                                                     :store/name
                                                                     {:store/photo [:photo/path]}]}]}]}]}
     :query/current-route
     {:query/auth [:user/email]}
     :query/messages])
  Object
  #?(:cljs
     (place-order
       [this]
       (let [{:query/keys [current-route cart auth]} (om/props this)
             {:checkout/keys [shipping payment]} (om/get-state this)
             {:keys [card source]} payment
             {:keys [route-params]} current-route
             {:keys [store-id]} route-params]
         (let [items (filter #(= (c/parse-long store-id) (get-in % [:store.item/_skus :store/_items :db/id])) (:cart/items cart))]
           (msg/om-transact! this `[(user/checkout ~{:source   source
                                                     :shipping shipping
                                                     :items    (map :store.item.sku/uuid items)
                                                     :store-id (c/parse-long store-id)})])))))
  #?(:cljs
     (save-payment
       [this]
       (let [{:keys [card]} (om/get-state this)]
         (stripe/create-token
           card
           (fn [token]
             (debug "Got token: " token)
             (om/update-state! this assoc :checkout/payment (stripe/token->payment token)))
           (fn [error]
             (debug "Got error: " error)
             (om/update-state! this assoc :payment-error (.-message error)))))))
  #?(:cljs
     (save-shipping
       [this]
       (let [{:address/keys [street1 street2 postal locality region country full-name]} shipping-elements
             shipping {:address/full-name (web-utils/input-value-by-id full-name)
                       :address/street1   (web-utils/input-value-by-id street1)
                       :address/street2   (web-utils/input-value-by-id street2)
                       :address/locality  (web-utils/input-value-by-id locality)
                       :address/country   (web-utils/input-value-by-id country)
                       :address/region    (web-utils/input-value-by-id region)
                       :address/postal    (web-utils/input-value-by-id postal)}]
         (om/update-state! this assoc :checkout/shipping shipping))))
  (componentDidMount [this]
    (debug "Stripe component did mount")
    #?(:cljs
       (let [card (stripe/mount-payment-form {:element-id "sulo-card-element"})
             autocomplete (places/mount-places-address-autocomplete {:element-id "auto-complete"
                                                                     :on-change  (fn [place]
                                                                                   (prefill-address-form place))})]
         (om/update-state! this assoc :card card :autocomplete autocomplete))))

  (initLocalState [_]
    {:checkout/shipping nil
     :checkout/payment nil})
  (render [this]
    (let [{:proxy/keys [navbar]} (om/props this)
          {:checkout/keys [shipping payment]} (om/get-state this)
          progress (cond (nil? shipping) 1
                         (nil? payment) 2
                         :else 3)]
      (debug "Progress: " progress " " (str (/ progress 3) "%"))
      ;(debug "Items: " checkout-items)
      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (my-dom/div
          (->> (css/grid-row)
               (css/align :center)
               (css/add-class :collapse))
          (my-dom/div
            (->> (css/grid-column)
                 (css/grid-column-size {:small 12 :medium 8 :large 8}))
            (dom/div #js {:className "progress"}
              (dom/div #js {:className "progress-meter"
                            :style     #js {:width (str (int (* 100 (/ progress 3))) "%")}}))
            ;(shipping-element this )
            (condp = progress
              1 (shipping-element this)
              2 (payment-element this)
              3 (confirm-element this))))))))

(def ->Checkout (om/factory Checkout))