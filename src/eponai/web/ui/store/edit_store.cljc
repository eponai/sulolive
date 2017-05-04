(ns eponai.web.ui.store.edit-store
  (:require
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.om-quill :as quill]
    [eponai.common.format :as f]
    [eponai.common.ui.product-item :as pi]))

(defn about-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [description name]} (:store/profile store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil (str "About " name)))
        (quill/->QuillRenderer {:html (f/bytes->str description)})))))

(defn policies-section [component]
  (let [{:query/keys [store]} (om/props component)
        {:store.profile/keys [return-policy]} (:store/profile store)]
    (grid/row-column
      nil
      (dom/div
        (css/callout)
        (dom/p nil (dom/strong nil "Returns"))
        (quill/->QuillRenderer {:html (f/bytes->str return-policy)})))))

(defui EditStore
  static om/IQuery
  (query [_]
    [:query/current-route])
  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {{:store.profile/keys [cover tagline]
            store-name :store.profile/name} :store/profile
           store-items :store/items} store
          {:query/keys [current-route]} (om/props this)
          {:keys [route route-params]} current-route]
      (debug "Edit store: " store)
      (dom/div
        nil
        (grid/row
          nil
          (grid/column
            nil
            (dom/h3 nil "Edit store"))
          (grid/column
            (->> (css/text-align :right)
                 (css/add-class :shrink))
            (dom/a (css/button-hollow) "Restore")
            (dom/a (css/button) "Save changes")))
        (grid/row-column
          {:id "sulo-store" :classes ["edit-store"]}
          (dom/div
            nil
            (grid/row
              (->> (grid/columns-in-row {:small 1})
                   (css/add-class :expanded)
                   (css/add-class :collapse))
              (grid/column
                (grid/column-order {:small 2 :medium 1})
                (when
                  (some? cover)
                  (photo/cover {:photo-id (:photo/id cover)}
                               (photo/overlay
                                 nil
                                 (dom/a (css/button-hollow)
                                        (dom/i {:classes ["fa fa-camera fa-fw"]})
                                        (dom/span nil "Upload cover photo"))))))



              (grid/column
                (->> (grid/column-order {:small 1 :medium 2})
                     (css/add-class :store-container))

                (grid/row
                  (->> (css/align :middle)
                       (css/align :center))

                  (grid/column
                    (grid/column-size {:small 12 :medium 2})
                    (photo/store-photo store :transformation/thumbnail))

                  (grid/column
                    (css/add-class :shrink)
                    (dom/div (css/add-class :store-name)
                             (dom/strong nil store-name)
                             (dom/a (css/button-hollow) (dom/i {:classes ["fa fa-pencil fa-fw"]})))
                    (dom/p nil
                           ;(dom/i
                           ;     (css/add-class "fa fa-map-marker fa-fw"))
                           (dom/small nil "North Vancouver, BC")))
                  (grid/column
                    (->> (grid/column-size {:small 12 :medium 4 :large 3})
                         (css/text-align :center)
                         (css/add-class :follow-section))
                    ;(dom/div nil
                    ;         (common/follow-button nil)
                    ;         (common/contact-button nil))
                    )
                  ))
              (grid/column
                (->> (grid/column-order {:small 3 :medium 3})
                     (css/add-class :quote-section)
                     (css/text-align :center))
                (dom/span nil tagline))))

          (dom/div
            {:id "shop"}
            (grid/row
              (->> (css/add-class :collapse)
                   (css/add-class :menu-container))
              (grid/column
                nil
                (menu/horizontal
                  (css/add-class :navigation)

                  (menu/item (cond->> (css/add-class :about)
                                      (= route :store/about)
                                      (css/add-class ::css/is-active))
                             (dom/a {:href (routes/url :store/about {:store-id (:db/id store)})}
                                    (dom/span nil "About")))
                  (menu/item (cond->> (css/add-class :about)
                                      (= route :store/policies)
                                      (css/add-class ::css/is-active))
                             (dom/a {:href (routes/url :store/policies {:store-id (:db/id store)})}
                                    (dom/span nil "Policies")))
                  (menu/item (when (= :store route)
                               (css/add-class ::css/is-active))
                             (dom/a {:href (routes/url :store {:store-id (:db/id store)})}
                                    (dom/span nil "All Items")))
                  (map-indexed
                    (fn [i n]
                      (let [{:store.section/keys [path label]} n
                            is-active? (= path (:navigation route-params))]
                        (menu/item
                          (cond->> {:key (+ 10 i)}
                                   is-active?
                                   (css/add-class ::css/is-active))
                          (dom/a
                            {:href (routes/url :store/navigation {:navigation path :store-id (:db/id store)})}
                            (dom/span nil label)))))
                    (:store/sections store)))))
            (about-section this)
            ;(cond (= route :store/about)
            ;      (about-section this)
            ;      (= route :store/policies)
            ;      (policies-section this)
            ;      :else
            ;      (grid/products store-items
            ;                     (fn [p]
            ;                       (pi/->ProductItem {:product p}))))
            ))))))

(def ->EditStore (om/factory EditStore))