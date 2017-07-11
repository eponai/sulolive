(ns eponai.web.ui.stores
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [eponai.web.ui.photo :as photo]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.router :as router]
    [eponai.web.ui.content-item :as ci]))

(defui Stores

  static om/IQuery
  (query [_]
    [{:query/stores (om/get-query ci/StoreItem)}
     :query/locations])
  Object
  (render [this]
    (let [{:query/keys [locations stores]} (om/props this)]

      (dom/div
        {:classes ["sulo-browse"]}
        (common/city-banner this locations)
        ;(grid/row
        ;  nil
        ;  (grid/column
        ;    nil
        ;    ))

        (grid/row-column
          (css/add-classes [:section :text-center])
          (dom/div
            {:classes ["sulo-items-container"]}
            (dom/h2 nil (str "SULO Stores " (:sulo-locality/path locations)))
            (grid/row
              (grid/columns-in-row {:small 2 :medium 3 :large 4})
              (map (fn [store]
                     (let [store-name (get-in store [:store/profile :store.profile/name])]
                       (grid/column
                         nil
                         (ci/->StoreItem store))))
                   stores))))))))

(router/register-component :stores Stores)