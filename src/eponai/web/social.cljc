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
   :social/pinterest "/assets/img/icons/Pinterest_badgeRGB.png"
   :social/instagram "/assets/img/icons/IG_Glyph_Fill.png"})

(def socials
  {:social/email     "Email"
   :social/facebook  "Facebook"
   :social/twitter   "Twitter"
   :social/pinterest "Pinterest"
   :social/instagram "Instagram"})

(defmulti share-social (fn [{:keys [platform]}] platform))

(defn params->query-str [params]
  (string/join "&"
               (reduce (fn [l [k v]]
                         (conj l (str (name k) "=" v)))
                       []
                       params)))

(defmethod share-social :social/facebook
  [{:keys [href]}]
  #?(:cljs
     (let [params {:app_id "145634995501895"
                   :href   href}
           query (url/map->query params)]
       (.open js/window (str "https://www.facebook.com/dialog/share?" query)))))
(defmethod share-social :social/twitter
  [{:keys [href description]}]
  #?(:cljs
     (let [params {:text     description
                   :url      href
                   :hashtags "sulolive"
                   :via      "sulolive"}
           query (url/map->query params)]
       (.open js/window (str "https://twitter.com/intent/tweet?" query)))))

(defmethod share-social :social/pinterest
  [{:keys [href description media]}]
  #?(:cljs
     (let [params {:url         href
                   :media       media
                   :description description}
           query (url/map->query params)]
       (.open js/window (str "https://www.pinterest.com/pin/create/button/?" query)))))

(defn follow-button [{:keys [platform on-click] :as opts}]
  (dom/a
    (->> (dissoc opts :platform)
         (css/add-class :follow-button)
         (css/add-class :sl-tooltip))
    (dom/img {:src (get logos platform)})
    (dom/span (css/add-class :sl-tooltip-text)
              (get socials platform))))

(defn share-button [{:keys [on-click platform] :as opts}]
  (dom/a
    (->> {:onClick #(do
                     (debug "Share on platform: " platform)
                     (share-social opts)
                     (when on-click
                       (on-click)))}
         (css/add-class :share-button)
         (css/add-class :sl-tooltip))
    (dom/img {:src (get logos platform) :alt (str "Share on " (string/capitalize (name platform)))})
    (dom/span (css/add-class :sl-tooltip-text)
              (str "Share on " (get socials platform)))))

(def profiles
  {:social/instagram "https://www.instagram.com/sulolive/"
   :social/facebook "https://www.facebook.com/live.sulo"
   :social/twitter "https://twitter.com/sulolive"
   :social/pinterest "https://www.pinterest.com/sulolive/"})

(defn sulo-social-link [platform]
  (condp = platform
    :social/instagram
    (dom/a {:href   (:social/instagram profiles)
            :target "_blank"}
           (dom/span {:classes ["icon icon-instagram"]
                      :title "Instagram"}))
    :social/facebook
    (dom/a {:href   (:social/facebook profiles)
            :target "_blank"}
           (dom/span {:classes ["icon icon-facebook"]
                      :title "Facebook"}))
    :social/twitter
    (dom/a {:href   (:social/twitter profiles)
            :target "_blank"}
           (dom/span {:classes ["icon icon-twitter"]
                      :title "Twitter"}))
    :social/pinterest
    (dom/a {:href   (:social/pinterest profiles)
            :target "_blank"}
           (dom/span {:classes ["icon icon-pinterest"]
                      :title "Pinterest"}))))

(defn sulo-icon-attribution []
  (dom/a {:href   "https://icons8.com"
          :target "_blank"}
         (dom/span {:classes ["copyright"]} "Icons by Icons8")))

(defn sulo-copyright []
  (dom/span {:classes ["copyright"]} "© eponai hb 2017"))
