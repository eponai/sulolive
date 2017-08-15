(ns eponai.web.ui.not-found
  (:require
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.client.routes :as routes]
    [eponai.web.ui.content-item :as ci]))

(defui NotFound
  static om/IQuery
  (query [_]
    [{:query/featured-items (om/get-query ci/ProductItem)}
     :query/current-route])
  Object
  (render [this]
    (let [{:query/keys [featured-items current-route]} (om/props this)]
      (dom/div
        {:id "sulo-not-found"}
        (grid/row-column
          (css/text-align :center)
          (dom/div (css/add-classes [:not-found-code])
                   (dom/p (css/add-class :stat) "404"))
          (dom/h1 nil "Page not found")
          (dom/div (css/add-class :empty-container)
                   (dom/p (css/add-class :shoutout) "Oops, seems we're a little lost. This page doesn't exist."))
          (button/button
            {:href    (routes/url :browse/all-items)
             :classes [:hollow :sulo-dark]}
            (dom/span nil "Browse products")))
        (when (not-empty featured-items)
          [
           (grid/row-column
             nil
             (dom/hr nil)
             (dom/div
               (css/add-class :section-title)
               (dom/h3 nil "New arrivals")))
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

(router/register-component :not-found NotFound)
