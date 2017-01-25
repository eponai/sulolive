(ns eponai.common.ui.index
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.photo :as photo]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.dom :as my-dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]
    [eponai.client.auth :as auth]
    [eponai.common.ui.elements.menu :as menu]))

(defn top-feature [opts icon title text]
  (dom/div #js {:className "feature-item column"}
    (div
      (->> (css/grid-row)
           (css/add-class :align-middle))
      (div (->> (css/grid-column)
                (css/grid-column-size {:small 2 :medium 12}))
           (dom/i #js {:className (str "fa fa-fw " icon)}))
      (div (css/grid-column)
           (dom/strong #js {:className "feature-title"} title)
           (dom/p nil text)))))

(defn banner [{:keys [color align] :as opts} & content]
  (let [align (or align :left)
        color (or color :default)]
    (dom/div #js {:className (str "banner " (name color))}
      (dom/div #js {:className "row"}
        (apply dom/div #js {:className (str "column small-12 medium-8"
                                        (when (= (name align) "right")
                                          " medium-offset-4 text-right"))}
               content)))))

(defn content-section [{:keys [href class sizes]} header content footer]
  (div
    (->> {:classes [class]}
         (css/add-class :section))
    ;(div
    ;  (->> (css/grid-row) css/grid-column))
    (div
      (->> (css/grid-row) (css/add-class :section-header) (css/add-class :small-unstack))
      (div (->> (css/grid-column) (css/add-class :middle-border)))
      (div (->> (css/grid-column) (css/add-class :shrink))
           (dom/h3 nil header))
      (div (->> (css/grid-column) (css/add-class :middle-border)))
      )

    content
    (when (not-empty footer)
      (div
        (->> (css/grid-row) css/grid-column (css/add-class :section-footer) (css/text-align :center))
        (dom/a #js {:href href :className "button gray hollow"} footer)))
    ))

(defn collection-element [{:keys [url title square?]}]
  ;; Use the whole thing as hover elem
  (my-dom/a
    {:href (str "/goods?category=" (.toLowerCase title))
     :classes [:full]}
    (photo/with-overlay
      nil
      (if square?
        (photo/full {:src url})
        (photo/photo {:src url}))
      (my-dom/div
        (->> (css/text-align :center))
        (dom/p nil (dom/strong nil title))))))

(defui Index
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:db/id
                             :item/name
                             :item/price
                             :item/id
                             :item/img-src
                             {:item/store [:store/name]}]}
     {:query/featured-stores [:db/id
                              :store/name
                              :store/featured
                              :store/featured-img-src
                              :store/rating
                              :store/review-count
                              :store/photo
                              {:item/_store [:db/id :item/img-src]}]}
     {:query/featured-streams [:db/id :stream/name {:stream/store [:db/id  :store/name]} :stream/viewer-count :stream/img-src]}])
  Object
  #?(:cljs
     (on-scroll [this]
                (let [scroll-limit 145
                      font-difference 2
                      header (.getElementById js/document "header-content")
                      navbar-brand (.getElementById js/document "navbar-brand")
                      body (.getElementById js/document "sulo-index")
                      quote (/ font-difference scroll-limit)
                      scroll-value (.-scrollTop body)]

                  (if (> scroll-limit scroll-value)
                    (do
                      (set! (.-fontSize (.-style header)) (str (inc (* (- scroll-limit scroll-value) quote)) "rem"))
                      (set! (.-opacity (.-style header)) (* (- scroll-limit scroll-value) quote))
                      (set! (.-opacity (.-style navbar-brand)) (/ 1 (- scroll-limit scroll-value)))
                      )
                    (do
                      (set! (.-opacity (.-style header)) 0)
                      (set! (.-opacity (.-style navbar-brand)) 1)))
                  )))
  (initLocalState [this]
    {:on-scroll-fn #(.on-scroll this)})
  #?(:cljs
     (componentWillUnmount [this]
                           (let [body (.getElementById js/document "sulo-index")
                                 {:keys [lock on-scroll-fn]} (om/get-state this)]
                             ;(.removeEventListener body "scroll" on-scroll-fn)
                             )))
  #?(:cljs
     (componentDidMount [this]
                        (debug "Component did mount")
                       (let [body (.getElementById js/document "sulo-index")
                             navbar-brand (.getElementById js/document "navbar-brand")
                             {:keys [lock on-scroll-fn]} (om/get-state this)]
                         (debug "Scroll body: " body)
                         ;(set! (.-opacity (.-style navbar-brand)) 0)
                         ;(.addEventListener body "scroll" on-scroll-fn)
                         )))
  (render [this]
    (let [{:keys [proxy/navbar query/featured-items query/featured-stores query/featured-streams]} (om/props this)
          {:keys [input-search]} (om/get-state this)]
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
                    (dom/h2 nil "Your local marketplace online")
                    (dom/p nil (dom/strong nil (dom/i #js {:className "fa fa-map-marker fa-fw"}) "Vancouver, BC")))

                  (div (->> (css/grid-row)
                            (css/add-class :search-container))
                       (div (->> (css/grid-column)
                                 (css/grid-column-size {:small 12 :medium 8}))
                            (dom/input #js {:placeholder "What are you looking for?"
                                            :type        "text"
                                            :value       (or input-search "")
                                            :onChange    #(do (debug " search " (.. % -target -value)) (om/update-state! this assoc :input-search (.. % -target -value)))
                                            :onKeyDown   (fn [e]
                                                           #?(:cljs
                                                              (when (= 13 (.. e -keyCode))
                                                                (let [search-string (.. e -target -value)]
                                                                  (set! js/window.location (str "/goods?search=" search-string))))))}))
                       (div (->> (css/grid-column)
                                 (css/grid-column-size {:small 4 :medium 3})
                                 (css/text-align :left))
                            (dom/a #js {:className "button expanded highlight"
                                        :onClick   (fn []
                                                     #?(:cljs
                                                        (set! js/window.location (str "/goods?search=" input-search))))}
                                   "Search"))
                       )
                  )))

            (dom/div #js {:className "top-features"}
              (dom/div #js {:className " row small-up-1 medium-up-3"}
                (top-feature
                  nil
                  "fa-shopping-bag"
                  "Shop and Discover"
                  "Get lost in a marketplace filled with your local gems.")
                (top-feature
                  nil
                  "fa-video-camera"
                  "Watch, chat and follow"
                  "Hang out with your favourite local brands on SULO LIVE.")
                (top-feature
                  nil
                  "fa-heart"
                  "Join the Community"
                  "Sign up and follow, like and share your faves with others.")))


            (content-section {:href  "/streams"
                              :class "online-channels"}
                             "Stores streaming right now"
                             (div (->> (css/grid-row)
                                       (css/grid-row-columns {:small 2 :medium 4}))
                                  (map (fn [c]
                                         (common/online-channel-element c))
                                       featured-streams))
                             "See More")

            (content-section {:class "collections"}
                             "Shop by collection"
                             (div nil
                                  (div
                                    (->> (css/grid-row))
                                    (div
                                      (->> (css/grid-column)
                                           (css/add-class :content-item))
                                      (collection-element {:url   "/assets/img/collection-home-5.jpg"
                                                           :title "Home"}))
                                    (div
                                      (->> (css/grid-column)
                                           (css/add-class :content-item)
                                           (css/grid-column-size {:small 12 :medium 5}))
                                      (collection-element {:url   "/assets/img/collection-women-2.jpg"
                                                           :title "Women"
                                                           :square? true})))
                                  (div
                                    (->> (css/grid-row))
                                    (div
                                      (->> (css/grid-column)
                                           (css/add-class :content-item))
                                      (collection-element {:url   "/assets/img/collection-men-2.jpg"
                                                           :title "Men"}))
                                    (div
                                      (->> (css/grid-column)
                                           (css/add-class :content-item)
                                           (css/grid-column-size {:small 12 :medium 5}))
                                      (collection-element {:url   "/assets/img/collection-kids-4.jpg"
                                                           :title "Kids"
                                                           :square? true}))))
                             ;(map (fn [s t]
                             ;       (collection-element {:url (first (:store/featured-img-src s))
                             ;                            :title t}))
                             ;     featured-stores
                             ;     ["Home" "Kids" "Women" "Men"])
                             ""
                             )

            (content-section {:href  "/goods"}
                             "New arrivals"
                             (div (->> (css/grid-row)
                                       (css/grid-row-columns {:small 2 :medium 4 :large 5}))
                                  (map (fn [p]
                                         (common/product-element {:open-url? true} p))
                                       featured-items))
                             "See More")

            (banner {:color :default}
                    (dom/h2 nil "Watch, shop and chat with your favorite vendors and artisans.")
                    (dom/p nil "Follow and stay up-to-date on when they're online to meet you!")
                    (dom/a #js {:className "button"} "Join"))

            (banner {:color :white
                     :align :right}
                    (dom/h2 nil "Open your own shop on SULO and tell your story to Vancouver.")
                    (dom/p nil "Enjoy a community that lives for local.")
                    (dom/a #js {:className "button gray hollow"} "Contact Us"))))))))


(def ->Index (om/factory Index))

(defui ComingSoonContent
  Object
  #?(:cljs
     (show-login [this]
                 (let [{:keys [lock]} (om/get-state this)]
                   (.show lock (clj->js {:allowedConnections ["Username-Password-Authentication"]
                                         :auth               {:params       {:state js/window.location.origin
                                                                             :scope            "openid email"}}})))))
  #?(:cljs
     (do-login [this auth-res]
               (debug "Auth-result: " auth-res)))
  #?(:cljs
     (componentDidMount [this]
                        (when js/Auth0Lock
                          (let [lock (new js/Auth0Lock
                                          "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh"
                                          "sulo.auth0.com"
                                          (clj->js {:auth {:redirectUrl  (str js/window.location.origin "/auth")}
                                                    :languageDictionary {:title "SULO"}
                                                    :theme              {:primaryColor "#240061"}}))]
                            (.on lock "authenticated" (fn [res]
                                                        (debug "Login result: " res)
                                                        (.getUserInfo (.-accessToken res)
                                                                      (fn [e p]
                                                                        (if e
                                                                          (error e)
                                                                          (auth/set-logged-in-token (.-accessToken res)))))))
                            (om/update-state! this assoc :lock lock)))))
  (initLocalState [this]
    {:on-login-fn #?(:cljs #(.show-login this) :clj (fn [] nil))})
  (render [this]
    (let [{:keys [header-src]} (om/props this)
          {:keys [content-form]} (om/get-computed this)]
      (photo/header
        {:src header-src}
        (div (->> (css/grid-row)
                  (css/add-class :align-center))
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
               ;(dom/a #js {:onClick on-login-fn :className "enter"} (dom/strong nil "Already a member? Sign In >>"))
               ))

        ;(div (->> (css/grid-row)
        ;          (css/text-align :center))
        ;     (div (->> (css/grid-column)
        ;               (css/add-class ::css/callout)
        ;               (css/add-class ::css/secondary))
        ;          (dom/strong nil "Coming Soon, Spring '17")))
        ))))
(def ->ComingSoonContent (om/factory ComingSoonContent))

(defui ComingSoon
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (render [this]
    (let [{:keys [proxy/navbar]} (om/props this)
          {:keys [lock on-login-fn]} (om/get-state this)]

      (dom/div #js {:id "sulo-coming-soon" :className "sulo-page"}
        (common/page-container
          {:navbar (om/computed navbar {:coming-soon? true
                                        :right-menu (menu/horizontal
                                                      nil
                                                      (menu/item-link
                                                        (css/add-class :contact {:href "/sell/coming-soon"})
                                                        (my-dom/span (css/show-for {:size :small}) (dom/span nil "Sell on SULO?"))
                                                        (dom/i #js {:className "fa fa-caret-right fa-fw"})))})}

          (->ComingSoonContent (om/computed {:header-src "/assets/img/coming-soon-bg.jpg"}
                                            {:content-form (dom/form
                                                             nil
                                                             (div (->> (css/grid-row)
                                                                       (css/align :center))
                                                                  (div (->> (css/grid-column)
                                                                            (css/grid-column-size {:small 12 :medium 8 :large 8}))
                                                                       (dom/input #js {:type "email" :placeholder "you@email.com"}))
                                                                  (div (->> (css/grid-column)
                                                                            (css/add-class :shrink))
                                                                       (dom/button #js {:className "button green" :type "submit"} "Get Early Access"))))})))))))

(def ->ComingSoon (om/factory ComingSoon))

(defui ComingSoonBiz
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}])

  Object
  (render [this]
    (let [{:keys [proxy/navbar]} (om/props this)
          {:keys [lock on-login-fn]} (om/get-state this)]

      (dom/div #js {:id "sulo-sell-coming-soon" :className "sulo-page"}
        (common/page-container
          {:navbar (om/computed navbar {:coming-soon? true
                                        :right-menu (menu/horizontal
                                                      nil
                                                      (menu/item-link
                                                        (css/add-class :contact {:href "/coming-soon"})
                                                        (my-dom/span (css/show-for {:size :small}) (dom/span nil "Shop on SULO?"))
                                                        (dom/i #js {:className "fa fa-caret-right fa-fw"})))})}

          (->ComingSoonContent
            (om/computed {:header-src "/assets/img/coming-soon-sell-bg.jpg"}
                         {:content-form (div nil
                                             (dom/hr nil)
                                             (dom/p nil "Are you a maker or artisan in Vancouver? Get in touch with us!")
                                             (dom/form nil
                                                       (div (->> (css/grid-row)
                                                                 (css/align :middle))
                                                            (div (->> (css/grid-column)
                                                                      (css/grid-column-size {:small 12 :medium 2}))
                                                                 (dom/label nil "Name"))
                                                            (div (->> (css/grid-column))
                                                                 (dom/input #js {:type "email" :placeholder "Company Name"})))
                                                       (div (->> (css/grid-row)
                                                                 (css/align :middle))
                                                            (div (->> (css/grid-column)
                                                                      (css/grid-column-size {:small 12 :medium 2}))
                                                                 (dom/label nil "Website"))
                                                            (div (->> (css/grid-column))
                                                                 (dom/input #js {:type "text" :placeholder "yourwebsite.com"})))
                                                       (div (->> (css/grid-row)
                                                                 (css/align :middle))
                                                            (div (->> (css/grid-column)
                                                                      (css/grid-column-size {:small 12 :medium 2}))
                                                                 (dom/label nil "Email"))
                                                            (div (->> (css/grid-column))
                                                                 (dom/input #js {:type "email" :placeholder "you@email.com"})))
                                                       (dom/button #js {:className "button green" :type "submit"} "Tell Me More")))})))))))

(def ->ComingSoonBiz (om/factory ComingSoonBiz))