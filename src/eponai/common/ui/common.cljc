(ns eponai.common.ui.common
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [om.next :as om]))

(defn order-status-element [order]
  (let [status (:order/status order)]
    (when (some? status)
      (dom/span
        (->> (css/add-class :sl-orderstatus)
             (css/add-class (str "sl-orderstatus--" (name status))))
        (name status)))))

(defn modal [opts & content]
  (let [{:keys [on-close size]} opts]
    (dom/div
      (->> {:id        "reveal-overlay"
            :onClick   #(when (= "reveal-overlay" (.-id (.-target %)))
                         (on-close))}
           (css/add-class :reveal-overlay))
      (dom/div
        (css/add-class (str "reveal " (when (some? size) (name size))))
             (dom/a
               (css/add-class :close-button {:onClick on-close})
               (dom/span nil "x"))
             content))))

(defn loading-spinner [& [opts]]
  (dom/div
    (css/add-class :sl-spinner-overlay)
    (dom/div
      (css/add-class :sl-spinner)
      (dom/img {:src "/assets/img/auth0-icon.png"}))))

(defn online-channel-element [channel]
  (let [{:stream/keys [store]
         stream-name  :stream/title} channel
        {:store/keys [photo]} store
        store-link (routes/url :store {:store-id (:db/id store)})]
    (grid/column
      (->> (css/add-class :content-item)
           (css/add-class :stream-item))
      (dom/a
        {:href store-link}
        (photo/with-overlay
          nil
          (photo/square
            {:src (:photo/path photo)})
          (dom/div (css/add-class :video)
                   (dom/i {:classes ["fa fa-play fa-fw"]}))))
      (dom/div
        (->> (css/add-class :text)
             (css/add-class :header))
        (dom/a {:href store-link}
               (dom/span nil stream-name)))

      (dom/div
        (css/add-class :text)
        (dom/a {:href store-link}
               (dom/strong nil (:store/name store)))))))

(defn content-section [{:keys [href class sizes]} header content footer]
  (dom/div
    (->> {:classes [class]}
         (css/add-class :section))
    ;(div
    ;  (->> (css/grid-row) css/grid-column))
    (grid/row
      (->> (css/add-class :section-header)
           (css/add-class :small-unstack))
      (grid/column
        (css/add-class :middle-border))
      (grid/column
        (css/add-class :shrink)
        (dom/h3 (css/add-class :header) header))
      (grid/column
        (css/add-class :middle-border))
      )

    content
    ;(when (not-empty footer))
    (grid/row-column
      (->> (css/add-class :section-footer)
           (css/text-align :center))
      (dom/a
        (css/button-hollow {:href href}) footer))
    ))

(defn footer [opts]
  (dom/div
    (css/add-class :footer {:key "footer"})
    (dom/footer
      (css/clearfix)
      (grid/row
        (grid/columns-in-row {:small 2 :medium 3 :large 4})
        (grid/column
          nil
          (menu/vertical {}
                         (menu/item-text nil (dom/span nil "Discover"))
                         (menu/item-link nil (dom/span nil "HOME"))
                         (menu/item-link nil (dom/span nil "WOMEN"))
                         (menu/item-link nil (dom/span nil "MEN"))
                         (menu/item-link nil (dom/span nil "KIDS"))))

        (grid/column
          nil
          (menu/vertical {}
                         (menu/item-text nil (dom/span nil "Learn More"))
                         (menu/item-link nil (dom/span nil "About Us"))
                         (menu/item-link nil (dom/span nil "Contact"))
                         (menu/item-link nil (dom/span nil "Help"))
                         (menu/item-link nil (dom/span nil "Legal"))
                         (menu/item-link nil (dom/span nil "Shipping & Returns"))))
        (grid/column
          nil
          (menu/vertical {}
                         (menu/item-text nil (dom/span nil "SULO"))
                         (menu/item-link nil (dom/span nil "Start a Shop"))
                         (menu/item-link nil (dom/span nil "Sign Up/Sign In"))
                         (menu/item-link nil (dom/span nil "Press"))
                         (menu/item-link nil (dom/span nil "Blog"))
                         (menu/item-link nil (dom/span nil "FAQ"))))
        (grid/column
          (->> (grid/column-size {:small 12 :medium 4})
               (css/add-class :social))
          (menu/vertical {}
                         (menu/item-text nil (dom/span nil "Follow Us")))
          (menu/horizontal
            {:key "social"}
            (menu/item-link nil (dom/i {:classes ["fa fa-instagram fa-fw"]}))
            (menu/item-link nil (dom/i {:classes ["fa fa-twitter fa-fw"]}))
            (menu/item-link nil (dom/i {:classes ["fa fa-facebook fa-fw"]})))))
      (menu/horizontal
        (->> {:key "legal"}
             (css/align :right))
        ;(menu/item-link nil (dom/small nil "Privacy Policy"))
        ;(menu/item-link nil (dom/small nil "Terms & Conditions"))
        (menu/item-text nil (dom/small {:classes ["copyright"]} "© eponai hb 2017"))))))

(defn page-container [{:keys [navbar id class-name]} & content]
  (dom/div
    (css/add-class (str "sulo-page " class-name) {:id id})
    (dom/div
      (css/add-class :page-container)
      (nav/navbar navbar)
      (dom/div
        (css/add-class :page-content-container {:key "content-container"})
        (dom/div
          (css/add-class :page-content)
          content))
      (footer nil))))

(defn wip-label [_]
  (dom/div
    (css/text-align :center)
    (dom/span (->> {:id    "wip-tooltip"
                    :title "This page is still a work in progress and might have unexpected behaviors. Thank you for understanding."}
                   (css/add-class :label)
                   (css/add-class :wip-label)
                   (css/add-class :green)) "Work in progress")))