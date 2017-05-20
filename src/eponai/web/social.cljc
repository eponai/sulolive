(ns eponai.web.social
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [clojure.string :as string]
    [cemerick.url :as url]))

(def logos
  {:social/email     "/assets/img/icons/Email-96.png"
   :social/facebook  "/assets/img/icons/FB-f-Logo__blue_1024.png"
   :social/twitter   "/assets/img/icons/Twitter_Logo_White_On_Blue.png"
   :social/pinterest "/assets/img/icons/Pinterest_badgeRGB.png"})

(def socials
  {:social/email     "Email"
   :social/facebook  "Facebook"
   :social/twitter   "Twitter"
   :social/pinterest "Pinterest"})

(defmulti share-stream (fn [{:keys [platform]}] platform))

(defn params->query-str [params]
  (string/join "&"
               (reduce (fn [l [k v]]
                         (conj l (str (name k) "=" v)))
                       []
                       params)))

(defmethod share-stream :social/facebook
  [{:keys [href]}]
  #?(:cljs
     (let [params {:app_id "145634995501895"
                   :href   href}
           query (url/map->query params)]
       (.open js/window (str "https://www.facebook.com/dialog/share?" query)))))
(defmethod share-stream :social/twitter
  [{:keys [href description]}]
  #?(:cljs
     (let [params {:text     description
                   :url      href
                   :hashtags "sulolive"
                   :via      "sulolive"}
           query (url/map->query params)]
       (.open js/window (str "https://twitter.com/intent/tweet?" query)))))

;<a data-pin-do="buttonPin" href="https://www.pinterest.com/pin/create/button/?url=http://www.foodiecrush.com/2014/03/filet-mignon-with-porcini-mushroom-compound-butter/&media=https://s-media-cache-ak0.pinimg.com/736x/17/34/8e/17348e163a3212c06e61c41c4b22b87a.jpg&description=So%20delicious!"></a>
(defmethod share-stream :social/pinterest
  [{:keys [href description media]}]
  #?(:cljs
     (let [params {:url         href
                   :media       media
                   :description description}
           query (url/map->query params)]
       (.open js/window (str "https://www.pinterest.com/pin/create/button/?" query)))))

;(defmethod share-stream :social/email
;  [_ stream])

(defn share-button [{:keys [on-click platform] :as opts}]
  (dom/a
    (->> {:onClick #(do
                     (debug "Share on platform: " platform)
                     (share-stream opts)
                     (when on-click
                       (on-click)))}
         (css/add-class :share-button)
         (css/add-class :sl-tooltip))
    (dom/img {:src (get logos platform)})
    (dom/span (css/add-class :sl-tooltip-text)
              (str "Share on " (get socials platform)))))

(defn sulo-social-link [platform]
  (condp = platform
    :social/instagram
    (dom/a {:href   "https://www.instagram.com/sulolive"
            :target "_blank"}
           (dom/span {:classes ["icon icon-instagram"]}))
    :social/facebook
    (dom/a {:href   "https://www.facebook.com/live.sulo"
            :target "_blank"}
           (dom/span {:classes ["icon icon-facebook"]}))))

(defn sulo-icon-attribution []
  (dom/a {:href   "https://icons8.com"
          :target "_blank"}
         (dom/small {:classes ["copyright"]} "Icons by Icons8")))

(defn sulo-copyright []
  (dom/small {:classes ["copyright"]} "© eponai hb 2017"))
