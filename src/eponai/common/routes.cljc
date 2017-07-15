(ns eponai.common.routes
  (:require [bidi.bidi :as bidi]
            [taoensso.timbre :as timbre :refer [debug error]]
            [eponai.common.auth :as auth]
            [clojure.string :as str]
            [cemerick.url :as url]))

(def store-routes
  {""           :store
   "/dashboard" {""          :store-dashboard
                 "/profile"  {""         :store-dashboard/profile
                              "/options" :store-dashboard/profile#options}
                 "/policies" :store-dashboard/policies
                 "/shipping" :store-dashboard/shipping
                 "/finances" {""          :store-dashboard/finances
                              "/settings" :store-dashboard/finances#settings
                              "/taxes"    :store-dashboard/finances#taxes}
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
  {["/" [#"\d+" :product-id]] :product})

(def help-routes
  {""          :help
   "/stores"   {""                :help/stores
                "/first-stream"   :help/first-stream
                "/mobile-stream"  :help/mobile-stream
                "/shipping-rules" :help/shipping-rules
                "/quality"        :help/quality
                "/faq"            :help/faq
                "/taxes"          :help/taxes}
   "/accounts" :help/accounts
   "/welcome"  :help/welcome
   })

(def checkout-routes
  {""          :checkout
   "/shipping" :checkout/shipping
   "/payments" :checkout/payment
   "/review"   :checkout/review})

(defn- branch-handler [handler param sub-handler]
  {""          handler
   ["/" param] sub-handler})

(def browse-categories
  [["" :browse/all-items]
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

(def locality-routes
  [["" {
        ;"/"         :index
        "/shops"  :stores
        "/browse" browse-categories
        "/live"   :live}]
   [true :index]])

(defn normalize-browse-route [route]
  (letfn [(remove-from-char [s c]
            (if-let [idx (str/index-of s c)]
              (subs s 0 idx)
              s))]
    (keyword (namespace route)
             (remove-from-char (name route) "+"))))

(def routes
  ["" [["/" {
             ""                                                           :landing-page
             "l"                                                          :landing-page/locality
             ["" [#"(yvr|yul)" :locality]]                                locality-routes
             "sell"                                                       :sell
             ["products/" [#"\d+" :product-id] "/" [#".*" :product-name]] :product
             ;"products"                    product-routes
             ;"browse"                      browse-categories

             ;"store"                                                      :index/store
             ["shop/" :store-id]                                          store-routes
             "help"                                                       help-routes
             "about"                                                      :about
             ["checkout/" :store-id]                                      checkout-routes
             "shopping-bag"                                               :shopping-bag
             "business"                                                   :business
             "settings"                                                   :user-settings
             "orders"                                                     {""                       :user/order-list
                                                                           ["/" [#"\w+" :order-id]] :user/order}
             ;["user/" [#"\d+" :user-id]]   user-routes
             "tos"                                                        :tos
             "auth"                                                       :auth
             "login"                                                      :login
             "logout"                                                     :logout
             "link-social"                                                :link-social
             "unauthorized"                                               :unauthorized}]
       [true :not-found]]])

;; Compojure + bidi merges our query-params and route-params.
;; These keys will be removed from our query-params so we use
;; query-params with merge when updating routes.
(def only-route-param-keys #{:top-category :sub-category :sub-sub-category :locality})

(defn location-independent-route? [route]
  (when (some? route)
    (or (contains? #{:user-settings
                     :sell
                     :landing-page
                     :landing-page/locality
                     :user/order-list
                     :user/order
                     :shopping-bag
                     :about
                     :tos
                     :not-found
                     :login
                     :link-social
                     :unauthorized
                     }
                   route)
        (= :store-dashboard route)
        (= (name :store-dashboard) (namespace route))
        (= :help route)
        (= (name :help) (namespace route)))))

(defn auth-roles [handler]
  (cond
    (#{:landing-page :sell :about :not-found :login :tos :unauthorized} handler)
    ::auth/public
    (= handler :store-dashboard)
    ::auth/store-owner
    (= (namespace handler) "store-dashboard")
    ::auth/store-owner

    (#{:user/order :user/order-list :checkout :link-social} handler)
    ::auth/any-user
    :else
    ::auth/any-user))

(defn path
  "Takes a route and its route-params and returns a path"
  ([route] (path route nil))
  ([route route-params] (path route route-params nil))
  ([route route-params query-params]
   (try
     (let [url (apply bidi/path-for routes route (some->> route-params (reduce into [])))]
       (cond-> url
               (not (empty? query-params))
               (str "?" (url/map->query query-params))))
     (catch #?@(:cljs [:default e]
                :clj  [Throwable e])
            (error "Error when trying to create url from route: " route
                   " route-params: " route-params
                   " error: " e)
       nil))))
