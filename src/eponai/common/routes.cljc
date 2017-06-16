(ns eponai.common.routes
  (:require [bidi.bidi :as bidi]
            [taoensso.timbre :refer [debug error]]
            [eponai.common.auth :as auth]
            [clojure.string :as str]))

(def store-routes
  {""           :store
   "/dashboard" {""          :store-dashboard
                 "/profile"  :store-dashboard/profile
                 "/policies" :store-dashboard/policies
                 "/shipping" :store-dashboard/shipping
                 "/finances" {""          :store-dashboard/finances
                              "/settings" :store-dashboard/finances#settings}
                 "/business" {""        :store-dashboard/business
                              "/verify" :store-dashboard/business#verify}
                 ;"/settings" {""          :store-dashboard/settings
                 ;             "/payments" :store-dashboard/settings#payments
                 ;             "/payouts"  :store-dashboard/settings#payouts
                 ;             "/business" :store-dashboard/settings#business
                 ;             "/activate" :store-dashboard/settings#activate}
                 "/products" {""                                :store-dashboard/product-list
                              "/create"                         :store-dashboard/create-product
                              ["/" [#"(\d|\w|-)+" :product-id]] :store-dashboard/product}
                 "/orders"   {""                        :store-dashboard/order-list
                              "/inbox"                  :store-dashboard/order-list-new
                              "/fulfilled"              :store-dashboard/order-list-fulfilled
                              ["/" [#"create" :action]] :store-dashboard/create-order
                              ["/" [#"\w+" :order-id]]  :store-dashboard/order}
                 "/stream"   :store-dashboard/stream}})

(def user-routes
  {""          :user
   "/settings" {"" :user-settings}
   "/orders"   {""                       :user/order-list
                ["/" [#"\w+" :order-id]] :user/order}
   "/profile"  :user/profile})

(def product-routes
  {""                         :browse/all-items
   ["/" [#"\d+" :product-id]] :product})

(def help-routes
  {""                :help
   "/first-stream"   :help/first-stream
   "/mobile-stream"  :help/mobile-stream
   "/shipping-rules" :help/shipping-rules
   "/quality"        :help/quality
   "/faq"            :help/faq})

(def checkout-routes
  {""          :checkout
   "/shipping" :checkout/shipping
   "/payments" :checkout/payment
   "/review"   :checkout/review})

(defn- branch-handler [handler param sub-handler]
  {""          handler
   ["/" param] sub-handler})

(def browse-categories
  [
   ;; Can't do 'all-items' right now, as it doesn't make sense when navigating?
   ;; We've got our categories at the top right now.
   ;;["" :browse/all-items]
   [["/category/" :top-category]
    (branch-handler :browse/category
                    :sub-category
                    (branch-handler :browse/category+sub
                                    :sub-sub-category
                                    :browse/category+sub+sub-sub))]
   [["/" :sub-category]
    (branch-handler :browse/gender
                    :top-category
                    (branch-handler :browse/gender+top
                                    :sub-sub-category
                                    :browse/gender+top+sub-sub))]])

(defn normalize-browse-route [route]
  (letfn [(remove-from-char [s c]
            (if-let [idx (str/index-of s c)]
              (subs s 0 idx)
              s))]
    (keyword (namespace route)
             (remove-from-char (name route) "+"))))

(def routes
  ["/" {""                            :index
        "enter"                       :landing-page
        "enter/l"                     :landing-page/locality
        "sell"                        :sell
        "live"                        :live
        "store"                       :index/store
        ["store/" [#"\d+" :store-id]] store-routes
        "products"                    product-routes
        "browse"                      browse-categories
        "help"                        help-routes
        ["checkout/" :store-id]       checkout-routes
        "shopping-bag"                :shopping-bag
        "business"                    :business
        "settings"                    :user-settings
        "orders"                      {""                       :user/order-list
                                       ["/" [#"\w+" :order-id]] :user/order}
        ;["user/" [#"\d+" :user-id]]   user-routes
        "auth"                        :auth
        "login"                       :login
        "unauthorized"                :unauthorized}])

(def location-independent-routes #{:landing-page :user-settings :sell})

(defn auth-roles [handler]
  (cond
    (#{:landing-page :sell} handler)
    ::auth/public
    (= handler :store-dashboard)
    ::auth/store-owner
    (= (namespace handler) "store-dashboard")
    ::auth/store-owner

    (#{:user/order :user/order-list :checkout} handler)
    ::auth/any-user
    :else
    ::auth/any-user))

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
