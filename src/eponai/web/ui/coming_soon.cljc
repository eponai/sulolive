(ns eponai.web.ui.coming-soon
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [eponai.web.ui.button :as button]
    [eponai.client.routes :as routes]
    #?(:cljs [eponai.web.utils :as web.utils])))

(defui ComingSoon
  static om/IQuery
  (query [_]
    [:query/current-route
     {:query/sulo-localities [:db/id :sulo-locality/path :sulo-locality/title]}])
  Object
  ;(set-locality [this location]
  ;  #?(:cljs
  ;     (do
  ;       (web.utils/set-locality location)
  ;       (om/transact! this [(list 'client/set-locality {:locality location})
  ;                           :query/locations])
  ;       (routes/set-url! this :index {:locality (:sulo-locality/path location)}))))
  (render [this]
    (let [{:query/keys [locations sulo-localities]} (om/props this)
          yvr-location (some #(when (= (:sulo-locality/path %) "yvr") %) sulo-localities)]
      (dom/div
        {:id "sulo-coming-soon"}
        (common/city-banner this locations)

        (grid/row-column
          (css/text-align :center)


          (dom/div
            (css/add-class :section)
            ;(dom/div
            ;  (css/add-class :section-title))
            (dom/h1 nil "Fall 2017")
            (dom/h2 nil "Live in Montréal, QC")

            (dom/div
              (css/add-class :graphics)
              (photo/photo {:photo-id "static/live-sulo-cat-fall"}))
            (dom/h4 nil "SULO Live will open its virtual doors in Montréal this fall")
            (dom/p nil (dom/strong nil "In the meantime, don't miss the action in Vancouver!"))

            (dom/div
              (css/add-class :section-footer)
              (button/store-navigation-default
                {:onClick #(.set-locality this yvr-location)}
                (dom/span nil "Shop LIVE in Vancouver, BC")))))

        (common/sell-on-sulo this)))))

(def ->ComingSoon (om/factory ComingSoon))
