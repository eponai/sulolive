(ns eponai.web.ui.coming-soon
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.shared :as shared]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.dom :as my-dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.parser.message :as msg]
    [eponai.client.auth :as auth]
    [eponai.client.utils :as utils]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as p]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.router :as router]))

(defui ComingSoonContent
  Object
  (show-login [this]
    (auth/show-lock (shared/by-key this :shared/auth-lock)))
  (do-login [this auth-res]
    (debug "Auth-result: " auth-res))
  (initLocalState [this]
    {:on-login-fn #(.show-login this)})
  (render [this]
    (let [{:keys [header-src]} (om/props this)
          {:keys [content-form]} (om/get-computed this)
          {:keys [on-login-fn]} (om/get-state this)]
      (div (->> (css/grid-row)
                (css/add-class :align-center)
                (css/add-class :content))
           (div
             (->>
               (css/grid-column)
               (css/grid-column-size {:small 12 :medium 10 :large 8})
               (css/text-align :center)
               (css/add-class ::css/callout))
             (dom/h1 nil "SULO")
             (dom/strong nil (dom/i #js {:className "fa fa-map-marker fa-fw"}) "Vancouver's local marketplace online")
             ;(dom/hr nil)
             ;(dom/h2 nil "Get on the early adopter train!")
             ;(dom/p nil "Enter your email and we’ll put you on our invite list for an exclusive beta.")
             content-form
             ;(dom/div #js {:className "callout transparent"})
             (dom/h2 #js {:className "coming-soon"} "Coming Soon, Summer '17")
             (dom/a #js {:onClick on-login-fn :className "enter"} (dom/strong nil "Already a member? Sign in!")))))))
(def ->ComingSoonContent (om/factory ComingSoonContent))

(defn callout-banner [open?]
  (callout/callout-small
    (cond->> (css/add-class :info-banner)
             (not open?)
             (css/add-class :invisible))
    (grid/row-column
      nil
      (dom/span nil "Sign up to check out the LIVE market when it opens!"))))

(defui ComingSoonBiz
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     :query/messages])
  Object
  (open-live-popup [this]
    (om/update-state! this assoc :live-open? true))
  (subscribe [this]
    #?(:cljs (let [form (.getElementById js/document "beta-vendor-subscribe-form")
                   params {:name  (.-value (.getElementById js/document "beta-NAME"))
                           :email (.-value (.getElementById js/document "beta-EMAIL"))
                           :site  (.-value (.getElementById js/document "beta-SITE"))}]
               (if (and (not-empty (:name params))
                        (not-empty (:email params))
                        (utils/valid-email? (:email params)))
                 (do (msg/om-transact! this `[(beta/vendor ~params)])
                     (om/update-state! this assoc :client-msg nil))
                 (let [message (cond (empty? (:email params))
                                     "Please provide an email."
                                     (not (utils/valid-email? (:email params)))
                                     "Please provide a valid email."
                                     (empty? (:name params))
                                     "Please provide the name of your brand.")]
                   (om/update-state! this assoc :client-msg message))))))
  (componentDidUpdate [this _ _]
    #?(:cljs
       (let [{:keys [live-open?]} (om/get-state this)]
         (when live-open?
           (.setTimeout js/window (fn [] (om/update-state! this assoc :live-open? false)) 5000)))))
  (render [this]
    (let [{:keys [proxy/navbar]} (om/props this)
          {:keys [on-login-fn live-open? client-msg]} (om/get-state this)
          message (msg/last-message this 'beta/vendor)]
      (common/page-container
        {:id     "sulo-sell-coming-soon"
         :navbar (om/computed navbar
                              {:on-live-click #(om/update-state! this assoc :live-open? true)
                               :right-menu    (menu/horizontal
                                                nil
                                                (menu/item-link
                                                  (css/add-class :contact {:href "/coming-soon"})
                                                  (my-dom/span nil (dom/span nil "Shop on SULO?"))
                                                  (my-dom/i {:classes ["fa fa-caret-right fa-fw"]})))})}
        (debug "Live opene: " live-open?)
        (p/header
          {:photo-id "static/coming-soon-sell-bg"
           :transformation :transformation/full}

          (callout-banner live-open?)
          (->ComingSoonContent
            (om/computed
              {}
              {:content-form (div nil
                                  (my-dom/hr nil)
                                  (my-dom/p nil "Are you a maker or artisan in Vancouver? Get in touch with us and sign up for our Beta invite list!")
                                  (dom/form #js {:id "beta-vendor-subscribe-form"}
                                            (grid/row
                                              (css/align :middle)
                                              (grid/column
                                                (grid/column-size {:small 12 :medium 2})
                                                (dom/label nil "Name"))
                                              (grid/column
                                                nil
                                                (my-dom/input {:type "email" :placeholder "Your Brand" :id "beta-NAME"})))
                                            (grid/row
                                              (css/align :middle)
                                              (grid/column
                                                (grid/column-size {:small 12 :medium 2})
                                                (my-dom/label nil "Email"))
                                              (grid/column
                                                nil
                                                (my-dom/input {:type "email" :placeholder "youremail@example.com" :id "beta-EMAIL"})))
                                            (grid/row
                                              (css/align :middle)
                                              (grid/column
                                                (grid/column-size {:small 12 :medium 2})
                                                (dom/label nil "Website"))
                                              (grid/column
                                                nil
                                                (my-dom/input {:type "text" :placeholder "yourwebsite.com (optional)" :id "beta-SITE"})))
                                            (grid/row
                                              (css/align :center)
                                              (my-dom/p nil
                                                        (my-dom/small nil
                                                                      (my-dom/span nil "By signing up you accept our ")
                                                                      (my-dom/a {:href    "//www.iubenda.com/privacy-policy/8010910"
                                                                                 :classes ["iubenda-nostyle no-brand iubenda-embed"]
                                                                                 :title   "Privacy Policy"}
                                                                                (my-dom/span nil "Privacy Policy")))))
                                            (grid/row
                                              (css/align :center)
                                              (my-dom/button {:classes ["button search"]
                                                              :onClick #(do (.preventDefault %)
                                                                            (when-not (msg/pending? message)
                                                                              (.subscribe this)))}
                                                             (if (msg/pending? message)
                                                               (my-dom/i {:classes ["fa fa-spinner fa-spin fa-fw"]})
                                                               "Invite me")))
                                            (grid/row
                                              (css/align :center)
                                              (my-dom/p {:classes [(cond
                                                                     (some? client-msg)
                                                                     "text-alert"
                                                                     (msg/final? message)
                                                                     (if (msg/success? message)
                                                                       "text-success"
                                                                       "text-alert"))]}
                                                        (cond
                                                          (not-empty client-msg)
                                                          client-msg
                                                          (msg/final? message)
                                                          (msg/message message)
                                                          :else
                                                          "")))))})))))))

(def ->ComingSoonBiz (om/factory ComingSoonBiz))

(defui ComingSoon
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/sell-soon (om/get-query ComingSoonBiz)}
     :query/current-route
     :query/messages])
  Object
  (componentDidUpdate [this _ _]
    #?(:cljs
       (let [{:keys [live-open? live-timer-started?]} (om/get-state this)]
         (when (and live-open? (not live-timer-started?))
           (.setTimeout js/window (fn [] (om/update-state! this assoc
                                                           :live-open? false
                                                           :live-timer-started? false)) 5000)
           (om/update-state! this assoc :live-timer-started? true)))))
  (subscribe-customer [this]
    #?(:cljs (let [email (web-utils/input-value-by-id "coming-soon-email-input")]
               (if (and (not-empty email)
                        (utils/valid-email? email))
                 (do (msg/om-transact! this `[(beta/customer ~{:email email})])
                     (om/update-state! this assoc :client-msg nil))
                 (let [message (cond (empty? email)
                                     "Please provide an email."
                                     (not (utils/valid-email? email))
                                     "Please provide a valid email."
                                     (empty? email)
                                     "Please provide the name of your brand.")]
                   (om/update-state! this assoc :client-msg message))))))
  (render [this]
    (if (= :coming-soon/sell (get-in (om/props this) [:query/current-route :route]))
      (let [{:proxy/keys [sell-soon]} (om/props this)]
        (->ComingSoonBiz sell-soon))
      (let [{:keys [proxy/navbar]} (om/props this)
            {:keys [on-login-fn live-open? client-msg]} (om/get-state this)
            message (msg/last-message this 'beta/customer)]
        (dom/div #js {:id "sulo-coming-soon" :className "sulo-page"}
          (common/page-container
            {:navbar (om/computed navbar {:coming-soon?  true
                                          :on-live-click #(om/update-state! this assoc :live-open? true)
                                          :right-menu    (menu/horizontal
                                                           nil
                                                           (menu/item-link
                                                             (css/add-class :contact {:href "/sell/coming-soon"})
                                                             (my-dom/span nil (dom/span nil "Sell on SULO?"))
                                                             (dom/i #js {:className "fa fa-caret-right fa-fw"})))})}

            (p/header
              {:photo-id "static/home-header-bg"
               :transformation :transformation/full}

              (callout-banner live-open?)
              (->ComingSoonContent
                (om/computed {}
                             {:content-form (dom/div
                                              nil
                                              (dom/hr nil)
                                              (dom/p nil "Do you love to shop local in Vancouver? Get early access and be part of creating an interactive community with makers online! Invitations will be sent out this summer.")
                                              (dom/form
                                                nil
                                                (div (->> (css/grid-row)
                                                          (css/align :middle))
                                                     (div (->> (css/grid-column))
                                                          (dom/input #js {:type "email" :placeholder "youremail@example.com" :id "coming-soon-email-input"})))
                                                (div (->> (css/grid-row)
                                                          (css/align :center))
                                                     (dom/p nil (dom/small nil "By signing up you accept our "
                                                                           (dom/a #js {:href      "//www.iubenda.com/privacy-policy/8010910"
                                                                                       :className "iubenda-nostyle no-brand iubenda-embed"
                                                                                       :title     "Privacy Policy"}
                                                                                  "Privacy Policy"))))
                                                (div (->> (css/grid-row)
                                                          (css/align :center))
                                                     (dom/button #js {:className "button search"
                                                                      :onClick   #(do
                                                                                   (.preventDefault %)
                                                                                   (when-not (msg/pending? message)
                                                                                     (.subscribe-customer this)))

                                                                      :type      "submit"}
                                                                 (if (msg/pending? message)
                                                                   (my-dom/i {:classes ["fa fa-spinner fa-spin fa-fw"]})
                                                                   "Get early access")))
                                                (grid/row
                                                  (css/align :center)
                                                  (my-dom/p {:classes [(cond
                                                                      (some? client-msg)
                                                                      "text-alert"
                                                                      (msg/final? message)
                                                                      (if (msg/success? message)
                                                                        "text-success"
                                                                        "text-alert"))]}
                                                         (cond
                                                           (not-empty client-msg)
                                                           client-msg
                                                           (msg/final? message)
                                                           (msg/message message)
                                                           :else
                                                           "")))))})))))))))

(def ->ComingSoon (om/factory ComingSoon))

(router/register-component :coming-soon ComingSoon)