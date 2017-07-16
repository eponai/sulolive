(ns eponai.common.firebase
  (:require
    [bidi.bidi :as bidi]
    [taoensso.timbre :refer [error]]))

(def locality-routes
  {"visitor-count"              {""              :visitor-counts
                                 ["/" :store-id] {""                    :visitor-count/store
                                                  ["/" [#"\d+" :count]] :visitor-count/store+count}}
   "user-presence/store-owners" {""             :user-presence/store-owners
                                 ["/" :user-id] :user-presence/store-owner}})

(def store-routes
  {"owner-presence" {""             :store/owner-presences
                     ["/" :user-id] :store/owner-presence}
   "visitors"       {""                 :store/visitors
                     ["/" :firebase-id] :store/visitor}
   "chat"           {"" :store/chat}})

(def user-routes
  {"chat-notifications" {"/unread" :user/unread-chat-notifications}
   "notifications"      {"/unread" {""                 :user/unread-notifications
                                    ["/" :firebase-id] :user/unread-notification}}})

(def firebase-routes-v2
  ["/v2/" {["locality/" :locality "/"] locality-routes
           ["store/" :store-id "/"]    store-routes
           ["user/" :user-id "/"]      user-routes}])

(defn path
  "Takes a route and its route-params and returns a firebase path"
  [route route-params]
  (try
    (apply bidi/path-for firebase-routes-v2 route (some->> route-params (reduce into [])))
    (catch #?@(:cljs [:default e]
               :clj  [Throwable e])
           (error "Error when trying to create firebase path from route: " route
                  " route-params: " route-params
                  " error: " e)
      nil)))
