(ns eponai.server.ui.common
  (:require [om.dom :as dom]
            [clojure.string :as string]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

;; Utils

(def text-javascript "text/javascript")

(defn inline-javascript [code]
  {:pre [(every? string? code)]}
  (let [code (->> code
                  (map string/trim)
                  (apply str))]
    (dom/script {:type                    text-javascript
                 :dangerouslySetInnerHTML {:__html code}})))

(defn anti-forgery-field []
  (dom/input
    {:id "__anti-forgery-token"
     :name "__anti-forgery-token"
     :type "hidden"
     :value *anti-forgery-token*}))

;; Mix panel inline code

(defn mixpanel []
  (inline-javascript
    ["(function(e,a){if(!a.__SV){var b=window;try{var c,l,i,j=b.location,g=j.hash;c=function(a,b){return(l=a.match(RegExp(b+\"=([^&]*)\")))?l[1]:null};g&&c(g,\"state\")&&(i=JSON.parse(decodeURIComponent(c(g,\"state\"))),\"mpeditor\"===i.action&&(b.sessionStorage.setItem(\"_mpcehash\",g),history.replaceState(i.desiredHash||\"\",e.title,j.pathname+j.search)))}catch(m){}var k,h;window.mixpanel=a;a._i=[];a.init=function(b,c,f){function e(b,a){var c=a.split(\".\");2==c.length&&(b=b[c[0]],a=c[1]);b[a]=function(){b.push([a].concat(Array.prototype.slice.call(arguments,\n0)))}}var d=a;\"undefined\"!==typeof f?d=a[f]=[]:f=\"mixpanel\";d.people=d.people||[];d.toString=function(b){var a=\"mixpanel\";\"mixpanel\"!==f&&(a+=\".\"+f);b||(a+=\" (stub)\");return a};d.people.toString=function(){return d.toString(1)+\".people (stub)\"};k=\"disable time_event track track_pageview track_links track_forms register register_once alias unregister identify name_tag set_config reset people.set people.set_once people.increment people.append people.union people.track_charge people.clear_charges people.delete_user\".split(\" \");\nfor(h=0;h<k.length;h++)e(d,k[h]);a._i.push([b,c,f])};a.__SV=1.2;b=e.createElement(\"script\");b.type=\"text/javascript\";b.async=!0;b.src=\"undefined\"!==typeof MIXPANEL_CUSTOM_LIB_URL?MIXPANEL_CUSTOM_LIB_URL:\"file:\"===e.location.protocol&&\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\".match(/^\\/\\//)?\"https://cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\":\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\";c=e.getElementsByTagName(\"script\")[0];c.parentNode.insertBefore(b,c)}})(document,window.mixpanel||[]);\nmixpanel.init(\"0980865aa734b3f18be4d92d0b612ee5\");"]))

;; End mix panel.

(defn icons []
  (letfn [(icon [size rel href-without-size]
            (let [sxs (str size "x" size)]
              (dom/link {:rel   rel
                         :sizes sxs
                         :href  (str href-without-size sxs ".png")
                         :type  "image/png"})))]
    (concat
      (map #(icon % "apple-touch-icon" "/assets/img/favicon/apple-icon-")
           [57 60 72 76 114 120 144 152 180])
      (map #(icon % "icon" "/assets/img/favicon/favicon-")
           [16 32 96]))))

(defn head [release? & [exclude-icons?]]
  [(dom/meta {:name    "google-site-verification"
              :content "eWC2ZsxC6JcZzOWYczeVin6E0cvP4u6PE3insn9p76U"})
   (dom/meta {:charset "utf-8"})
   (dom/meta {:http-equiv "X-UA-Compatible"
              :content    "IE=edge"})
   (dom/meta {:name    "viewport"
              :content "width=device-width, initial-scale=1 maximum-scale=1 user-scalable=no"})
   (dom/meta {:name "author" :content "eponai hb"})
   (dom/meta {:name "description"
              :content "Jourmoney is an expense tracker for nomads, making it easy to track different housing arrangements, transports and international bank fees."})
   (comment (dom/meta {:http-equiv "Content-Type"
                       :content    "text/html; charset=utf-8"}))
   (dom/title nil "Tracking Expenses for Nomads - jourmoney")
   (dom/link {:href "/assets/css/app.css"
              :rel  "stylesheet"})

   ;; Custom fonts
   (dom/link {:href (if release?
                      "https://maxcdn.bootstrapcdn.com/font-awesome/4.5.0/css/font-awesome.min.css"
                      "/assets/font-awesome/css/font-awesome.min.css")
              :rel  "stylesheet"
              :type "text/css"})

   (when release?
     (mixpanel))

   ;; Favicon
   (when (not exclude-icons?)
     (icons))

   (dom/link {:rel "manifest" :href "/assets/img/favicon/manifest.json"})
   (dom/meta {:name "msapplication-TileColor" :content "#ffffff"})
   (dom/meta {:name    "msapplication-TileImage"
              :content "/assets/img/favicon/ms-icon-144x144.png"})
   (dom/meta {:name "theme-color" :content "#ffffff"})])


(defn footer []
  (dom/div {:id "footer-container"}
    (dom/div {:className "footer"}
      (dom/small nil "Copyright &copy; eponai 2016. All Rights Reserved"))))