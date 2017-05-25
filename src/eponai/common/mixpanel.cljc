(ns eponai.common.mixpanel
  (:require
    [taoensso.timbre :refer [warn debug]]
    #?(:clj
    [clojure.data.json :as json])
    #?(:clj
    [clj-http.client :as http])
    #?(:clj
    [buddy.core.codecs.base64 :as base64])
    [eponai.common.format.date :as date]))

#?(:clj
   (def token-atom (atom nil)))

#?(:clj
   (def tokens
     {::token-dev     "5b80eeb6f690b9f97815f4cce58d96ac"
      ::token-release "b266c99172ca107a814c16cb22661d04"}))

#?(:clj
   (defn set-token [key]
     (let [token (get tokens key)]
       (reset! token-atom token))))

(defn track [event & [properties]]
  #?(:cljs (.track js/mixpanel event (clj->js properties))
     :clj  (if-let [token @token-atom]
             (let [params (json/write-str {:event      event
                                           :properties (assoc properties
                                                         :token token
                                                         :time (date/current-secs))})]
               (http/get "https://api.mixpanel.com/track"
                         {:query-params {:data (String. (base64/encode params true))}}))
             (throw (ex-info "Mixpanel token is nil, make sure to set token before first call to track"
                             {:event      event
                              :properties properties})))))

(def events
  {
   ;; Store menu
   ::go-to-dashboard       "Store: Go to dashboard"
   ::go-to-store-info      "Store: Go to edit store info"
   ::go-to-stream-settings "Store: Go to stream settings"
   ::go-to-products        "Store: Go to products"
   ::go-to-orders          "Store: Go to orders"
   ::go-to-business        "Store: Go to business settings"

   ;; User menu
   ::go-to-manage-store    "Go to manage store"
   ::go-to-purchases       "Go to user purchases"
   ::go-to-settings        "Go to user settings"

   ::signout               "Sign out user"
   ::open-signin           "Open sign in user"
   ::go-to-start-store     "Go to start store"

   ::shop-by-category "Shop by category"
   ::upload-photo          "Upload photo"})

(defn track-key [k & [properties]]
  (if-let [event (get events k)]
    (track event properties)
    (warn "Mixpanel - Received unrecognized event key:  " k)))

(defn people-set [params]
  #?(:cljs (.set (.-people js/mixpanel) (clj->js params))))
