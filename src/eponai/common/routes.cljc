(ns eponai.common.routes)

(def store-routes
  {""           :store
   "/dashboard" {""          :store-dashboard
                 "/products" {""                                :store-dashboard/product-list
                              ["/" [#"create" :action]]         :store-dashboard/create-product
                              ["/" [#"(\d|\w|-)+" :product-id]] :store-dashboard/product}
                 "/orders"   {""                        :store-dashboard/order-list
                              ["/" [#"create" :action]] :store-dashboard/create-order
                              ["/" [#"\w+" :order-id]]  :store-dashboard/order}
                 "/stream"   :store-dashboard/stream}})

(def user-routes
  {""        :user
   "/orders" {""                       :user/order-list
              ["/" [#"\w+" :order-id]] :user/order}})
(def routes
  ["/" {""                            :index
        "coming-soon"                 :coming-soon
        "sell/coming-soon"            :sell-soon
        "store/new"                   :new-store
        ["store/" [#"\d+" :store-id]] store-routes
        "products"                    :products
        ["product/" :product-id]      :product
        "streams"                     :streams
        ["checkout/" :store-id]        :checkout
        "shopping-bag"                :shopping-bag
        "business"                    :business
        ["user/" [#"\d+" :user-id]]   user-routes
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