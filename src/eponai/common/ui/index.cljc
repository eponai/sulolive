(ns eponai.common.ui.index
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.photo :as photo]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.dom :as my-dom :refer [div a]]
    [eponai.common.ui.elements.css :as css]))

(defn top-feature [opts icon title text]
  (dom/div #js {:className "feature-item column"}
    (div
      (css/grid-row)
      (dom/div #js {:className "column align-self-middle small-2 medium-12"}
        (dom/i #js {:className (str "fa fa-fw " icon)}))
      (dom/div #js {:className "text-content columns"}
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
         css/grid-row
         css/grid-column
         (css/add-class :section))
    ;(div
    ;  (->> (css/grid-row) css/grid-column))
    (div
      (->> (css/grid-row) (css/add-class :section-header) (css/add-class :small-unstack))
      (div (->> (css/grid-column) (css/add-class :middle-border)))
      (div (->> (css/grid-column) (css/add-class :shrink))
           (dom/h3 nil header))
      (div (->> (css/grid-column) (css/add-class :middle-border)))
      ;(a {:href href}
      ;   (dom/span nil "See more >>"))
      )

    (apply div
           (->> (css/grid-row)
                (css/grid-row-columns sizes))
           content)
    (div
      (->> (css/grid-row) css/grid-column (css/add-class :section-footer) (css/text-align :right))
      (dom/a #js {:href href} footer))
    ))

(defn collection-element [{:keys [url title]}]
  (div
    (->> (css/grid-column)
         (css/add-class :content-item))

    ;; Use the whole thing as hover elem
    (my-dom/a
      {:href (str "/goods?category=" (.toLowerCase title))}
      (photo/with-overlay
        nil
        (photo/photo {:src url})
        (my-dom/div
          (->> (css/text-align :center))
          (dom/p nil (dom/strong nil title)))))))

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

            (dom/div #js {:className "intro-header"}
              (dom/div #js {:className "row"}
                (dom/div #js {:className "column small-offset-6 header-photo-container"}
                  ;(photo/header
                  ;  {:src "/assets/img/night-market.jpg"})
                  ))
              (div
                (->>
                  (css/grid-row)
                  css/grid-column
                  (css/add-class :header-content)
                  (css/text-align :center)
                  (css/add-class :large-offset-4))
                (div
                  (css/text-align :left)
                  (dom/h1 #js {:id "header-content"} "SULO")
                  ;(dom/h1 nil "LO" (dom/small nil "CAL"))
                  (dom/h2 nil "Vancouver's local marketplace online"))
                (dom/div #js {:className "search-container row"}
                  (dom/div #js {:className "column small-12 medium-6"}
                    (dom/input #js {:placeholder "What are you looking for?"
                                    :type        "text"
                                    :value       (or input-search "")
                                    :onChange    #(do (debug " search " (.. % -target -value)) (om/update-state! this assoc :input-search (.. % -target -value)))
                                    :onKeyDown   (fn [e]
                                                   #?(:cljs
                                                      (when (= 13 (.. e -keyCode))
                                                        (let [search-string (.. e -target -value)]
                                                          (set! js/window.location (str "/goods?search=" search-string))))))}))
                  (dom/div #js {:className "column text-left small-4 medium-2"}
                    (dom/a #js {:className "button expanded"
                                :onClick   (fn []
                                             #?(:cljs
                                                (set! js/window.location (str "/goods?search=" input-search))))}
                           "Search"))
                  )
                ))


            (content-section {:href  "/streams"
                              :sizes {:small 2 :medium 4}
                              :class "online-channels"}
                             "Stores streaming at the market right now"
                             (map (fn [c]
                                    (common/online-channel-element c))
                                  featured-streams)
                             "Discover more at the LIVE market >>")

            ;(dom/div #js {:className "top-features"}
            ;  (dom/div #js {:className " row small-up-1 medium-up-3"}
            ;    (top-feature
            ;      nil
            ;      "fa-shopping-cart"
            ;      "Connect with the community"
            ;      "Real time interaction with the community.")
            ;    (top-feature
            ;      nil
            ;      "fa-shopping-cart"
            ;      "Find products and stories you love"
            ;      "Our partners have a wide range of home made goods")
            ;    (top-feature
            ;      nil
            ;      "fa-shopping-cart"
            ;      "Share your favorites"
            ;      "Interact with your customers as a store owner and create real long time relationships")))

            (content-section {:href  "/goods"
                              :sizes {:small 2 :medium 4}}
                             "New arrivals"
                             (map (fn [p]
                                    (common/product-element {:open-url? true} p))
                                  featured-items)
                             "See more >>")

            (content-section {:sizes {:small 1 :medium 2}
                              :class "collections"}
                             "Shop by collection"
                             (map (fn [s t]
                                    (collection-element {:url (first (:store/featured-img-src s))
                                                         :title t}))
                                  featured-stores
                                  ["Home" "Kids" "Women" "Men"])
                             ""
                             )

            (banner {:color :default}
                    (dom/h3 nil "Watch, shop and chat with your favorite Locals.")
                    (dom/p nil "Follow and stay up-to-date on when they're online to meet you!")
                    (dom/a #js {:className "button"} "Join"))

            (banner {:color :white
                     :align :right}
                    (dom/h3 nil "Open your own shop on SULO and tell your story to Vancouver.")
                    (dom/p nil "Enjoy a community that lives for local.")
                    (dom/a #js {:className "button secondary"} "Contact us"))))))))


(def ->Index (om/factory Index))

(defui ComingSoon
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}])
  Object
  (render [this]
    (let [{:keys [proxy/navbar]} (om/props this)]
      (dom/div #js {:id "sulo-coming-soon" :className "sulo-page"}

        (common/page-container
          {:navbar (om/computed navbar {:coming-soon? true})}

          (photo/header
            {:src "/assets/img/coming-soon-bg.jpg"}
            (div (->> (css/grid-row)
                      (css/add-class :align-center))
                 (div
                   (->>
                     (css/grid-column)
                     (css/grid-column-size {:small 12 :medium 10 :large 8})
                     (css/text-align :center)
                     (css/add-class :content-container))
                   (dom/h1 nil "SULO")
                   (dom/strong nil (dom/i #js {:className "fa fa-map-marker fa-fw"}) "Vancouver's local marketplace online")
                   (dom/hr nil)
                   (dom/h2 nil "Join a community that lives for local!")
                   (dom/p nil "Enter your email and we’ll put you on our invite list for an exclusive beta.")
                   (dom/form
                     #js {:className "row collapse align-center"}
                     (div (->> (css/grid-column)
                               (css/grid-column-size {:small 12 :medium 8 :large 8}))
                          (dom/input #js {:type "email" :placeholder "you@email.com"}))
                     (div (->> (css/grid-column)
                               (css/add-class :shrink))
                          (dom/button #js {:className "button expanded" :type "submit"} "Invite Me!")))))

            (div (->> (css/grid-row)
                      css/grid-column
                      (css/add-class :bottom-container)
                      (css/text-align :center))
                 (dom/strong nil "Coming Soon, Spring '17"))

            (div (->> (css/grid-row)
                      css/grid-column
                      (css/text-align :center))
                 (dom/a #js {:href "/enter"} (dom/strong nil "ENTER >>")))))))))

(def ->ComingSoon (om/factory ComingSoon))