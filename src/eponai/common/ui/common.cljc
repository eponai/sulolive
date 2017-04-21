(ns eponai.common.ui.common
  (:require
    [clojure.string :as string]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.navbar :as nav]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.grid :as grid]))

(defn order-status-element [order]
  (let [status (:order/status order "")
        status-class (cond (= status :order.status/created)
                           "secondary"
                           (= status :order.status/paid)
                           "success"
                           (= status :order.status/fulfilled)
                           "green"
                           (= status :order.status/returned)
                           "green"
                           (= status :order.status/canceled)
                           "alert")]
    (dom/span #js {:className (str "label " status-class)} (name status))))

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

(defn loading-spinner [& [opts]]
  (dom/div #js {:className "sulo-spinner-overlay"}
    (dom/div #js {:className "sulo-spinner"}
      (dom/img #js {:src "/assets/img/auth0-icon.png"}))))

(defn viewer-element [opts view-count]
  (my-dom/div
    (->> opts
         (css/add-class :viewers-container))
    (dom/i #js {:className "fa fa-eye fa-fw"})
    (dom/span nil (str view-count))))

(defn link-to-store [store]
  (str "/store/" (:db/id store)))

(defn online-channel-element [channel]
  (let [{:stream/keys [store]
         stream-name :stream/title} channel
        {:store/keys [photo]} store
        store-link (link-to-store store)]
    (grid/column
      (->> (css/add-class :content-item)
           (css/add-class :stream-item))
      (my-dom/a
        {:href store-link}
        (photo/with-overlay
          nil
          (photo/square
            {:src (:photo/path photo)})
          (my-dom/div (css/add-class :video)
                      (my-dom/i {:classes ["fa fa-play fa-fw"]}))))
      (my-dom/div
        (->> (css/add-class :text)
             (css/add-class :header))
        (my-dom/a {:href store-link}
               (my-dom/span nil stream-name)))

      (my-dom/div
        (css/add-class :text)
        (my-dom/a {:href store-link}
                  (my-dom/strong nil (:store/name store))))
      (my-dom/div
        (css/add-class :text)
        (viewer-element nil "x")))))

(defn content-section [{:keys [href class sizes]} header content footer]
  (my-dom/div
    (->> {:classes [class]}
         (css/add-class :section))
    ;(div
    ;  (->> (css/grid-row) css/grid-column))
    (my-dom/div
      (->> (css/grid-row) (css/add-class :section-header) (css/add-class :small-unstack))
      (my-dom/div (->> (css/grid-column) (css/add-class :middle-border)))
      (my-dom/div (->> (css/grid-column) (css/add-class :shrink))
           (dom/h3 #js {:className "header"} header))
      (my-dom/div (->> (css/grid-column) (css/add-class :middle-border)))
      )

    content
    ;(when (not-empty footer))
    (my-dom/div
      (->> (css/grid-row) css/grid-column (css/add-class :section-footer) (css/text-align :center))
      (dom/a #js {:href href :className "button hollow"} footer))
    ))

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

(defn footer [opts]
  (dom/div #js {:key "footer" :className "footer"}
    (dom/footer #js {:className "clearfix"}
                (my-dom/div
                  (->> (css/grid-row)
                       (css/grid-row-columns {:small 2 :medium 3 :large 4}))
                  (my-dom/div
                    (->> (css/grid-column))
                    (menu/vertical {}
                                   (menu/item-text nil (dom/span nil "Discover"))
                                   (menu/item-link nil (dom/span nil "HOME"))
                                   (menu/item-link nil (dom/span nil "WOMEN"))
                                   (menu/item-link nil (dom/span nil "MEN"))
                                   (menu/item-link nil (dom/span nil "KIDS"))))

                  (my-dom/div
                    (->> (css/grid-column))
                    (menu/vertical {}
                                   (menu/item-text nil (dom/span nil "Learn More"))
                                   (menu/item-link nil (dom/span nil "About Us"))
                                   (menu/item-link nil (dom/span nil "Contact"))
                                   (menu/item-link nil (dom/span nil "Help"))
                                   (menu/item-link nil (dom/span nil "Legal"))
                                   (menu/item-link nil (dom/span nil "Shipping & Returns"))))
                  (my-dom/div
                    (->> (css/grid-column))
                    (menu/vertical {}
                                   (menu/item-text nil (dom/span nil "SULO"))
                                   (menu/item-link nil (dom/span nil "Start a Shop"))
                                   (menu/item-link nil (dom/span nil "Sign Up/Sign In"))
                                   (menu/item-link nil (dom/span nil "Press"))
                                   (menu/item-link nil (dom/span nil "Blog"))
                                   (menu/item-link nil (dom/span nil "FAQ"))))
                  (my-dom/div
                    (->> (css/grid-column)
                         (css/grid-column-size {:small 12 :medium 4})
                         (css/add-class :social))
                    (menu/vertical {}
                                   (menu/item-text nil (dom/span nil "Follow Us")))
                    (menu/horizontal
                      {:key "social"}
                      (menu/item-link nil (dom/i #js {:className "fa fa-instagram fa-fw"}))
                      (menu/item-link nil (dom/i #js {:className "fa fa-twitter fa-fw"}))
                      (menu/item-link nil (dom/i #js {:className "fa fa-facebook fa-fw"})))))
                (menu/horizontal
                  (->> {:key "legal"}
                       (css/align :right))
                  ;(menu/item-link nil (dom/small nil "Privacy Policy"))
                  ;(menu/item-link nil (dom/small nil "Terms & Conditions"))
                  (menu/item-text nil (dom/small #js {:className "copyright"} "© eponai hb 2017"))))))

(defn page-container [{:keys [navbar id class-name]} & content]
  (dom/div #js {:className (str "sulo-page " class-name) :id id}
    (dom/div #js {:className "page-container"}
      (nav/navbar navbar)
      (dom/div #js {:key "content-container" :className "page-content-container"}
        (apply dom/div #js {:className "page-content"}
               content))
      (footer nil))))