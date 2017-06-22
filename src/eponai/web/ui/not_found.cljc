(ns eponai.web.ui.not-found
  (:require
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.client.routes :as routes]
    [eponai.common.ui.product-item :as pi]))

(defui NotFound
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}
                                             :store/locality]}]}
     :query/locations
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [featured-items locations]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-not-found"}
        (grid/row-column
          (css/text-align :center)
          (dom/div (css/add-classes [:not-found-code])
                   (dom/p (css/add-class :stat) "404"))
          (dom/h1 nil "Page not found")
          (dom/div (css/add-class :empty-container)
                   (dom/p (css/add-class :shoutout) "Oops, seems we're a little lost. This page doesn't exist."))
          (if (some? locations)
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
                   (pi/product-element {:open-url? true} p)))
               (take 6 featured-items)))])))))

(router/register-component :not-found NotFound)
