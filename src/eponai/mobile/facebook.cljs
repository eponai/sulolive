(ns eponai.mobile.facebook
  (:require
    [taoensso.timbre :refer-macros [debug]]))

(def ReactNativeFBSDK (js/require "react-native-fbsdk"))


(defn login
  [f-res & opts]
  ;(when f-res
  ;  (assert (fn? f-res) (str "Facebook login result handler must be a function. Got type: " (type f-res))))
  (let [fb-login-manager (.-LoginManager ReactNativeFBSDK)
        access-token (.-AccessToken ReactNativeFBSDK)
        read-permissions (if (:read-permissions opts)
                           (clj->js (:read-permissions opts))
                           #js ["public_profile"])

        result-handler (fn [res]
                         (if (.-isCancelled res)
                           (do
                             (debug "Facebook login canceled.")
                             (when f-res
                               (f-res {:status ::login-cancel})))
                           (do
                             (debug "Facebook login successful with permissions: " (.. res -grantedPermissions toString))
                             (.. access-token
                                 getCurrentAccessToken
                                 (then (fn [d]
                                         (let [access-token (.. d -accessToken toString)
                                               user-id (.. d -userID)]
                                           (when f-res
                                             (f-res {:status ::login-success
                                                     :access-token  access-token
                                                     :user-id user-id})))))))))
        error-handler (fn [e]
                        (debug "Facebook login failed with error: " e)
                        (when f-res
                          (f-res {:status ::login-error
                                  :error  e})))]

    (.. fb-login-manager
        (logInWithReadPermissions read-permissions)
        (then result-handler
              error-handler))))