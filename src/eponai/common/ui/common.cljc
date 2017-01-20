(ns eponai.common.ui.common
  (:require
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.menu :as menu]))

(defn modal [opts & content]
  (let [{:keys [on-close size]} opts]
    (dom/div #js {:className "reveal-overlay"
                  :id        "reveal-overlay"
                  :onClick   #(when (= "reveal-overlay" (.-id (.-target %)))
                               (on-close))}
      (apply dom/div #js {:className (str "reveal " (when (some? size) (name size)))}
             (dom/a #js {:className "close-button"
                         :onClick   on-close} "x")
             content))))

(defn viewer-element [opts view-count]
  (my-dom/div
    (->> opts
         (css/add-class :viewers-container))
    (dom/i #js {:className "fa fa-eye fa-fw"})
    (dom/span nil (str view-count))))

(defn link-to-store [store]
  (str "/store/" (:db/id store)))

(defn online-channel-element [channel]
  (let [{:stream/keys [store viewer-count img-src]
         stream-name :stream/name} channel
        store-link (link-to-store store)]
    (dom/div #js {:className "column content-item online-channel"}
      (my-dom/a {:href store-link}
                (photo/with-overlay
                  nil
                  (photo/square
                    {:src img-src})
                  (my-dom/div nil (dom/p nil (dom/strong nil (:store/name store))))))
      (my-dom/div
        (->>
             ;(css/add-class :content-item-title-section)
             (css/text-align :center))
        (dom/a #js {:href store-link}
               (dom/strong nil stream-name))
        (viewer-element nil viewer-count))
      ;(dom/div #js {:className "content-item-subtitle-section"}
      ;  (dom/a #js {:href store-link} (:store/name store)))
      )))

(defn rating-element [rating & [review-count]]
  (let [rating (if (some? rating) (int rating) 0)
        stars (cond-> (vec (repeat rating "fa fa-star fa-fw"))

                      (< 0 (- rating (int rating)))
                      (conj "fa fa-star-half-o fa-fw"))

        empty-stars (repeat (- 5 (count stars)) "fa fa-star-o fa-fw")
        all-stars (concat stars empty-stars)]
    (dom/div #js {:className "star-rating-container"}
      (apply dom/span #js {:className "star-rating"}
             (map (fn [cl]
                    (dom/i #js {:className cl}))
                  all-stars))
      (when (some? review-count)
        (dom/span nil (str "(" review-count ")"))))))

(defn product-element [opts product & children]
  (let [{:keys [on-click open-url?]} opts
        goods-href (when (or open-url? (nil? on-click)) (str "/goods/" (:db/id product)))
        on-click (when-not open-url? on-click)]
    (apply dom/div #js {:className "column content-item product-item"}
           (my-dom/a
             {:onClick on-click
              :href    goods-href}
             (photo/square
               {:src (:item/img-src product)})
             ;(my-dom/div
             ;  (->> (css/text-align :center))
             ;  (dom/p nil (dom/span nil (:item/name product)))
             ;  (dom/strong nil (ui-utils/two-decimal-price (:item/price product)))
             ;  (rating-element 4 11)
             ;  (my-dom/div
             ;    (->> (css/add-class :padded)
             ;         (css/add-class :vertical)))
             ;  )
             )
           (dom/div #js {:className "content-item-title-section text-center"}
             (dom/a #js {:onClick on-click
                         :href    goods-href}
                    (:item/name product)))
           (dom/div #js {:className "content-item-subtitle-section"}
             (dom/strong nil (:store/name (:item/store product))))
           (dom/div #js {:className "content-item-subtitle-section"}
             (dom/strong nil (ui-utils/two-decimal-price (:item/price product))))
           children
           )))

(defn footer [opts]
  (dom/div #js {:key "footer" :className "footer"}
    (dom/footer #js {:className "clearfix"}
                (menu/horizontal
                  {:key "social" :classes [::css/float-left]}
                  (menu/item-link nil (dom/i #js {:className "fa fa-instagram fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-twitter fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-facebook fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-envelope-o fa-fw"})))
                (menu/horizontal
                  {:key     "legal"
                   :classes [::css/float-right]}
                  (menu/item-link nil (dom/small nil "Privacy Policy"))
                  (menu/item-link nil (dom/small nil "Terms & Conditions"))
                  (menu/item-text nil (dom/small nil "© Sulo 2016"))))))

(defn page-container [props & content]
  (dom/div #js {:className "page-container"}
    (nav/navbar (:navbar props))
    (apply dom/div #js {:key "content" :className "page-content"}
      content)
    (footer nil)))