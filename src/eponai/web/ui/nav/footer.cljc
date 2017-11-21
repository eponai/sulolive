(ns eponai.web.ui.nav.footer
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    #?(:cljs
       [eponai.web.utils :as web-utils])
    [eponai.client.routes :as routes]
    [eponai.web.social :as social]
    [taoensso.timbre :refer [debug]]
    [eponai.common :as c]
    [clojure.string :as string]))

(defui Footer
  static om/IQuery
  (query [_]
    [
     {:query/sulo-localities [:db/id
                              :sulo-locality/path
                              :sulo-locality/title
                              {:sulo-locality/photo [:photo/id]}]}
     {:query/auth [:db/id]}
     :query/current-route])
  Object
  (render [this]
    (let [{:query/keys [sulo-localities locations auth]} (om/props this)]
      (dom/div
        (css/add-class :footer {:key "footer"})
        (dom/footer
          nil
          (grid/row
            (grid/columns-in-row {:small 2 :medium 4})
            (grid/column
              nil
              (menu/vertical
                (css/add-class :location-menu)
                (menu/item-text nil (dom/h5 (css/add-class :footer-header) ))
                (menu/item
                  nil
                  (dom/select
                    {:value "CAD"}
                    (map (fn [c]
                           (dom/option
                             {:value (:currency/code c)} (:currency/code c)))
                         [{:currency/code "CAD"}])))))

            (grid/column
              nil
              (menu/vertical {}
                             (menu/item-text nil (dom/h5 (css/add-class :footer-header) "Learn more"))

                             (menu/item-link nil (dom/span nil "Contact"))
                             (menu/item-link nil (dom/span nil "Privacy policy"))
                             (menu/item-link {:href (routes/url :tos)} (dom/span nil "Terms of service"))))
            (grid/column
              nil
              (menu/vertical {}
                             (menu/item-text nil (dom/h5 (css/add-class :footer-header) "SULO"))
                             (menu/item-link {:href (routes/url :about) ;"https://blog.sulo.live/introducing-sulo-live-b3de8206a419"
                                              } (dom/span nil "About us"))
                             (menu/item-link nil (dom/span nil "Blog"))
                             (menu/item-link {:href   (routes/url :help)} (dom/span nil "Help center"))))
            (grid/column
              nil
              (menu/vertical {}
                             (menu/item-text nil (dom/h5 (css/add-class :footer-header) "Go LIVE"))
                             (menu/item-link {:href (routes/url :sell)} (dom/span nil "Open your shop")))))
          (grid/row-column
            (css/add-class :social)
            (menu/horizontal
              (css/align :center)
              (menu/item-text nil (dom/h5 (css/add-class :footer-header) "Follow us")))
            (menu/horizontal
              (->> {:key "social"}
                   (css/align :center))
              (menu/item nil (social/sulo-social-link :social/facebook))
              (menu/item nil (social/sulo-social-link :social/twitter))
              (menu/item nil (social/sulo-social-link :social/pinterest))
              (menu/item nil (social/sulo-social-link :social/instagram))))
          (menu/horizontal
            (->> {:key "legal"}
                 (css/align :right))
            (menu/item (css/add-class :sub-item) (social/sulo-icon-attribution))
            (menu/item-text (css/add-class :sub-item) (social/sulo-copyright))))))))

(def ->Footer (om/factory Footer))
