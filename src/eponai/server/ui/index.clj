(ns eponai.server.ui.index
  (:require [om.next :as om :refer [defui]]
            [om.dom :as dom]
            [eponai.server.ui.store :as store]
            [eponai.server.ui.common :as common :refer [text-javascript]]))



(defn mocked-goods []
  (shuffle (mapcat :goods store/stores))
  ;[{:name    "Kids clothes"
  ;  :price   "$34.00"
  ;  :img-src "https://img0.etsystatic.com/112/0/10558959/il_570xN.1006376182_5fke.jpg"}
  ; {:name    "Beddings"
  ;  :price   "$52.00"
  ;  :img-src "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg"}
  ; {:name    "Accessories"
  ;  :price   "$134.00"
  ;  :img-src "https://img1.etsystatic.com/030/0/6396625/il_570xN.635631611_4c3s.jpg"}
  ; {:name    "Jewel"
  ;  :price   "$34.00"
  ;  :img-src "https://img0.etsystatic.com/057/2/5243597/il_570xN.729877080_d5f4.jpg"}]
  )

(defn mocked-channels []
  (let [stores store/stores]
    (shuffle [{:name         "Wear and tear proof your clothes"
               :store        (get stores 0)
               :viewer-count 8
               :img-src      "https://img1.etsystatic.com/122/0/10558959/isla_500x500.21872363_66njj7uo.jpg"}
              {:name         "What's up with thread count"
               :store        (get stores 1)
               :viewer-count 13
               :img-src      "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg"}
              {:name         "Old looking leather, how?"
               :store        (get stores 2)
               :viewer-count 43
               :img-src      "https://img1.etsystatic.com/121/0/6396625/isla_500x500.17289961_hkw1djlp.jpg"}
              {:name         "Talking wedding bands"
               :store        (get stores 3)
               :viewer-count 3
               :img-src      "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg"}])))

(defn mocked-stores []
  (let [featured-fn (fn [s]
                      (let [[img-1 img-2] (take 2 (shuffle (map :img-src (:goods s))))]
                        (assoc s :img-src [img-1 (:photo s) img-2])))]
    (map featured-fn (shuffle store/stores))))



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

(defn content-section [{:keys [class]} header content footer]
  (dom/div {:className "row column section text-center"}
    (dom/h3 {:className "text-center"} header)
    (apply dom/div {:className "content-items-container row small-up-2 medium-up-4"}
      content)
    (dom/a nil footer)))

(defn online-channel-element [channel]
  (dom/div {:className "column content-item online-channel"}
    (dom/a {:className "content-item-thumbnail-container"}
      (dom/div {:className "content-item-thumbnail" :style {:background-image (str "url(" (:img-src channel) ")")}}))
    (dom/div {:className "content-item-title-section"}
      (dom/a nil (dom/strong nil (:name channel)))
      (dom/div {:className "viewers-container"}
        (dom/i {:className "fa fa-eye fa-fw"})
        (dom/small nil (:viewer-count channel))))
    (dom/div {:className "content-item-subtitle-section"}
      (dom/a nil (:name (:store channel))))))

(defn store-element [store]
  (let [[large mini-1 mini-2] (:img-src store)]
    (dom/div {:className "column content-item store-item"}
      (dom/a {:href      (str "/store?id=" (:id store))
              :className "store-collage content-item-thumbnail-container"}
             (dom/div {:className "content-item-thumbnail"
                       :style     {:background-image (str "url(" large ")")}})
             (dom/div {:className "content-item-thumbnail-mini-container"}
               (dom/div {:className "content-item-thumbnail mini"
                         :style     {:background-image (str "url(" mini-1 ")")}})
               (dom/div {:className "content-item-thumbnail mini"
                         :style     {:background-image (str "url(" mini-2 ")")}})))
      (dom/div {:className "content-item-title-section"}
        (dom/a nil (:name store)))
      (dom/div {:className "content-item-subtitle-section"}
        (dom/div {:className "user-rating-container"}
          (dom/img {:className "user-rating"
                    :src       "/assets/img/rating-5.png"})
          (dom/small nil "(23)"))))))

(defui Index
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))
        (dom/body
          {:id "sulo-start"}
          (common/navbar nil)
          (dom/svg {:height 0}
                   (dom/defs nil
                     (dom/mask {:id               "header-alpha-mask"
                                :maskUnits        "objectBoundingBox"
                                :maskContentUnits "objectBoundingBox"}
                               (dom/radialGradient {:id "grad1" :gradientUnits "objectBoundingBox" :cx "50%" :cy "50%" :r "50%"}
                                                   (dom/stop {:offset "0%" :style {:stop-color "black" :stop-opacity 1}})
                                                   (dom/stop {:offset "100%" :style {:stop-color "black" :stop-opacity 0}}))))
                   (dom/ellipse {:cx 200 :cy 70 :rx 85 :ry 55 :fill "url(#grad1)"}))
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
                                  (mocked-channels))
                             "Check out more on the live market >>")

            (banner {:color :blue}
                    (dom/h2 nil "Find products you love made by passionate store owners"))

            (content-section nil
                             "Fresh from the oven goods"
                             (map (fn [p]
                                    (common/product-element p))
                                  (take 4 (mocked-goods)))
                             "Check out more goods >>")

            (banner {:color :green}
                    (dom/h2 nil "Discover new stores and become part of their community"))

            (content-section nil
                             "Have you seen these stores?"
                             (map (fn [s]
                                    (store-element s))
                                  (mocked-stores))
                             "Check out more stores >>")

            (banner {:color :default}
                    (dom/h2 nil "Follow your favorite stores and stay updated on when they go online")
                    (dom/a {:className "button"} "Sign up"))

            (banner {:color :white
                     :align :right}
                    (dom/h2 nil "Start streaming on Sulo and tell your story to your customers")
                    (dom/a {:className "button secondary"} "Contact us")))

          (dom/div {:className "footer"}
            (dom/footer {:className "clearfix"}
                        (dom/ul {:className "float-left menu"}
                                (dom/li {:className "menu-text"} (dom/small nil "Say hi anytime"))
                                (dom/li nil (dom/a nil (dom/i {:className "fa fa-instagram fa-fw"})))
                                (dom/li nil (dom/a nil (dom/i {:className "fa fa-twitter fa-fw"})))
                                (dom/li nil (dom/a nil (dom/i {:className "fa fa-facebook fa-fw"})))
                                (dom/li nil (dom/a nil (dom/i {:className "fa fa-envelope-o fa-fw"}))))

                        (dom/ul {:className "float-right menu"}
                                (dom/li nil (dom/a nil (dom/small nil "Privacy Policy")))
                                (dom/li nil (dom/a nil (dom/small nil "Terms & Conditions")))
                                (dom/li {:className "menu-text"} (dom/small nil "© Sulo 2016"))))))))))