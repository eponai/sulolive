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
    [:query/locations
     {:query/sulo-localities [:db/id
                              :sulo-locality/path
                              :sulo-locality/title
                              {:sulo-locality/photo [:photo/id]}]}
     {:query/auth [:db/id]}
     :query/current-route])
  Object
  (change-location [this location-id]
    (debug "Change location: " location-id)
    (let [{:query/keys [current-route sulo-localities]} (om/props this)
          {:keys [on-change-location]} (om/get-computed this)
          {:keys [route route-params]} current-route
          location (some #(when (= (:db/id %) (c/parse-long location-id)) %) sulo-localities)]
      #?(:cljs
         (do
           (web-utils/set-locality location)
           (om/transact! this [(list 'client/set-locality {:locality location})
                               :query/locations])))
      (when (some? on-change-location)
        (on-change-location location))
      (if (some? (:locality route-params))
        (routes/set-url! this route (assoc route-params :locality (:sulo-locality/path location)))
        (routes/set-url! this :index {:locality (:sulo-locality/path location)}))))

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
                (menu/item-text nil (dom/span nil "Change location"))
                (menu/item
                  nil
                  (dom/select
                    {:value    (or (:db/id locations) "")
                     :onChange #(.change-location this (.-value (.-target %)))}
                    (dom/option {:value    ""
                                 :disabled true}
                                "--- Select location ---")
                    (map (fn [l]
                           (dom/option
                             {:value (:db/id l)} (:sulo-locality/title l)))
                         sulo-localities))))
              (menu/vertical
                (css/add-class :location-menu)
                (menu/item-text nil (dom/span nil "Currency"))
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
                             (menu/item-text nil (dom/span nil "Learn more"))

                             (menu/item-link {:href "mailto:hello@sulo.live"} (dom/span nil "Contact"))
                             ;(menu/item-link {:href (routes/url :help)} (dom/span nil "Help"))
                             (menu/item-link {:href      "//www.iubenda.com/privacy-policy/8010910"
                                              :className "iubenda-nostyle no-brand iubenda-embed"
                                              :title     "Privacy Policy"} (dom/span nil "Privacy policy"))
                             (menu/item-link {:href (routes/url :tos)} (dom/span nil "Terms of service"))
                             ;(menu/item-link nil (dom/span nil "Shipping & Returns"))
                             ))
            (grid/column
              nil
              (menu/vertical {}
                             (menu/item-text nil (dom/span nil "SULO"))
                             ;(menu/item-link {:href (routes/url :sell)} (dom/span nil "Start a store"))
                             ;(menu/item-link nil (dom/span nil "Sign up/Sign in"))
                             ;(menu/item-link nil (dom/span nil "Press"))
                             (menu/item-link {:href (routes/url :about) ;"https://blog.sulo.live/introducing-sulo-live-b3de8206a419"
                                              } (dom/span nil "About us"))
                             (menu/item-link {:href   "https://blog.sulo.live"
                                              :target "_blank"} (dom/span nil "Blog"))
                             (menu/item-link {:href   (routes/url :help)} (dom/span nil "Help center"))
                             ))
            (grid/column
              (->> (grid/column-size {:small 12 :medium 4})
                   (css/add-class :social))
              (menu/vertical {}
                             (menu/item-text nil (dom/span nil "Follow us")))
              (menu/horizontal
                {:key "social"}
                (menu/item nil (social/sulo-social-link :social/facebook))
                (menu/item nil (social/sulo-social-link :social/instagram))

                ;(menu/item-link nil (dom/i {:classes ["fa fa-twitter fa-fw"]}))
                )))
          (menu/horizontal
            (->> {:key "legal"}
                 (css/align :right))
            ;(menu/item-link nil (dom/small nil "Privacy Policy"))
            ;(menu/item-link nil (dom/small nil "Terms & Conditions"))
            (menu/item (css/add-class :sub-item) (social/sulo-icon-attribution))
            (menu/item-text (css/add-class :sub-item) (social/sulo-copyright))))))))

(def ->Footer (om/factory Footer))
