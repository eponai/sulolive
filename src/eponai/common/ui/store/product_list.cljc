(ns eponai.common.ui.store.product-list
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.store.common :as store-common]
    #?(:cljs
       [goog.crypt.base64 :as crypt])
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.format.date :as date]
    [eponai.common.ui.utils :as utils]
    [eponai.common.ui.elements.table :as table]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as p]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.web.ui.photo :as photo]))

(defui ProductList

  static om/IQuery
  (query [_]
    [{:query/inventory [:store.item/name
                        :store.item/description
                        {:store.item/photos [{:store.item.photo/photo [:photo/path]}
                                             :store.item.photo/index]}]}])

  static store-common/IDashboardNavbarContent
  (render-subnav [_ _]
    (dom/div nil))

  (subnav-title [_ _]
    "Products")

  Object
  (render [this]
    (let [{:keys [query/inventory]} (om/props this)
          {:keys          [search-input]
           :products/keys [selected-section edit-sections listing-layout]} (om/get-state this)
          {:keys [route-params store]} (om/get-computed this)
          products (if (not-empty search-input)
                     (filter #(clojure.string/includes? (.toLowerCase (:store.item/name %))
                                                        (.toLowerCase search-input)) inventory)
                     inventory)]
      (dom/div
        {:id "sulo-product-list"}
        (dom/h1
          (css/show-for-sr) "Products")
        ;(dom/div
        ;  (css/add-class :section-title)
        ;  (dom/h2 nil "Sections")
        ;  (store-common/edit-button
        ;    {:onClick #(om/update-state! this assoc :products/edit-sections (into [] (:store/sections store)))}
        ;    (dom/span nil "Edit sections")))
        ;(when edit-sections
        ;  (store-common/edit-sections-modal this {:edit-sections edit-sections
        ;                                          :items (:store/items store)}))
        ;(callout/callout
        ;  nil
        ;  (menu/horizontal
        ;    (css/add-class :product-section-menu)
        ;    (menu/item
        ;      (when (= selected-section :all)
        ;        (css/add-class :is-active))
        ;      (dom/a
        ;        {:onClick #(om/update-state! this assoc :products/selected-section :all)}
        ;        (dom/span nil "All items")))
        ;    (map (fn [s]
        ;           (menu/item
        ;             (when (= selected-section (:db/id s))
        ;               (css/add-class :is-active))
        ;             (dom/a {:onClick #(om/update-state! this assoc :products/selected-section (:db/id s))}
        ;                    (dom/span nil (:store.section/label s)))))
        ;         (concat (:store/sections store) (:store/sections store) (:store/sections store)))))

        (dom/div
          (css/add-class :section-title)
          (dom/h2 nil "Products")
          (dom/a (css/button {:href (routes/url :store-dashboard/create-product
                                                {:store-id (:store-id route-params)
                                                 :action   "create"})})
                 "Add product"))
        (callout/callout-small
          nil
          (grid/row
            (css/add-classes [:expanded :collapse])
            (grid/column
              nil
              (dom/input {:value       (or search-input "")
                          :onChange    #(om/update-state! this assoc :search-input (.. % -target -value))
                          :placeholder "Search Products..."
                          :type        "text"}))
            (grid/column
              (->> (css/add-class :shrink)
                   (css/text-align :right))
              (dom/a (css/button-hollow {:onClick #(om/update-state! this assoc :products/listing-layout :products/list)})
                     (dom/i {:classes ["fa fa-list"]}))
              (dom/a (css/button-hollow
                       {:onClick #(om/update-state! this assoc :products/listing-layout :products/grid)})
                     (dom/i {:classes ["fa fa-th"]})))
            )
          (if (= listing-layout :products/list)
            (table/table
              (css/add-class :hover)
              (dom/div
                (css/add-class :thead)
                (dom/div
                  (css/add-class :tr)
                  (dom/span (css/add-class :th) (dom/span nil ""))
                  (dom/span (css/add-class :th) "Product Name")
                  (dom/span
                    (->> (css/add-class :th)
                         (css/text-align :right)) "Price")
                  (dom/span
                    (->> (css/add-class :th)
                         (css/show-for :medium)) "Last Updated")))
              (dom/div
                (css/add-class :tbody)
                (map (fn [p]
                       (let [product-link (routes/url :store-dashboard/product
                                                      {:store-id   (:store-id route-params)
                                                       :product-id (:db/id p)})]
                         (dom/a
                           (css/add-class :tr {:href product-link})
                           (dom/span (css/add-class :td)
                                     (p/product-preview p {:transformation :transformation/thumbnail-tiny}))
                           (dom/span (css/add-class :td)
                                     (:store.item/name p))
                           (dom/span
                             (->> (css/add-class :td)
                                  (css/text-align :right))
                             (utils/two-decimal-price (:store.item/price p)))
                           (dom/span
                             (->> (css/add-class :td)
                                  (css/show-for :medium))
                             (when (:store.item/updated p)
                               (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))
                     products)))
            (grid/products
              products
              (fn [p]
                (let [{:store.item/keys [price]
                       item-name        :store.item/name} p]
                  (dom/div
                    (->> (css/add-class :content-item)
                         (css/add-class :product-item))
                    (dom/div
                      (->>
                        (css/add-class :primary-photo))
                      (photo/product-preview p))

                    (dom/div
                      (->> (css/add-class :header)
                           (css/add-class :text))
                      (dom/div
                        nil
                        (dom/span nil item-name)))
                    (dom/div
                      (css/add-class :text)
                      (dom/strong nil (utils/two-decimal-price price)))
                    (menu/horizontal
                      (css/add-class :edit-item-menu)
                      (menu/item nil
                                 (dom/a {:href (routes/url :store-dashboard/product
                                                           {:product-id (:db/id p)
                                                            :store-id   (:db/id store)})}
                                        (dom/i {:classes ["fa fa-pencil fa-fw"]})
                                        (dom/span nil "Go to edit"))))))))))





        ;(grid/row
        ;  (css/add-class :collapse)
        ;  (grid/column
        ;    nil
        ;    (table/table
        ;      (css/add-class :hover)
        ;      (dom/div
        ;        (css/add-class :thead)
        ;        (dom/div
        ;          (css/add-class :tr)
        ;          (dom/span (css/add-class :th) (dom/span nil ""))
        ;          (dom/span (css/add-class :th) "Product Name")
        ;          (dom/span
        ;            (->> (css/add-class :th)
        ;                 (css/text-align :right)) "Price")
        ;          (dom/span
        ;            (->> (css/add-class :th)
        ;                 (css/show-for :medium)) "Last Updated")))
        ;      (dom/div
        ;        (css/add-class :tbody)
        ;        (map (fn [p]
        ;               (let [product-link (routes/url :store-dashboard/product
        ;                                              {:store-id   (:store-id route-params)
        ;                                               :product-id (:db/id p)})]
        ;                 (dom/a
        ;                   (css/add-class :tr {:href product-link})
        ;                   (dom/span (css/add-class :td)
        ;                             (p/product-preview p {:transformation :transformation/thumbnail-tiny}))
        ;                   (dom/span (css/add-class :td)
        ;                             (:store.item/name p))
        ;                   (dom/span
        ;                     (->> (css/add-class :td)
        ;                          (css/text-align :right))
        ;                     (utils/two-decimal-price (:store.item/price p)))
        ;                   (dom/span
        ;                     (->> (css/add-class :td)
        ;                          (css/show-for :medium))
        ;                     (when (:store.item/updated p)
        ;                       (date/date->string (* 1000 (:store.item/updated p)) "MMM dd yyyy HH:mm"))))))
        ;             products)))))
        ))))

(def ->ProductList (om/factory ProductList))
