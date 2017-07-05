(ns eponai.common.ui.product-page
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.product :as product]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.footer :as foot]
    [eponai.web.ui.store.profile.options :as options]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.client.routes :as routes]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.callout :as callout]))

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
       (if (:sulo-locality/path locations)
         (button/store-navigation-default
           {:href (routes/url :browse/all-items {:locality (:sulo-locality/path locations)})}
           (dom/span nil "Browse products"))
         (button/store-navigation-default
           {:href (routes/url :landing-page)}
           (dom/span nil "Choose location"))))]))

(defui ProductPage
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:query/item (om/get-query product/Product)}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}
                                             :store/locality]}]}
     :query/locations])
  Object
  (render [this]
    (let [{:keys [query/item proxy/navbar proxy/footer]} (om/props this)
          store (:store/_items item)]
      (common/page-container
        {:navbar navbar :footer footer :id "sulo-product-page"}
        (debug "Item: " item)
        (debug "Store: " store)
        ;(when-not (options/store-is-open? store)
        ;  (dom/div nil "Closed"))
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
             (product/->Product item)]))))))

(def ->ProductPage (om/factory ProductPage))

(router/register-component :product ProductPage)