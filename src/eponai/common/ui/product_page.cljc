(ns eponai.common.ui.product-page
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.product :as product]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.store.profile.options :as options]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.api.products :as products]
    [clojure.string :as string]))

(defn product-not-found [component]
  (let [{:query/keys [locations featured-items]} (om/props component)]
    [
     (grid/row-column
       (css/add-class :store-not-found (css/text-align :center))
       (dom/div (css/add-classes [:not-found-code])
                (dom/p (css/add-class :stat) "404"))
       (dom/h1 nil "Store not found")
       (dom/div (css/add-class :empty-container)
                (dom/p (css/add-class :shoutout) "Oops, that store doesn't seem to exist."))
       (button/store-navigation-default
         {:href (routes/url :browse/all-items)}
         (dom/span nil "Browse products")))]))

(defui ProductPage
  static om/IQuery
  (query [_]
    [{:query/item (product/product-query)}
     ;{:query/featured-items [:db/id
     ;                        :store.item/name
     ;                        :store.item/price
     ;                        :store.item/created-at
     ;                        {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
     ;                                             :store.item.photo/index]}
     ;                        {:store/_items [{:store/profile [:store.profile/name]}
     ;                                        :store/locality]}]}
     ])
  Object
  (render [this]
    (let [{:keys [query/item]} (om/props this)
          store (:store/_items item)
          category (:store.item/category item)
          category-path (string/split (:category/path category) #" ")]
      (dom/div
        {:id "sulo-product-page"}
        (debug "Item:  " item)
        (debug "Store: " store)
        (debug "IS gender category: " (products/gender-category? category))

        ;(when-not (options/store-is-open? store)
        ;  (dom/div nil "Closed"))
        (debug (:store.item/category item))
        (if (:store.item/not-found? item)
          (product-not-found this)
          (let [store (:store/_items item)]
            [
             (when-not (options/store-is-open? store)
               (callout/callout-small
                 (->> (css/text-align :center {:id "store-closed-banner"})
                      (css/add-classes [:alert]))
                 (dom/div (css/add-class :sl-tooltip)
                          (dom/h3
                            (css/add-class :closed)
                            (dom/strong nil "Closed - "))
                          (dom/span (css/add-class :sl-tooltip-text)
                                    "Only you can see your products. Customers who try to view your store and products will see a not found page."))
                 (dom/a {:href (routes/store-url store :store-dashboard/profile#options)}
                        (dom/span nil "Go to options"))))
             (grid/row-column
               nil
               (menu/breadcrumbs
                 nil
                 (if (products/gender-category? category)
                   (let [[top-category gender] category-path]
                     [(when gender
                        (menu/item nil (dom/a {:href (routes/url :browse/gender {:sub-category gender})}
                                              gender)))
                      (when top-category
                        (menu/item nil (dom/a {:href (routes/url :browse/gender+top {:top-category top-category
                                                                                     :sub-category gender})}
                                              top-category)))])
                   (let [[top-category sub-category] category-path]
                     [(when top-category
                        (menu/item nil (dom/a {:href (routes/url :browse/category {:top-category top-category})}
                                              (str top-category))))

                      (when (not-empty sub-category)
                        (menu/item nil (dom/a {:href (routes/url :browse/category+sub {:top-category top-category
                                                                                       :sub-category sub-category})}
                                              (str sub-category))))]))
                 (menu/item nil (dom/span nil (:store.item/name item)))))
             (product/->Product item)]))))))

(def ->ProductPage (om/factory ProductPage))

(router/register-component :product ProductPage)