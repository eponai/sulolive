(ns eponai.common.ui.common
  (:require
    [eponai.common :as com]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

(defn two-decimal-price [price]
  (com/format-str "$%.2f" (double (or price 0))))

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

(defn rating-element [rating & [review-count]]
  (let [rating (or (int rating) 0)
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
        goods-href (when (or open-url? (nil? on-click)) (str "/goods/" (:item/id product)))
        on-click (when-not open-url? on-click)]
    (dom/div #js {:className "column content-item product-item"}
      (dom/a #js {:onClick   on-click
                  :href      goods-href}
             (dom/div #js {:className "photo-container"}
               (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}})))
      (dom/div #js {:className "content-item-title-section"}
        (dom/a #js {:onClick on-click
                    :href    goods-href}
               (:item/name product)))
      (dom/div #js {:className "content-item-subtitle-section"}
        (dom/strong nil (two-decimal-price (:item/price product)))
        (rating-element 4 11)))))
