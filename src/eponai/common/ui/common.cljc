(ns eponai.common.ui.common
  (:require
    [om.dom :as dom]))

(defn modal [opts & content]
  (let [{:keys [on-close]} opts]
    (dom/div #js {:className "reveal-overlay"
                  :id        "reveal-overlay"
                  :onClick   #(when (= "reveal-overlay" (.-id (.-target %)))
                               (on-close))}
      (apply dom/div #js {:className "reveal"}
             (dom/a #js {:className "close-button"
                         :onClick   on-close} "x")
             content))))

(defn rating-element [rating review-count]
  (let [rating (or rating 0)
        stars (cond-> (vec (repeat rating "fa fa-star fa-fw"))

                      (< 0 (- rating (int rating)))
                      (conj "fa fa-star-half-o fa-fw"))

        empty-stars (repeat (- 5 (count stars)) "fa fa-star-o fa-fw")
        all-stars (concat stars empty-stars)]
    (dom/div #js {:className "user-rating-container"}
      (apply dom/span nil
             (map (fn [cl]
                    (dom/i #js {:className cl}))
                  all-stars))
      (dom/span nil (str "(" review-count ")")))))

(defn product-element [product & [opts]]
  (let [{:keys [on-click]} opts]
    (dom/div #js {:className "column content-item product-item"}
      (dom/a #js {:className "content-item-thumbnail-container"
                  :onClick   on-click}
             (dom/div #js {:className "content-item-thumbnail" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}}))
      (dom/div #js {:className "content-item-title-section"}
        (dom/a nil (:item/name product)))
      (dom/div #js {:className "content-item-subtitle-section"}
        (dom/strong nil (:item/price product))
        (rating-element 5 11)))))