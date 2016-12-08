(ns eponai.server.ui.index
  (:require
    [eponai.common.ui.common :as cljc-common]
    [eponai.server.parser.read :as store]
    [eponai.server.ui.common :as clj-common :refer [text-javascript]]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]))

(defn top-feature [opts icon title text]
  (dom/div {:className "feature-item column"}
    (dom/div {:className "row"}
      (dom/div {:className "column align-self-middle small-2 medium-12"}
        (dom/i {:className (str "fa fa-fw " icon)}))
      (dom/div {:className "text-content columns"}
        (dom/strong {:className "feature-title"} title)
        (dom/p nil text)))))

(defn banner [{:keys [color align] :as opts} & content]
  (let [align (or align :left)
        color (or color :default)]
    (dom/div {:className (str "banner " (name color))}
      (dom/div {:className "row"}
        (apply dom/div {:className (str "column small-12 medium-8"
                                        (when (= (name align) "right")
                                          " medium-offset-4 text-right"))}
               content)))))

(defn content-section [{:keys [href]} header content footer]
  (dom/div {:className "row column section text-center"}
    (dom/h3 {:className "text-center"} header)
    (apply dom/div {:className "row small-up-2 medium-up-4"}
      content)
    (dom/a {:href href} footer)))

(defn link-to-store [store]
  (str "/store/" (:db/id store)))

(defn online-channel-element [channel]
  (let [{:stream/keys [store]} channel
        store-link (link-to-store store)]
    (dom/div {:className "column content-item online-channel"}
      (dom/a {:className "photo-container"
              :href      store-link}
             (dom/div {:className "photo square" :style {:background-image (str "url(" (:stream/img-src channel) ")")}}))
      (dom/div {:className "content-item-title-section"}
        (dom/a {:href store-link} (dom/strong nil (:stream/name channel)))
        (cljc-common/viewer-element (:stream/viewer-count channel)))
      (dom/div {:className "content-item-subtitle-section"}
        (dom/a {:href store-link} (:store/name store))))))

(defn store-element [store]
  (let [[large mini-1 mini-2] (:store/featured-img-src store)
        store-link (link-to-store store)]
    (dom/div {:className "column content-item store-item"}
      (dom/a {:href      store-link
              :className "photo-container collage"}
             (dom/div {:className "photo square"
                       :style     {:background-image (str "url(" large ")")}})
             (dom/div {:className "mini-container"}
               (dom/div {:className "photo"
                         :style     {:background-image (str "url(" mini-1 ")")}})
               (dom/div {:className "photo"
                         :style     {:background-image (str "url(" mini-2 ")")}})))
      (dom/div {:className "content-item-title-section"}
        (dom/a {:href store-link}
               (:store/name store)))
      (dom/div {:className "content-item-subtitle-section"}
        (cljc-common/rating-element (:store/rating store) (:store/review-count store))))))

(defui Index
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:item/name
                             :item/price
                             :item/id
                             :item/img-src]}
     {:query/featured-stores [:db/id :store/name :store/featured-img-src :store/rating :store/review-count]}
     {:query/featured-streams [:stream/name :stream/store :stream/viewer-count :stream/img-src]}])
  Object
  (render [this]
    (let [{:keys [release? query/featured-items query/featured-stores query/featured-streams proxy/navbar]} (om/props this)]
      (prn "Got items: " featured-items)
      (dom/html
        {:lang "en"}

        (apply dom/head nil (clj-common/head release?))
        (dom/body nil
                  (dom/div
            {:id "sulo-start"
             :className "page-container"}
                    (nav/->Navbar navbar)
                    (dom/div {:className "page-content"}

              (dom/div {:className "intro-header"}
                (dom/div {:className "row"}
                  (dom/div {:className "column small-offset-6 header-photo-container"}
                    (dom/div {:className "header-photo"})))
                (dom/div {:className "row column header-content"}
                  (dom/h1 nil "Go to the market in the comfort of your own home")
                  (dom/div {:className "search-container row"}
                    (dom/div {:className "column small-12 medium-6"}
                      (dom/input {:placeholder "What are you shopping for?"
                                  :type        "text"}))
                    (dom/div {:className "column"}
                      (dom/a {:className "button"}
                             "Search")))))

              (dom/div {:className "top-features row small-up-1 medium-up-3"}
                (top-feature
                  nil
                  "fa-shopping-cart"
                  "Connect with the community"
                  "Real time interaction with the community.")
                (top-feature
                  nil
                  "fa-shopping-cart"
                  "Find products and stories you love"
                  "Our partners have a wide range of home made goods")
                (top-feature
                  nil
                  "fa-shopping-cart"
                  "Share your favorites"
                  "Interact with your customers as a store owner and create real long time relationships"))


              (banner {:color :red}
                      (dom/h2 nil "Interact live with store owners and other buyers from the community"))

              (content-section nil
                               "Stores streaming on the online market right now"
                               (map (fn [c]
                                      (online-channel-element c))
                                    featured-streams)
                               "Check out more on the live market >>")

              (banner {:color :blue}
                      (dom/h2 nil "Find products you love made by passionate store owners"))

              (content-section {:href "/goods"}
                               "Fresh from the oven goods"
                               (map (fn [p]
                                      (cljc-common/product-element p {:open-url? true}))
                                    featured-items)
                               "Check out more goods >>")

              (banner {:color :green}
                      (dom/h2 nil "Discover new stores and become part of their community"))

              (content-section nil
                               "Have you seen these stores?"
                               (map (fn [s]
                                      (store-element s))
                                    featured-stores)
                               "Check out more stores >>")

              (banner {:color :default}
                      (dom/h2 nil "Follow your favorite stores and stay updated on when they go online")
                      (dom/a {:className "button"} "Sign up"))

              (banner {:color :white
                       :align :right}
                      (dom/h2 nil "Start streaming on Sulo and tell your story to your customers")
                      (dom/a {:className "button secondary"} "Contact us")))

                    (clj-common/footer nil))

                  (dom/script {:src  (clj-common/budget-js-path release?)
                               :type clj-common/text-javascript})

                  ;(clj-common/inline-javascript ["env.web.main.runnavbar()"])
                  )))))