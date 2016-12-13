(ns eponai.common.ui.common
  (:require
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.navbar :as nav]
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

(defn photo [{:keys [url class]} & content]
  (apply dom/div #js {:className (str "photo " class) :style #js {:backgroundImage #?(:cljs (str "url(" url ")")
                                                                                :clj "")}}
         content))

(defn photo-header []
  (photo {:class "header" :url "/assets/img/night-market.jpg"}))

(defn photo-element [opts & content]
  (dom/div #js {:className "photo-container"}
    (apply photo opts content)))

(defn photo-cover [opts & content]
  (apply photo-element (assoc opts :class "cover")
         content))

(defn photo-collage-element [{:keys [urls]}]
  (let [[large mini-1 mini-2] urls]
    (dom/div #js {:className "photo-container collage"}
      (photo {:class "square" :url large})
      (dom/div #js {:className "mini-container"}
        (photo {:url mini-1})
        (photo {:url mini-2})))))

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
             (photo-element {:class "square" :url img-src})
             ;(dom/div #js {:className "photo-container"}
             ;  (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" img-src ")")}}))
             )
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
             (dom/div #js {:className "photo-container"}
               (dom/div #js {:className "photo square" :style #js {:backgroundImage (str "url(" (:item/img-src product) ")")}})))
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
                (dom/ul #js {:className "menu float-left"}
                        (dom/li #js {:className "menu-text"} (dom/small nil "Say hi anytime"))
                        (dom/li nil (dom/a nil (dom/i #js {:className "fa fa-instagram fa-fw"})))
                        (dom/li nil (dom/a nil (dom/i #js {:className "fa fa-twitter fa-fw"})))
                        (dom/li nil (dom/a nil (dom/i #js {:className "fa fa-facebook fa-fw"})))
                        (dom/li nil (dom/a nil (dom/i #js {:className "fa fa-envelope-o fa-fw"}))))

                (dom/ul #js {:className "menu float-right"}
                        (dom/li nil (dom/a nil (dom/small nil "Privacy Policy")))
                        (dom/li nil (dom/a nil (dom/small nil "Terms & Conditions")))
                        (dom/li #js {:className "menu-text"} (dom/small nil "© Sulo 2016"))))))

(defn page-container [props content]
  (dom/div #js {:className "page-container"}
    (nav/navbar (:navbar props))
    (dom/div #js {:className "page-content"}
      content)
    (footer nil)))