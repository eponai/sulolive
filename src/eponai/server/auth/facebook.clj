(ns eponai.server.auth.facebook
  (:require [cemerick.url :as url]
            [ring.util.response :refer [redirect]]))

(defn login-dialog
  ([app-id]
    (login-dialog app-id "https://www.facebook.com/connect/login_success.html"))
  ([app-id redirect-url]
   (redirect
     (-> (url/url "https://www.facebook.com/dialog/oauth?")
         (assoc :query {:client_id    app-id
                        :redirect_uri redirect-url})
         str))))