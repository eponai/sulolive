(ns eponai.web.social
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.social.facebook :as fb]
    [taoensso.timbre :refer [debug]]
    [clojure.string :as string]
    [cemerick.url :as url]))

(def logos
  {:social/email    "/assets/img/icons/Email-96.png"
   :social/facebook "/assets/img/icons/FB-f-Logo__blue_1024.png"
   :social/twitter  "/assets/img/icons/Twitter_Logo_White_On_Blue.png"})

(def socials
  {:social/email    "Email"
   :social/facebook "Facebook"
   :social/twitter  "Twitter"})

(defmulti share-stream (fn [k _] k))

(defn params->query-str [params]
  (string/join "&"
               (reduce (fn [l [k v]]
                         (conj l (str (name k) "=" v)))
                       []
                       params)))

(defmethod share-stream :social/facebook
  [_ stream]
  #?(:cljs
     ;(js/FB.ui #js {:method  "share"
     ;               ;:action_type       "og.likes"
     ;               :href    "https://sulo.live/store/17592186045420"
     ;               ;:display "none"
     ;               ;:action_properties (js/JSON.stringify #js{:object "834534123363063"})
     ;               }
     ;          (fn [response]
     ;            (debug "Facebook response: " response)))
     (let [params {:app_id "145634995501895"
                   :href   "https://sulo.live/store/17592186045420"}
           query (url/map->query params)]
       (.open js/window (str "https://www.facebook.com/dialog/share?" query)))))
(defmethod share-stream :social/twitter
  [_ stream]
  #?(:cljs
     (let [params {:text     "Finding great things on this store"
                   :url      "https://sulo.live/store/17592186045420"
                   :hashtags "sulolive"
                   :via      "sulolive"}
           query (url/map->query params)]
       (.open js/window (str "https://twitter.com/intent/tweet?" query)))))
(defmethod share-stream :social/email
  [_ stream])

(defn share-button [stream {:keys [on-click platform]}]
  (dom/a
    (->> {:onClick #(do
                     (debug "Share on platform: " platform)
                     (share-stream platform stream)
                     (when on-click
                       (on-click)))}
         (css/add-class :share-button)
         (css/add-class :sl-tooltip))
    (dom/img {:src (get logos platform)})
    (dom/span (css/add-class :sl-tooltip-text)
              (str "Share on " (get socials platform)))))
