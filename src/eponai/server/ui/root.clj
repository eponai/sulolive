(ns eponai.server.ui.root
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [clojure.data.json :as json]))

(defui Root
  Object
  (render [this]
    (let [{:keys [release? ::app-html route auth cljs-build-id social-sharing]} (om/props this)]
      (debug "app-html: " app-html)
      (dom/html
        {:lang "en"}
        (common/head {:release?       release?
                      :social-sharing social-sharing
                      :cljs-build-id  cljs-build-id})
        (dom/body
          nil
          (dom/div {:height "100%" :id router/dom-app-id}
            app-html)

          (common/inline-javascript ["GretaOptions = {"
                                     "  accessToken:'" (if release?
                                                         "b554c0b026bb448362dfe657846bf982"
                                                         "fe5ab77ade7eee2c0d3e1e8f51692a7f")
                                     "'}"])
          (common/inline-javascript ["setTimeout(function(){var a=window.GretaOptions||{};a.lazyLoad=a.lazyLoad||{};var f=a.lazyLoad.imgAttribute||\"data-src\",g=a.lazyLoad.bgImgAttribute||\"data-bg-src\",h=\"DIV SECTION ARTICLE ASIDE B BODY FIGURE HTML I LI MAIN MARK NAV P SPAN STRONG SUMMARY TABLE TIME UL H1 H2 H3 H4 LABEL\".split(\" \"),e=a.lazyLoad.addClassOnLoad||\"\",a=\"boolean\"===typeof a.lazyLoad.enable?a.lazyLoad.enable:!0;!window.greta&&a&&setInterval(function(){for(var a,b=document.querySelectorAll(\"IMG[\"+f+\"]\"),c=0;c<b.length;c++)(a=b[c].getAttribute(f))&&b[c].src!==a&&(b[c].src=a,b[c].removeAttribute(f),0<e.length&&(b[c].className+=\" \"+e));for(c=0;c<h.length;c++)for(var b=document.querySelectorAll(h[c]+\"[\"+g+\"]\"),d=0;d<b.length;d++)(a=b[d].getAttribute(g))&&b[d].style.backgroundImage!==\"url(\"+a+\")\"&&(b[d].style.backgroundImage=\"url(\"+a+\")\",b[d].removeAttribute(g),0<e.length&&(b[d].className+=\" \"+e))},1E3)},1E3);"])
          (dom/script {:src     "https://cdn.greta.io/greta.min.js"
                       :type    common/text-javascript})
          (dom/script {:src "//assets.pinterest.com/js/pinit.js"
                       :async true
                       :defer true
                       :type common/text-javascript})

          (dom/script {:src "https://cdn.auth0.com/js/auth0/8.7/auth0.min.js"
                       :type common/text-javascript})

          ;<script src="//www.powr.io/powr.js" external-type="html"></script>

          (dom/script {:src  (common/budget-js-path)
                       :type common/text-javascript})

          (when (some? (:user-id auth))
            (common/inline-javascript [(str "mixpanel.identify(\"" (:user-id auth) "\");")
                                       (str "mixpanel.people.set_once(" (json/write-str {:$email (:email auth) :$last_name (:email auth)}) ");")]))

          ;; Powr can be loaded last, because it just hooks in to a div that we've specified.
          (dom/script {:src "//www.powr.io/powr.js"
                       :external-type "html"})

          (cond
            (= cljs-build-id "release")
            (if release?
              (common/inline-javascript ["env.web.main.runsulo()"])
              (common/inline-javascript ["env.web.main.run_fake_sulo()"]))

            (= cljs-build-id "devcards")
            nil

            :else
            (common/inline-javascript ["env.web.main.runsulo()"]))))
      )))
