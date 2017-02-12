(ns eponai.common.routes)

(def store-routes
  {""           :store
   "/dashboard" {""          :store-dashboard
                 "/products" {""                         :store-dashboard/product-list
                              ["/" [#"create" :action]]  :store-dashboard/create-product
                              ["/" [#"\d+" :product-id]] :store-dashboard/product}
                 "/orders"   {""                       :store-dashboard/orders
                              ["/" [#"\d+" :order-id]] :store-dashboard/order}
                 "/stream"   :store-dashboard/stream}})

(def routes
  ["/" {""                            :index
        "coming-soon"                 :coming-soon
        "sell/coming-soon"            :sell-soon
        "store/new"                   :new-store
        ["store/" [#"\d+" :store-id]] store-routes
        "goods"                       :goods
        ["goods/" :product-id]        :product
        "streams"                     :streams
        "checkout"                    :checkout
        "shopping-bag"                :shopping-bag
        "business"                    :business
        ["profile/" :user-id]         :profile
        "settings"                    :settings}])

;; #################################################
;; WHERE IS THE MAPPING BETWEEN ROUTE AND COMPONENT?
;;
;; See namespace: eponai.common.ui.router <<------
;;
;; We need this namespace to be requirable from
;; components.
;; #################################################

;; Used on the client side to avoid us using routing for kick-off lab stuff.
(defn without-coming-soon-route [routes]
  (update routes 1 dissoc "coming-soon" "sell/coming-soon"))

(defn normalize-route [])