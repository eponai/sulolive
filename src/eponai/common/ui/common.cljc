(ns eponai.common.ui.common
  (:require
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
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

(defn viewer-element [view-count]
  (dom/div #js {:className "viewers-container"}
    (dom/i #js {:className "fa fa-eye fa-fw"})
    (dom/small nil (str view-count))))

(defn link-to-store [store]
  (str "/store/" (:db/id store)))

(defn online-channel-element [channel]
  (let [{:stream/keys [store viewer-count img-src]
         stream-name :stream/name} channel
        store-link (link-to-store store)]
    (dom/div #js {:className "column content-item online-channel"}
      (dom/a #js {:href store-link}
             (photo/square
               {:src img-src}))
      (dom/div #js {:className "content-item-title-section"}
        (dom/a #js {:href store-link} (dom/strong nil stream-name))
        (viewer-element viewer-count))
      (dom/div #js {:className "content-item-subtitle-section"}
        (dom/a #js {:href store-link} (:store/name store))))))

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

(defn product-element [product & [opts]]
  (let [{:keys [on-click open-url?]} opts
        goods-href (when (or open-url? (nil? on-click)) (str "/goods/" (:db/id product)))
        on-click (when-not open-url? on-click)]
    (dom/div #js {:className "column content-item product-item"}
      (dom/a #js {:onClick   on-click
                  :href      goods-href}
             (photo/square
               {:src (:item/img-src product)}))
      (dom/div #js {:className "content-item-title-section"}
        (dom/a #js {:onClick on-click
                    :href    goods-href}
               (:item/name product)))
      (dom/div #js {:className "content-item-subtitle-section"}
        (dom/strong nil (ui-utils/two-decimal-price (:item/price product)))
        (rating-element 4 11)))))

(defn footer [opts]
  (dom/div #js {:className "footer"}
    (dom/footer #js {:className "clearfix"}
                (menu/horizontal
                  {:classes [::css/float-left]}
                  (menu/item-text nil (dom/small nil "Say hi anytime"))
                  (menu/item-link nil (dom/i #js {:className "fa fa-instagram fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-twitter fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-facebook fa-fw"}))
                  (menu/item-link nil (dom/i #js {:className "fa fa-envelope-o fa-fw"})))
                (menu/horizontal
                  {:classes [::css/float-right]}
                  (menu/item-link nil (dom/small nil "Privacy Policy"))
                  (menu/item-link nil (dom/small nil "Terms & Conditions"))
                  (menu/item-text nil (dom/small nil "© Sulo 2016"))))))

(defn page-container [props content]
  (dom/div #js {:className "page-container"}
    (nav/navbar (:navbar props))
    (dom/div #js {:className "page-content"}
      content)
    (footer nil)))