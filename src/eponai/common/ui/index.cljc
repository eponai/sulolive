(ns eponai.common.ui.index
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.product-item :as pi]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.dom :as my-dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.parser.message :as msg]
    [eponai.client.routes :as routes]
    [eponai.client.auth :as auth]
    [eponai.client.utils :as utils]
    [eponai.common.ui.icons :as icons]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]))

(defn top-feature [opts icon title text]
  (dom/div #js {:className "feature-item column"}
    (div
      (->> (css/grid-row)
           (css/add-class :align-middle))
      (div (->> (css/grid-column)
                (css/grid-column-size {:small 2 :medium 12}))
           icon)
      (div (css/grid-column)
           (dom/strong #js {:className "feature-title"} title)
           (dom/p nil text)))))

(defn banner [{:keys [color align] :as opts} primary secondary]
  (let [align (or align :left)
        color (or color :default)]
    (dom/div #js {:className (str "banner " (name color))}
      (grid/row
        nil
        (grid/column
          (cond->> (->> (grid/column-size {:small 9 :medium 8})
                       (css/text-align align))
                  (= align :right)
                  (grid/column-offset {:small 3 :medium 4}))
          primary)
        (grid/column
          (css/align :right)
          secondary)))))

(defn collection-element [{:keys [url title full?]}]
  ;; Use the whole thing as hover elem
  (my-dom/a
    {:href    (routes/url :products/categories {:category (.toLowerCase title)})
     :classes [:full :category-photo]}
    (photo/with-overlay
      nil
      (photo/photo {:src url})
      ;(if full?
      ;  (photo/full {:src url})
      ;  (photo/photo {:src url}))
      (my-dom/div
        (->> (css/text-align :center))
        (dom/span nil title)))))

(defui Index
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             {:store.item/photos [:photo/path]}
                             {:store/_items [:store/name]}]}
     {:query/featured-stores [:db/id
                              :store/name
                              :store/featured
                              :store/featured-img-src
                              {:store/photo [:photo/path]}
                              {:store/items [:db/id {:store.item/photos [:photo/path]}]}]}
     {:query/featured-streams [:db/id :stream/title {:stream/store [:db/id :store/name {:store/photo [:photo/path]}]}]}])
  Object
  (render [this]
    (let [{:keys [proxy/navbar query/featured-items query/featured-streams]} (om/props this)
          {:keys [input-search]} (om/get-state this)]
      (debug "Featured items: " featured-items)
      (dom/div #js {:id "sulo-index" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (dom/div #js {:id "sulo-index-container" :onScroll #(debug "Did scroll page: " %)}

            (photo/header
              (css/add-class :center {:src "/assets/img/home-header-bg.jpg"})
              (div
                (->> (css/grid-row)
                     (css/add-class :intro-header)
                     (css/add-class :align-middle))

                (div
                  (->>
                    (css/grid-column)
                    (css/add-class :header-content)
                    (css/text-align :center)
                    (css/grid-column-offset {:large 5 :medium 2}))
                  (div
                    (css/text-align :left)
                    (dom/h1 #js {:id "header-content" :className "show-for-sr"} "SULO")
                    ;(dom/h1 nil "LO" (dom/small nil "CAL"))
                    (dom/h2 #js {:className "header"} "Your local marketplace online")
                    (dom/p nil (dom/strong nil (dom/i #js {:className "fa fa-map-marker fa-fw"}) "Vancouver, BC")))

                  (div (->> (css/grid-row)
                            (css/add-class :search-container))
                       (div (->> (css/grid-column)
                                 (css/grid-column-size {:small 12 :medium 8}))
                            (dom/input #js {:className "drop-shadow"
                                            :placeholder "What are you looking for?"
                                            :type        "text"
                                            :value       (or input-search "")
                                            :onChange    #(do (debug " search " (.. % -target -value)) (om/update-state! this assoc :input-search (.. % -target -value)))
                                            :onKeyDown   (fn [e]
                                                           #?(:cljs
                                                              (when (= 13 (.. e -keyCode))
                                                                (let [search-string (.. e -target -value)]
                                                                  (set! js/window.location (str "/products?search=" search-string))))))}))
                       (div (->> (css/grid-column)
                                 (css/grid-column-size {:small 4 :medium 3})
                                 (css/text-align :left))
                            (dom/a #js {:className "button expanded search drop-shadow"
                                        :onClick   (fn []
                                                     #?(:cljs
                                                        (set! js/window.location (str "/products?search=" input-search))))}
                                   "Search"))
                       )
                  )))

            (dom/div #js {:className "top-features"}
              (dom/div #js {:className " row small-up-1 medium-up-3"}
                (top-feature
                  nil
                  (icons/shopping-bag)
                  ;(dom/img #js {:src "/assets/img/icons/shopping-bag.png"})
                  ;(dom/i #js {:className (str "fa fa-fw fa-shopping-bag")})
                  "Shop and Discover"
                  "Get lost in a marketplace filled with your local gems.")
                (top-feature
                  nil
                  (icons/video-camera)
                  "Watch, chat and follow"
                  "Hang out with your favourite local brands on SULO LIVE.")
                (top-feature
                  nil
                  (icons/heart)
                  "Join the Community"
                  "Sign up to follow others and share your faves.")))


            (common/content-section {:href  "/streams"
                                     :class "online-channels"}
                                    "Stores streaming right now"
                                    (div (->> (css/grid-row)
                                              (css/grid-row-columns {:small 2 :medium 4}))
                                         (map (fn [c]
                                                (common/online-channel-element c))
                                              featured-streams))
                                    "See More")

            (common/content-section {:class "collections"}
                                    "Shop by collection"
                                    (div nil
                                         (grid/row
                                           (grid/columns-in-row {:small 1 :medium 2})
                                           (grid/column
                                             (->> (css/add-class :content-item)
                                                  (css/add-class :collection-item))
                                             (collection-element {:url   "/assets/img/home-new.jpg"
                                                                  :title "Home"}))
                                           (grid/column
                                             (->> (css/add-class :content-item)
                                                  (css/add-class :collection-item))
                                             (collection-element {:url   "/assets/img/women-new.jpg"
                                                                  :title "Women"}))
                                           (grid/column
                                             (->> (css/add-class :content-item)
                                                  (css/add-class :collection-item))
                                             (collection-element {:url   "/assets/img/men-new.jpg"
                                                                  :title "Men"}))
                                           (grid/column
                                             (->> (css/add-class :content-item)
                                                  (css/add-class :collection-item))
                                             (collection-element {:url   "/assets/img/kids-new.jpg"
                                                                  :title "Kids"}))))
                                    ;(map (fn [s t]
                                    ;       (collection-element {:url (first (:store/featured-img-src s))
                                    ;                            :title t}))
                                    ;     featured-stores
                                    ;     ["Home" "Kids" "Women" "Men"])
                                    ""
                                    )

            (common/content-section {:href (routes/url :products nil)
                                     :class "new-arrivals"}
                                    "New arrivals"
                                    (div (->> (css/grid-row)
                                              (css/grid-row-columns {:small 2 :medium 4}))
                                         (map-indexed
                                           (fn [i p]
                                             (my-dom/div
                                               (css/grid-column {:key (str i)})
                                               (pi/product-element {:open-url? true} p)))
                                           (take 4 featured-items)))
                                    "See More")

            (banner {:color :default}
                    (dom/div nil
                      (dom/h2 nil "Watch, shop and chat with your favorite vendors and artisans.")
                      (dom/p nil "Follow and stay up-to-date on when they're online to meet you!")
                      (dom/a #js {:className "button"} "Join"))
                    (icons/heart-drawing))

            (banner {:color :white
                     :align :right}
                    (dom/div nil
                      (dom/h2 nil "Open your own shop on SULO and tell your story to Vancouver.")
                      (dom/p nil "Enjoy a community that lives for local.")
                      (dom/a #js {:className "button gray hollow"} "Contact Us"))
                    nil)))))))


(def ->Index (om/factory Index))

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
  (div
    (cond->> {:classes [::css/callout ::css/primary :info-banner]}
             (not open?)
             (css/add-class :invisible))
    ;(div (->> (css/add-class :close-button)) (dom/small nil "x"))
    (div (->> (css/grid-row)
              css/grid-column)
         (dom/span nil "Sign up to check out the LIVE market when it opens!"))))

(defui ComingSoon
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
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

          (photo/header
            {:src
             ;; TODO: We should resolve the s3 path to a configurable cloudfront path
             ;; "https://s3.amazonaws.com/sulo-images/site/home-header-bg.jpg"
             "https://d30slnyi7gxcwc.cloudfront.net/site/home-header-bg.jpg"
             }

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
                                                   (dom/p #js {:className "alert"} client-msg))))}))))))))

(def ->ComingSoon (om/factory ComingSoon))

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
          (photo/header
            {:src

             ;;TODO: Resolve s3 paths to cloudfront paths
             ;;"https://s3.amazonaws.com/sulo-images/site/coming-soon-sell-bg.jpg"
             "https://d30slnyi7gxcwc.cloudfront.net/site/coming-soon-sell-bg.jpg"
             }

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

(defui Login
  static om/IQuery
  (query [this]
    [{:proxy/index (om/get-query Index)}])
  Object
  (componentDidMount [this]
    (auth/show-lock (:shared/auth-lock (om/shared this))))
  (render [this]
    (->Index (:proxy/index (om/props this)))))

(def ->Login (om/factory Login))

(defui Unauthorized
  static om/IQuery
  (query [this]
    [:query/messages])
  Object
  (render [this]
    (dom/div
      nil
      (dom/strong nil "UNAUTHORIZED :("))))

(def ->Unauthorized (om/factory Unauthorized))