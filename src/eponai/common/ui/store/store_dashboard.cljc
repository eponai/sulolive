(ns eponai.common.ui.store.store-dashboard
  (:require
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.format :as format]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]))

(defn route-store [store-id & [path]]
  (str "/store/" store-id "/dashboard" path))

(defn store-orders [component store opts]
  (my-dom/div (->> (css/grid-row)
                   css/grid-column)
              (dom/div nil "Thes are the orders")))

(defn breadcrumbs [store current & path]
  (dom/nav #js {:aria-label "You are here:"
                :rolw       "navigation"}
           (dom/ul #js {:className "breadcrumbs"}
                   (map (fn [p]
                          (dom/li nil (dom/li nil (dom/a #js {:href (route-store (:db/id store) (:path p))} (:title p)))))
                        path)
                   ;(dom/li nil (dom/a #js {:href (route-store (:db/id store))} "Dashboard"))
                   ;(dom/li nil (dom/a #js {:href (route-store (:db/id store) "/products")} "Products"))
                   (dom/li nil (dom/span nil (:title current))))))

(defn store-products [component store {:keys [product-id create?] :as rp}]
  (if-let [product-id (when (some? product-id)
                        (c/parse-long product-id))]
    (let [selected-product (some #(when (= (:db/id %) product-id) %) (:store/items store))]
      (dom/div nil
        ;(my-dom/div (->> (css/grid-row)
        ;                 (css/grid-column))
        ;            (breadcrumbs store
        ;                         {:title "Edit"}
        ;                         {:title "Dashboard"} {:title "Products" :path "/products"}))

        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/h4 nil "Edit Product"))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/label nil "Name:")
                    (my-dom/input {:type         "text"
                                   :defaultValue (:store.item/name selected-product)}))
        (my-dom/div (->> (css/grid-row))
                    (my-dom/div (css/grid-column)
                                (dom/label nil "Price:")
                                (my-dom/input {:type         "text"
                                               :defaultValue (:store.item/price selected-product)}))
                    (my-dom/div (css/grid-column)
                                (dom/label nil "On Sale")
                                (my-dom/input {:type "checkbox"}))
                    (my-dom/div (css/grid-column)
                                (dom/label nil "Sale Price:")
                                (my-dom/input {:className    "disabled"
                                               :type         "text"
                                               :disabled     true
                                               :defaultValue (:store.item/price selected-product)})))
        (my-dom/div (->> (css/grid-row)
                         (css/grid-column))
                    (dom/div nil
                      (dom/a #js {:className "button hollow"
                                  :onClick   #(om/update-state! component dissoc :selected-product)} (dom/span nil "Cancel"))
                      (dom/a #js {:className "button"} (dom/span nil "Save"))))))
    (dom/div nil
      ;(my-dom/div (->> (css/grid-row)
      ;                 (css/grid-column))
      ;            (breadcrumbs store
      ;                         {:title "Products"}
      ;                         {:title "Dashboard"}))
      (my-dom/div
        (->> (css/grid-row))
        (my-dom/div
          (->> (css/grid-column))
          (dom/a #js {:className "button"
                      :onClick   #(om/transact! component `[(stripe/create-product ~{:product  {:name "Test product"
                                                                                                :price "100"
                                                                                                :currency "CAD"}
                                                                                :store-id (:db/id store)})
                                                       :query/store])} "Add product")))

      (my-dom/div
        (->> (css/grid-row))
        (my-dom/div
          (->> (css/grid-column))
          (dom/table
            #js {:className "hover"}
            (dom/thead
              nil
              (dom/tr nil
                      (dom/th nil (dom/span nil "Delete"))
                      (dom/th nil "Product Name")
                      (dom/th nil "Price")
                      (dom/th nil "Last Updated")))
            (dom/tbody
              nil
              (map (fn [p]
                     (dom/tr nil
                             (dom/td nil
                                     (dom/input #js {:type "checkbox"})
                                     ;(dom/a #js {:onClick #(om/transact! component `[(stripe/delete-product ~{:product {:db/id (:db/id p)}})
                                     ;                                                :query/store])} (dom/i #js {:className "fa fa-trash fa-fw"}))
                                     )
                             (dom/td nil
                                     (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                            (dom/span nil (:store.item/name p))))
                             (dom/td nil
                                     (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                            (dom/span nil (:store.item/price p))))
                             (dom/td nil
                                     (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                            (dom/span nil (:store.item/price p))))))
                   (:store/items store)))))))))
(defui StoreDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name]}
                    :store/collections]}
     ;{:query/stripe [:stripe/account :stripe/products]}
     :query/route-params
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})
  (render [this]
    (let [{:keys [proxy/navbar query/store query/route-params]} (om/props this)
          {:keys [dashboard-option]} route-params
          menu-item (fn [path title active?]
                      (menu/item-link {:classes (cond-> [:button :green] (not active?) (conj :hollow))
                                       :href    (route-store (:db/id store) path)} title))
          my-store store]
      (debug "My Store: " my-store)
      ;(debug "Route params: " route-params)
      ;(debug "PRoducts: " stripe)
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (my-dom/div
            (->> (css/grid-row))

            (my-dom/div
              (->> (css/grid-column))
              (menu/horizontal
                (css/add-class :store-nav)
                ;(menu-item "/products" "Products" (= dashboard-option "products"))
                ;(menu-item "/orders" "Orders" (= dashboard-option "orders"))
                (menu/item-tab {:active? (= dashboard-option "products")
                                 :href (route-store (:db/id my-store) "/products")} "Products")
                (menu/item-tab {:active? (= dashboard-option "orders")
                                 :href (route-store (:db/id my-store) "/orders")} "Orders")
                ))
            )
          (cond (= dashboard-option "products")
                (store-products this my-store route-params)
                (= dashboard-option "orders")
                (store-orders this my-store route-params)))))))

(def ->StoreDashboard (om/factory StoreDashboard))
