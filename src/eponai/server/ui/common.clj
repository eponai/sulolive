(ns eponai.server.ui.common
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [clojure.string :as string]
    [environ.core :as env]
    [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
    [eponai.common.mixpanel :as mixpanel]
    [taoensso.timbre :refer [debug]]))

;; Utils

(def asset-version (env/env :asset-version "unset_asset_version"))
(defn versionize [url]
  (str url "?v=" asset-version))

(def text-javascript "text/javascript")

(defn inline-javascript [code & [opts]]
  {:pre [(every? string? code)]}
  (let [code (->> code
                  (map string/trim)
                  (apply str))]
    (dom/script (merge opts {:type                    text-javascript
                             :dangerouslySetInnerHTML {:__html code}}))))

(defn anti-forgery-field []
  (dom/input
    {:id    "__anti-forgery-token"
     :name  "__anti-forgery-token"
     :type  "hidden"
     :value *anti-forgery-token*}))


(defn iubenda-code []
  (inline-javascript ["(function (w,d) {var loader = function () {var s = d.createElement(\"script\"), tag = d.getElementsByTagName(\"script\")[0]; s.src = \"//cdn.iubenda.com/iubenda.js\"; tag.parentNode.insertBefore(s,tag);}; if(w.addEventListener){w.addEventListener(\"load\", loader, false);}else if(w.attachEvent){w.attachEvent(\"onload\", loader);}else{w.onload = loader;}})(window, document);"]))

(defn google-analytics []
  (inline-javascript ["(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o), m=s.getElementsByTagName(o)[0];
  a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');"
                      "ga('create', 'UA-90954507-1', 'auto');"
                      "ga('require', 'ec');"
                      "ga('send', 'pageview');"]))
(defn google-analytics-sulo-master []
  (inline-javascript ["(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o), m=s.getElementsByTagName(o)[0];
  a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');"
                      "ga('create', 'UA-102742593-1', 'auto');"
                      "ga('require', 'ec');"
                      "ga('send', 'pageview');"]))

;; Facebook login init code

(defn facebook-async-init-code []
  ["window.fbAsyncInit = function() {"
   "  FB.init({"
   "    appId: '936364773079066',"
   "    xfbml: true,"
   "    version: 'v2.7'"
   "  });"
   "};"
   ""
   "(function(d, s, id){"
   "   var js, fjs = d.getElementsByTagName(s)[0];"
   "   if (d.getElementById(id)) {return;}"
   "   js = d.createElement(s); js.id = id;"
   "   js.src = \"//connect.facebook.net/en_US/sdk.js\";"
   "   fjs.parentNode.insertBefore(js, fjs);"
   " }(document, 'script', 'facebook-jssdk'));"])

;; Mix panel inline code

(defn mixpanel [release?]
  (let [use-token (if release? (::mixpanel/token-release mixpanel/tokens) (::mixpanel/token-dev mixpanel/tokens))]
    (inline-javascript
      ["(function(e,a){if(!a.__SV){var b=window;try{var c,l,i,j=b.location,g=j.hash;c=function(a,b){return(l=a.match(RegExp(b+\"=([^&]*)\")))?l[1]:null};g&&c(g,\"state\")&&(i=JSON.parse(decodeURIComponent(c(g,\"state\"))),\"mpeditor\"===i.action&&(b.sessionStorage.setItem(\"_mpcehash\",g),history.replaceState(i.desiredHash||\"\",e.title,j.pathname+j.search)))}catch(m){}var k,h;window.mixpanel=a;a._i=[];a.init=function(b,c,f){function e(b,a){var c=a.split(\".\");2==c.length&&(b=b[c[0]],a=c[1]);b[a]=function(){b.push([a].concat(Array.prototype.slice.call(arguments,0)))}}var d=a;\"undefined\"!==typeof f?d=a[f]=[]:f=\"mixpanel\";d.people=d.people||[];d.toString=function(b){var a=\"mixpanel\";\"mixpanel\"!==f&&(a+=\".\"+f);b||(a+=\" (stub)\");return a};d.people.toString=function(){return d.toString(1)+\".people (stub)\"};k=\"disable time_event track track_pageview track_links track_forms register register_once alias unregister identify name_tag set_config reset people.set people.set_once people.increment people.append people.union people.track_charge people.clear_charges people.delete_user\".split(\" \");for(h=0;h<k.length;h++)e(d,k[h]);a._i.push([b,c,f])};a.__SV=1.2;b=e.createElement(\"script\");b.type=\"text/javascript\";b.async=!0;b.src=\"undefined\"!==typeof MIXPANEL_CUSTOM_LIB_URL?MIXPANEL_CUSTOM_LIB_URL:\"file:\"===e.location.protocol&&\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\".match(/^\\/\\//)?\"https://cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\":\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\";c=e.getElementsByTagName(\"script\")[0];c.parentNode.insertBefore(b,c)}})(document,window.mixpanel||[]);mixpanel.init(\""
       use-token
       "\");"])))

