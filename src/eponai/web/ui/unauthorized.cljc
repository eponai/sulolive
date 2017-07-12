(ns eponai.web.ui.unauthorized
  (:require
    [eponai.common.ui.router :as router]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.client.routes :as routes]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.content-item :as ci]))

(defui Unauthorized
  static om/IQuery
  (query [this]
    [{:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}
                                             :store/locality]}]}
     :query/locations
     :query/current-route
     :query/messages])
  Object
  (render [this]
    (let [{:query/keys [locations featured-items current-route]} (om/props this)]

      (dom/div
        { :id "sulo-unauthorized"}
        (grid/row-column
          (css/text-align :center)
          (dom/h1 nil "Unauthorized")
          (dom/div (css/add-class :empty-container)
            (dom/p (css/add-class :shoutout)
                   (dom/span nil "Oops, seems we're a little lost. You don't have access to this page.")))
          (if (some? (:sulo-locality/path locations))
            (button/button
              {:href    (routes/url :browse/all-items {:locality (:sulo-locality/path locations)})
               :classes [:hollow :sulo-dark]}
              (dom/span nil "Browse products"))
            (button/button
              {:href    (routes/url :landing-page)
               :classes [:hollow :sulo-dark]}
              (dom/span nil "Select location"))))
        (when (not-empty featured-items)
          [
           (grid/row-column
             nil
             (dom/hr nil)
             (dom/div
               (css/add-class :section-title)
               (dom/h3 nil (str "New arrivals in " (:sulo-locality/title locations)))))
           (grid/row
             (->>
               (grid/columns-in-row {:small 2 :medium 3 :large 6}))
             (map
               (fn [p]
                 (grid/column
                   (css/add-class :new-arrival-item)
                   (ci/->ProductItem (om/computed p
                                                  {:current-route current-route}))))
               (take 6 featured-items)))])))))

(router/register-component :unauthorized Unauthorized)