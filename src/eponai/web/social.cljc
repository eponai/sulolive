(ns eponai.web.social
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.social.facebook :as fb]
    [taoensso.timbre :refer [debug]]))

(def logos
  {:social/email    "/assets/img/icons/Email-96.png"
   :social/facebook "/assets/img/icons/FB-f-Logo__blue_1024.png"
   :social/twitter  "/assets/img/icons/Twitter_Logo_White_On_Blue.png"})

(defmulti share-stream (fn [k _] k))

(defmethod share-stream :social/facebook
  [_ stream]
  (fb/share-stream stream))
(defmethod share-stream :social/twitter
  [_ stream])
(defmethod share-stream :social/email
  [_ stream])

(defn share-button [stream {:keys [on-click platform]}]
  (dom/a
    (->> {:onClick #(do
                     (debug "Share on platform: " platform)
                     (share-stream platform stream)
                     (when on-click
                       (on-click)))}
      (css/add-class :share-button))
    (dom/img {:src (get logos platform)})))