(defn facebook-pixel []
  [(inline-javascript ["!function(f,b,e,v,n,t,s)\n  {if(f.fbq)return;n=f.fbq=function(){n.callMethod?\n  n.callMethod.apply(n,arguments):n.queue.push(arguments)};\n  if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';\n  n.queue=[];t=b.createElement(e);t.async=!0;\n  t.src=v;s=b.getElementsByTagName(e)[0];\n  s.parentNode.insertBefore(t,s)}(window, document,'script',\n  'https://connect.facebook.net/en_US/fbevents.js');\n  fbq('init', '1966162850296774');\n  fbq('track', 'PageView');
  "])
   (dom/noscript {:src "https://www.facebook.com/tr?id=1966162850296774&ev=PageView&noscript=1"}
                 (dom/img {:height "1" :width "1" :style {:display "none"}}))])

(defn mailchimp []
  (inline-javascript
    ["!function(c,h,i,m,p){m=c.createElement(h),p=c.getElementsByTagName(h)[0],m.async=1,m.src=i,p.parentNode.insertBefore(m,p)}(document,\"script\",\"https://chimpstatic.com/mcjs-connected/js/users/b99ff2dd9a9433d58675f33ea/565ad9606bb5e7b721e413fef.js\");"]
    {:id "mcjs"}))

;; End mix panel.

(defn icons []
  (letfn [(icon [size rel href-without-size]
            (let [sxs (str size "x" size)]
              (dom/link {:rel   rel
                         :sizes sxs
                         :href  (str href-without-size sxs ".png?v=2")
                         :type  "image/png"})))]
    (concat
      (map #(icon % "apple-touch-icon" "/assets/img/favicon/apple-icon-")
           [57 60 72 76 114 120 144 152 180])
      (map #(icon % "icon" "/assets/img/favicon/favicon-")
           [16 32 96])
      (map #(icon % "icon" "/assets/img/favicon/android-icon-")
           [36 48 72 96 144 192]))))

(defn sharing-tags [{:keys [facebook twitter]}]
  (let [tag-fn (fn [[k v]]
                 (dom/meta {:property (name k)
                            :content  v}))
        facebook-tags (mapv tag-fn facebook)
        twitter-tags (mapv tag-fn twitter)]
    (into facebook-tags twitter-tags)))

(defn head [{:keys [release? exclude-icons? cljs-build-id social-sharing site-info random-seed]}]
  (debug "SITE INFO: " site-info)
  (dom/head
    {:prefix "og: http://ogp.me/ns# fb: http://ogp.me/ns/fb#"}
    (dom/meta {:name    "google-site-verification"
               :content "eWC2ZsxC6JcZzOWYczeVin6E0cvP4u6PE3insn9p76U"})

    (dom/meta {:name    "p:domain_verify"
               :content "f61e0ec64a4d48b5ac6f65e6dc3c5427"})
    (dom/meta {:charset "UTF-8"})
    (dom/meta {:http-equiv "X-UA-Compatible"
               :content    "IE=edge"})
    (dom/meta {:name    "viewport"
               :content "width=device-width, initial-scale=1 maximum-scale=1 user-scalable=0"})
    (dom/meta {:name "author" :content "SULO Live"})
    ;; Asset version is needed in our module urls.
    (dom/meta {:id "asset-version-meta" :name "asset-version" :content asset-version})
    (dom/meta {:id "random-seed-meta" :name "random-seed" :content (str random-seed)})
    (dom/meta {:name    "description"
               :content (or (:description site-info) "Shop local goods and hangout LIVE with your local vendors.")})
    (comment (dom/meta {:http-equiv "Content-Type"
                        :content    "text/html; charset=utf-8"}))
    (dom/title nil (or (:title site-info) "Your local marketplace online, shop and hangout LIVE with your local vendors - SULO Live"))
    (when (= cljs-build-id "devcards")
      (dom/link {:href "/bower_components/nvd3/build/nv.d3.css"
                 :rel  "stylesheet"}))
    (dom/link {:href "https://cdnjs.cloudflare.com/ajax/libs/react-select/1.0.0-rc.5/react-select.min.css"
               :rel  "stylesheet"})

    (dom/link {:href (versionize "/assets/css/app.css")
               :rel  "stylesheet"})

    (dom/link {:href (versionize "/assets/css/flag-icon.min.css")
               :rel  "stylesheet"})
    ;; Custom fonts
    (dom/link {:href (if release?
                       "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"
                       "/assets/font-awesome/css/font-awesome.min.css")
               :rel  "stylesheet"
               :type "text/css"})

    (sharing-tags social-sharing)

    (mixpanel release?)
    (iubenda-code)

    ;; Favicon
    (when (not exclude-icons?)
      (icons))

    (dom/link {:rel  "manifest"
               :href (versionize "/assets/img/favicon/manifest.json")})
    (dom/meta {:name "msapplication-TileColor" :content "#ffffff"})
    (dom/meta {:name    "msapplication-TileImage"
               :content (versionize "/assets/img/favicon/ms-icon-144x144.png")})
    (dom/meta {:name "theme-color" :content "#ffffff"})

    (when release?
      (mailchimp))
    (if release?
      (google-analytics)
      (google-analytics-sulo-master))
    (if release?
      (facebook-pixel))))

(defn budget-js-path []
  (versionize "/js/out/budget.js"))


(defn auth0-lock-passwordless [release?]
  (dom/script {:src (if release? "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"
                                 "/bower_components/auth0-lock-passwordless/build/lock-passwordless.min.js")}))

(defn auth0-lock [release?]
  (dom/script {:src (if release? "https://cdn.auth0.com/js/lock/10.6/lock.min.js"
                                 "/bower_components/auth0-lock/build/lock.js")}))