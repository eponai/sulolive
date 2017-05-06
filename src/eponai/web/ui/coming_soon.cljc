(ns eponai.web.ui.coming-soon
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
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
    (auth/show-lock (:shared/auth-lock (om/shared this))))
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
    (let [{:keys [proxy/navbar]} (om/get-computed this)
          {:keys [on-login-fn live-open? client-msg]} (om/get-state this)
          message (msg/last-message this 'beta/vendor)]
      (dom/div #js {:id "sulo-sell-coming-soon" :className "sulo-page"}
        (common/page-container
          {:navbar (om/computed navbar {:coming-soon?  true
                                        :on-live-click #(om/update-state! this assoc :live-open? true)
                                        :right-menu    (menu/horizontal
                                                         nil
                                                         (menu/item-link
                                                           (css/add-class :contact {:href "/coming-soon"})
                                                           (my-dom/span nil (dom/span nil "Shop on SULO?"))
                                                           (dom/i #js {:className "fa fa-caret-right fa-fw"})))})}
          (debug "Live opene: " live-open?)
          (p/header
            {:photo-id "static/coming-soon-sell-bg"}

            (callout-banner live-open?)
            (->ComingSoonContent
              (om/computed {}
                           {:content-form (div nil
                                               (dom/hr nil)
                                               (dom/p nil "Are you a maker or artisan in Vancouver? Get in touch with us and sign up for our Beta invite list!")
                                               (dom/form #js {:id "beta-vendor-subscribe-form"}
                                                         (div (->> (css/grid-row)
                                                                   (css/align :middle))
                                                              (div (->> (css/grid-column)
                                                                        (css/grid-column-size {:small 12 :medium 2}))
                                                                   (dom/label nil "Name"))
                                                              (div (->> (css/grid-column))
                                                                   (dom/input #js {:type "email" :placeholder "Your Brand" :id "beta-NAME"})))
                                                         (div (->> (css/grid-row)
                                                                   (css/align :middle))
                                                              (div (->> (css/grid-column)
                                                                        (css/grid-column-size {:small 12 :medium 2}))
                                                                   (dom/label nil "Email"))
                                                              (div (->> (css/grid-column))
                                                                   (dom/input #js {:type "email" :placeholder "youremail@example.com" :id "beta-EMAIL"})))
                                                         (div (->> (css/grid-row)
                                                                   (css/align :middle))
                                                              (div (->> (css/grid-column)
                                                                        (css/grid-column-size {:small 12 :medium 2}))
                                                                   (dom/label nil "Website"))
                                                              (div (->> (css/grid-column))
                                                                   (dom/input #js {:type "text" :placeholder "yourwebsite.com (optional)" :id "beta-SITE"})))
                                                         (div (->> (css/grid-row)
                                                                   (css/align :center))
                                                              (dom/p nil (dom/small nil "By signing up you accept our "
                                                                                    (dom/a #js {:href      "//www.iubenda.com/privacy-policy/8010910"
                                                                                                :className "iubenda-nostyle no-brand iubenda-embed"
                                                                                                :title     "Privacy Policy"}
                                                                                           "Privacy Policy"))))
                                                         (div (->> (css/grid-row)
                                                                   (css/align :center))
                                                              (dom/button #js {:className "button green"
                                                                               :onClick   #(do (.preventDefault %)
                                                                                               (.subscribe this))}
                                                                          (if (msg/pending? message)
                                                                            (dom/i #js {:className "fa fa-spinner fa-spin fa-fw"})
                                                                            "Invite Me")))
                                                         (div (->> (css/grid-row)
                                                                   (css/align :center))
                                                              (dom/p #js {:className (cond
                                                                                       (some? client-msg)
                                                                                       "alert"
                                                                                       (msg/final? message)
                                                                                       (if (msg/success? message)
                                                                                         "success"
                                                                                         "alert"))}
                                                                     (cond
                                                                       (not-empty client-msg)
                                                                       client-msg
                                                                       (msg/final? message)
                                                                       (msg/message message)
                                                                       :else
                                                                       "")))))}))))))))

(def ->ComingSoonBiz (om/factory ComingSoonBiz))

(defui ComingSoon
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/sell-soon (om/get-query ComingSoonBiz)}
     :query/current-route])
  Object
  (componentDidUpdate [this _ _]
    #?(:cljs
       (let [{:keys [live-open? live-timer-started?]} (om/get-state this)]
         (when (and live-open? (not live-timer-started?))
           (.setTimeout js/window (fn [] (om/update-state! this assoc
                                                           :live-open? false
                                                           :live-timer-started? false)) 5000)
           (om/update-state! this assoc :live-timer-started? true)))))
  (render [this]
    (if (= :coming-soon/sell (get-in (om/props this) [:query/current-route :route]))
      (let [{:proxy/keys [navbar sell-soon]} (om/props this)]
        (->ComingSoonBiz (om/computed sell-soon
                                      {:proxy/navbar navbar})))
      (let [{:keys [proxy/navbar]} (om/props this)
            {:keys [on-login-fn live-open? client-msg]} (om/get-state this)]
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
              {:photo-id "static/home-header-bg"}

              (callout-banner live-open?)
              (->ComingSoonContent
                (om/computed {}
                             {:content-form (dom/div
                                              nil
                                              (dom/hr nil)
                                              (dom/p nil "Do you love to shop local in Vancouver? Get early access and be part of creating an interactive community with makers online! Invitations will be sent out this spring.")
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
                                                     (dom/button #js {:className "button green"
                                                                      :onClick   #?(:clj  identity
                                                                                    :cljs (fn [e]
                                                                                            (let [input (.-value (.getElementById js/document "coming-soon-email-input"))
                                                                                                  valid-email? (utils/valid-email? input)]
                                                                                              (when-not valid-email?
                                                                                                (om/update-state! this assoc :client-msg
                                                                                                                  "Please enter a valid email.")
                                                                                                (.preventDefault e)))))
                                                                      :type      "submit"}
                                                                 "Get Early Access"))
                                                (div (->> (css/grid-row)
                                                          (css/align :center))
                                                     (dom/p #js {:className "alert"} client-msg))))})))))))))

(def ->ComingSoon (om/factory ComingSoon))

(router/register-component :coming-soon ComingSoon)