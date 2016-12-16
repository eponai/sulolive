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

(defn content-section [{:keys [href sizes class]} header content footer]
  (div
    (->> {:classes [class]}
         css/grid-row
         css/grid-column
         (css/add-class :section))
    (div
      (->> (css/grid-row) css/grid-column (css/add-class :section-header))
      (dom/h3 nil header)
      (a {:href href}
         "See more >>"))

    (apply div
           (->> (css/grid-row)
                (css/grid-row-columns sizes))
           content)
    ;(div
    ;  (->> (css/grid-row) css/grid-column (css/add-class :section-footer))
    ;  (dom/a #js {:href href} footer))
    ))

(defn store-element [store]
  (let [store-link (common/link-to-store store)]
    (div
      (->> (css/grid-column)
           (css/add-class :content-item))

      ;; Use the whole thing as hover elem
      (my-dom/a
        {:href store-link}
        (photo/with-overlay
          nil
          (photo/collage {:srcs (:store/featured-img-src store)})
          (my-dom/div
            (->> (css/text-align :center))
            (dom/p nil (dom/span nil (:store/name store)))
            (common/rating-element (:store/rating store) (:store/review-count store))
            (my-dom/div
              (->> (css/add-class :padded)
                   (css/add-class :vertical))))))

      (div
        (css/add-class :content-item-title-section)
        (a {:href store-link}
           (:store/name store))))))

(defui Index
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:db/id
                             :item/name
                             :item/price
                             :item/id
                             :item/img-src
                             :item/store]}
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
                             (.removeEventListener body "scroll" on-scroll-fn))))
  #?(:cljs
     (componentDidMount [this]
                        (debug "Component did mount")
                       (let [body (.getElementById js/document "sulo-index")
                             navbar-brand (.getElementById js/document "navbar-brand")
                             {:keys [lock on-scroll-fn]} (om/get-state this)]
                         (debug "Scroll body: " body)
                         (set! (.-opacity (.-style navbar-brand)) 0)
                         (.addEventListener body "scroll" on-scroll-fn))))
  (render [this]
    (let [{:keys [proxy/navbar query/featured-items query/featured-stores query/featured-streams]} (om/props this)
          {:keys [input-search]} (om/get-state this)]
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
                (css/text-align :center))
              (div
                (css/text-align :left)
                (dom/h1 #js {:id "header-content"} "SU" (dom/small nil "PPORT") (dom/br nil) "LO" (dom/small nil "CAL"))
                ;(dom/h1 nil "LO" (dom/small nil "CAL"))
                (dom/h2 nil "your local market online"))
              ;(dom/div #js {:className "search-container row"}
              ;  (dom/div #js {:className "column small-12 medium-6"}
              ;    (dom/input #js {:placeholder "What are you shopping for?"
              ;                    :type        "text"
              ;                    :value       (or input-search "")
              ;                    :onChange    #(do (debug " search " (.. % -target -value)) (om/update-state! this assoc :input-search (.. % -target -value)))
              ;                    :onKeyDown   (fn [e]
              ;                                   #?(:cljs
              ;                                      (when (= 13 (.. e -keyCode))
              ;                                        (let [search-string (.. e -target -value)]
              ;                                          (set! js/window.location (str "/goods?search=" search-string))))))}))
              ;  (dom/div #js {:className "column"}
              ;    (dom/a #js {:className "button"
              ;                :onClick   (fn []
              ;                             #?(:cljs
              ;                                (set! js/window.location (str "/goods?search=" input-search))))}
              ;           "Search")))
              ))


          (content-section {:href "/streams"
                            :sizes {:small 2}
                            :class "online-channels"}
                           "Makers online right now"
                           (map (fn [c]
                                  (common/online-channel-element c))
                                featured-streams)
                           "Check out more on the live market >>")

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

          (content-section {:href "/goods"
                            :sizes {:small 2 :medium 4}}
                           "Fresh from the oven goods"
                           (map (fn [p]
                                  (common/product-element {:open-url? true} p))
                                featured-items)
                           "Check out more goods >>")

          (content-section {:sizes {:small 2 :medium 4}}
                           "Have you seen these stores?"
                           (map (fn [s]
                                  (store-element s))
                                featured-stores)
                           "Check out more stores >>")

          (banner {:color :default}
                  (dom/p nil "Follow your favorite stores and stay updated on when they go online")
                  (dom/a #js {:className "button"} "Sign up"))

          (banner {:color :white
                   :align :right}
                  (dom/p nil "Start streaming on Sulo and tell your story to your customers")
                  (dom/a #js {:className "button hollow"} "Contact us")))))))

(def ->Index (om/factory Index))