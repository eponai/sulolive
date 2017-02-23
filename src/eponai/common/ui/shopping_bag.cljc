(ns eponai.common.ui.shopping-bag
  #?(:cljs
     (:require-macros
       [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:cljs
       [cljs.core.async :refer [chan <! put!]])
    ;[clojure.core.async.macros :refer [go]]
    ;[clojure.walk :refer [keywordize-keys]]
    #?(:cljs
       [goog.object :as gobj])
    [om.dom :as dom]
    [eponai.common.ui.dom :as my-dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.client.parser.message :as msg]))

#?(:cljs
   (defn load-checkout [channel]
     (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
         (.addCallback #(put! channel [:stripe-checkout-loaded :success])))))

#?(:cljs
   (defn stripe-token-recieved-cb [component]
     (fn [token]
       (let [clj-token (js->clj token)]
         (debug "Recieved token from Stripe.")
         ;(trace "Recieved token from Stripe: " clj-token)
         (om/transact! component `[(stripe/update-card ~{:token clj-token})
                                   :query/stripe])))))
#?(:cljs
   (defn checkout-loaded? []
     (boolean (gobj/get js/window "StripeCheckout"))))

#?(:cljs
   (defn open-checkout [component email]
     (let [checkout (.configure js/StripeCheckout
                                (clj->js {:key    "pk_test_VhkTdX6J9LXMyp5nqIqUTemM"
                                          :locale "auto"
                                          :token  (stripe-token-recieved-cb component)}))]
       (.open checkout
              #js {:name            "SULO"
                   :email           email
                   :locale          "auto"
                   :allowRememberMe false
                   :opened          #(debug " StripeCheckout did open") ; #(.show-loading component false)
                   :closed          #(debug "StripeCheckout did close.")
                   :panelLabel      ""
                   }))))

(defn items-by-store [items]
  (group-by #(get-in % [:store.item/_skus :store/_items]) items))

(defn compute-item-price [items]
  (reduce + (map :store.item/price items)))

(defn store-element [s]
  (let [{:store/keys [photo] store-name :store/name} s]
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :expanded)
           (css/add-class :store-container)
           (css/align :center)
           )
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 3 :medium 2 :large 1}))
        (photo/circle {:src (:photo/path photo)}))
      (my-dom/div
        (->> (css/grid-column)
             (css/grid-column-size {:small 12})
             (css/text-align :center))
        (dom/div nil (dom/p nil (dom/strong #js {:className "store-name"} store-name))))
      ;(my-dom/div
      ;  (->> (css/grid-column)
      ;       (css/add-class :follow-section)
      ;       (css/text-align :center)
      ;       (css/grid-column-size {:small 12 :medium 4 :large 3}))
      ;  (dom/div nil
      ;    (dom/a #js {:className "button"} "+ Follow")
      ;    (dom/a #js {:className "button hollow"} "Contact")))
      ))
  ;(my-dom/div
  ;  (->> (css/grid-row)
  ;       (css/align :middle)
  ;       (css/add-class :store)
  ;       (css/text-align :right))
  ;
  ;  (my-dom/div
  ;    (->> (css/grid-column))
  ;    (dom/a #js {:href (str "/store/" (:db/id s))}
  ;           (dom/span nil (:store/name s)))
  ;    ;(common/rating-element (:store/rating s) (:store/review-count s))
  ;    )
  ;  (my-dom/div
  ;    (->> (css/grid-column)
  ;         (css/grid-column-size {:small 2 :medium 4}))
  ;    (photo/square
  ;      {:src (:photo/path (:store/photo s))})))
  )

(defn store-checkout-element [component store cart-items]
  (dom/div #js {:className "callout transparent cart-checkout-item"}
    (store-element store)
    ;(my-dom/div
    ;  (css/grid-row))


    (map (fn [sku]
           (let [{:store.item/keys [price photos]
                  product-id       :db/id
                  item-name        :store.item/name} (get sku :store.item/_skus)]
             (my-dom/div
               (->> (css/grid-row)
                    (css/add-class :collapse)
                    (css/align :middle)
                    (css/add-class :callout)
                    (css/add-class :transparent)
                    (css/add-class :item))

               (my-dom/div
                 (->> (css/grid-column)
                      (css/grid-column-size {:small 3 :medium 2 :large 1}))
                 (photo/square
                   {:src (:photo/path (first photos))}))

               ;(my-dom/div
               ;  (->> (css/grid-column))
               ;  (dom/a #js {:className "close-button"} (dom/small nil "x")))
               (my-dom/div
                 (->> (css/grid-column)
                      (css/grid-column-size {:small 8}))

                 (dom/div #js {:className ""}
                             (dom/a #js {:href      (str "/goods/" product-id)
                                         :className "name"}
                                    (dom/span nil item-name)))
                 (dom/div #js {:className ""}
                   (dom/a #js {:href      (str "/goods/" product-id)
                               :className "sku"}
                          (dom/span nil (:store.item.sku/value sku)))))

               (my-dom/div
                 (->> (css/grid-column)
                      (css/align :right)
                      (css/grid-column-size {:small 3 :medium 2 :large 1})
                      (css/grid-column-offset {:small 3 :large 0}))
                 (dom/input #js {:type         "number"
                                 :defaultValue 1}))
               (my-dom/div
                 (->> (css/grid-column)
                      (css/text-align :right)
                      )
                 (dom/div #js {:className ""}
                   (dom/span #js {:className "price"}
                             (utils/two-decimal-price price)))))))
         cart-items)
    (my-dom/div
      (->> (css/grid-row)
           (css/add-class :collapse)
           (css/align :middle)
           (css/add-class :callout)
           (css/add-class :transparent)
           (css/add-class :item)
           (css/text-align :right))
      (let [item-price (compute-item-price (map #(get % :store.item/_skus) cart-items))
            shipping-price 0]
        (my-dom/div
          (css/grid-column)
          (dom/p nil
            (dom/span nil "Total: ")
            (dom/strong nil (utils/two-decimal-price (+ item-price shipping-price))))
          (dom/a #js {:className "button gray"
                      :onClick   #(.checkout component store)} "Checkout")))
      )
    ;(my-dom/div
    ;  (->> (css/grid-column)
    ;       (css/text-align :right)
    ;       (css/add-class :price-info))
    ;  (dom/div #js {:className "total-price-section"}
    ;    (let [item-price (compute-item-price (map #(get % :store.item/_skus) cart-items))
    ;          shipping-price 0]
    ;      (dom/table
    ;        nil
    ;        (dom/tbody
    ;          nil
    ;          (dom/tr nil
    ;                  (dom/td nil "Item Price")
    ;                  (dom/td nil (utils/two-decimal-price item-price)))
    ;          (dom/tr nil
    ;                  (dom/td nil "Shipping")
    ;                  (dom/td nil (utils/two-decimal-price shipping-price)))
    ;          (dom/tr #js {:className "total-price"}
    ;                  (dom/td nil "Total")
    ;                  (dom/td nil (utils/two-decimal-price (+ item-price shipping-price)))))))
    ;    (dom/a #js {:className "button gray"
    ;                :onClick #(.checkout component store)} "Checkout")))
    ))


(defui ShoppingBag
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/cart [{:cart/items [:db/id
                                 :store.item.sku/uuid
                                 :store.item.sku/value
                                 {:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [:store/name
                                                                     {:store/photo [:photo/path]}]}]}]}]}
     {:query/auth [:user/email]}])
  Object
  #?(:cljs
     (checkout
       [this store]
       (let [{:keys [query/cart query/auth]} (om/props this)
             {:keys [cart/items]} cart
             store-items (get (items-by-store items) store)]
         (debug "Checkout store: " store)
         (debug "Items for store: " items)
         (open-checkout this (:user/email auth))
         ;(msg/om-transact! this `[(user/checkout ~{:items (map :store.item.sku/uuid store-items)
         ;                                          :store-id (:db/id store)})])
         )))
  #?(:cljs
     (initLocalState [_]
                     (let [checkout-loaded (checkout-loaded?)]
                       {:checkout-loaded?   checkout-loaded
                        :load-checkout-chan (chan)
                        :is-stripe-loading? (not checkout-loaded)})))
  #?(:cljs
     (componentWillMount [this]
                         (let [{:keys [load-checkout-chan
                                       checkout-loaded?]} (om/get-state this)]
                           (when-not checkout-loaded?
                             (go (<! load-checkout-chan)
                                 (om/update-state! this assoc :checkout-loaded? true :is-stripe-loading? false))
                             (load-checkout load-checkout-chan)))))
  (render [this]
    (let [{:keys [query/cart proxy/navbar]} (om/props this)
          {:keys [cart/items]} cart
          item-count (count items)]

      (debug "CART ITEMS: " cart)
      (debug "GROUPED: " (items-by-store items))
      (common/page-container
        {:navbar navbar :id "sulo-checkout"}
        (my-dom/div
          (-> (css/grid-column)
              css/grid-row)
          (if (not-empty items)
            (dom/div nil
              (dom/h3 nil "Shopping Bag")
              (apply dom/div nil
                     (map (fn [[s its]]
                            (store-checkout-element this s its))
                          (items-by-store items)))
              (when (< 1 (count (items-by-store items)))
                (my-dom/div nil
                            (dom/h3 nil "or checkout all stores")
                            (my-dom/div
                              (css/callout)
                              ;; GRID ROW
                              (my-dom/div
                                (css/grid-row)

                                ;; GRID COLUMN
                                (apply my-dom/div
                                       (css/grid-column)
                                       (map (fn [[s its]]
                                              (my-dom/div
                                                (css/grid-row {:classes [::css/grid-row-align-middle :padded :vertical]})
                                                ;{:classes [::css/grid-row-align-middle :padded :vertical]}
                                                (my-dom/div
                                                  (->> (css/grid-column)
                                                       (css/grid-column-size {:small 2}))
                                                  (photo/square
                                                    {:src (:store/photo s)}))
                                                (my-dom/div
                                                  (css/grid-column)
                                                  (dom/a #js {:href (str "/store/" (:db/id s))}
                                                         (dom/strong #js {:className "store-name"} (:store/name s)))
                                                  (common/rating-element (:store/rating s) (:store/review-count s)))

                                                (my-dom/div
                                                  (-> (css/text-align :right)
                                                      css/grid-column)
                                                  (dom/span nil
                                                            (str (count its) " items"))
                                                  (dom/br nil)
                                                  (dom/strong nil (utils/two-decimal-price (compute-item-price its))))))
                                            (items-by-store items)))

                                ;; GRID COLUMN
                                (my-dom/div
                                  (->> (css/grid-column)
                                       (css/grid-column-size {:small  12
                                                              :medium 4})
                                       (css/text-align :right))
                                  (dom/table nil
                                             (dom/tbody nil
                                                        (dom/tr nil
                                                                (dom/td nil "Item Price")
                                                                (dom/td nil (utils/two-decimal-price (compute-item-price items))))
                                                        (dom/tr nil
                                                                (dom/td nil "Shipping")
                                                                (dom/td nil (utils/two-decimal-price 0)))
                                                        (dom/tr #js {:className "total-price"}
                                                                (dom/td nil (dom/h5 nil "Total"))
                                                                (dom/td nil (dom/h5 nil (utils/two-decimal-price (compute-item-price items)))))))
                                  (dom/a #js {:className "button gray"}
                                         "Checkout All Stores")))))))
            #?(:cljs
                    (dom/div #js {:className "cart-empty callout text-center"}
                      (dom/h3 nil "Your shopping bag is empty")
                      (dom/a #js {:href "/"} (dom/h5 nil "Go to the market - start shopping")))
               :clj (dom/div {:className "cart-loading text-center"}
                      (dom/i {:className "fa fa-spinner fa-spin fa-4x"})))))))))

(def ->ShoppingBag (om/factory ShoppingBag))