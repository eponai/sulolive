(ns eponai.common.routes
  (:require [bidi.bidi :as bidi]
            [taoensso.timbre :refer [debug error]]
            [eponai.common.auth :as auth]))

(def store-routes
  {""           :store
   "/dashboard" {""          :store-dashboard
                 "/settings" {""          :store-dashboard/settings
                              "/shipping" :store-dashboard/settings#shipping
                              "/payments" :store-dashboard/settings#payments
                              "/payouts"  :store-dashboard/settings#payouts
                              "/business" :store-dashboard/settings#business
                              "/activate" :store-dashboard/settings#activate}
                 "/products" {""                                :store-dashboard/product-list
                              "/create"                         :store-dashboard/create-product
                              ["/" [#"(\d|\w|-)+" :product-id]] :store-dashboard/product}
                 "/orders"   {""                        :store-dashboard/order-list
                              ["/" [#"create" :action]] :store-dashboard/create-order
                              ["/" [#"\w+" :order-id]]  :store-dashboard/order}
                 "/stream"   :store-dashboard/stream}
   "/nav"       {"/about"                          :store/about
                 ["/" [#"(\d|\w|-)+" :navigation]] :store/navigation}})

(def user-routes
  {""         :user
   "/orders"  {""                       :user/order-list
               ["/" [#"\w+" :order-id]] :user/order}
   "/profile" :user/profile})

(def product-routes
  {""                         :products
   ["/" [#"\d+" :product-id]] :product})

(def help-routes
  {""         :help
   "/encoding" :help/encoding})

(def routes
  ["/" {""                            :index
        "coming-soon"                 :coming-soon
        "sell/coming-soon"            :sell-soon
        "live"                        :live
        "store/new"                   :new-store
        "store"                       :index
        ["store/" [#"\d+" :store-id]] store-routes
        "products"                    product-routes
        "categories"                  {""              :products/all-categories
                                       ["/" :category] :products/categories}
        "help"                        help-routes
        ["checkout/" :store-id]       :checkout
        "shopping-bag"                :shopping-bag
        "business"                    :business
        ["user/" [#"\d+" :user-id]]   user-routes
        "settings"                    :settings
        "auth"                        :auth
        "login"                       :login
        "unauthorized"                :unauthorized}])

(defn auth-roles [handler]
  (cond
    (= handler :store-dashboard)
    ::auth/store-owner
    (= (namespace handler) "store-dashboard")
    ::auth/store-owner
    (#{:user/profile :user/order :user/order-list} handler)
    ::auth/exact-user
    (= handler :checkout)
    ::auth/any-user
    (= handler :settings)
    ::auth/any-user
    :else
    ::auth/public))

(defn path
  "Takes a route and its route-params and returns a path"
  ([route] (path route nil))
  ([route route-params]
   (try
     (apply bidi/path-for routes route (some->> route-params (reduce into [])))
     (catch #?@(:cljs [:default e]
                :clj  [Throwable e])
            (error "Error when trying to create url from route: " route
                   " route-params: " route-params
                   " error: " e)
       #?(:clj (.printStackTrace e))
       nil))))

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