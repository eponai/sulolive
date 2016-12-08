(ns eponai.common.ui.common
  (:require
    [eponai.common.ui.navbar :as nav]
    #?(:cljs [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

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
  (let [rating (or rating 0)
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
  (let [{:keys [on-click open-url?]} opts]
    (dom/div #js {:className "column content-item product-item"}
      (dom/a #js {:className "photo-container"
                  :onClick   (when-not open-url? on-click)
                  :href      (when (or open-url? (nil? on-click)) (str "/goods/" (:item/id product)))}
             (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}}))
      (dom/div #js {:className "content-item-title-section"}
        (dom/a nil (:item/name product)))
      (dom/div #js {:className "content-item-subtitle-section"}
        (dom/strong nil (:item/price product))
        (rating-element 4 11)))))

(defn navbar [opts]
  (nav/->Navbar))