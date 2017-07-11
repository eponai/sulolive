(ns eponai.common.ui.streams
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as my-dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.router :as router]
    [eponai.client.routes :as routes]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.dom :as dom]
    [eponai.web.ui.button :as button]
    [eponai.web.ui.content-item :as ci]))

(defui Streams
  static om/IQuery
  (query [_]
    [
     {:query/streams (om/get-query ci/OnlineChannel)}
     `({:query/stores ~(om/get-query ci/StoreItem)} ~{:states [:stream.state/offline :stream.state/online]})
     {:query/online-stores (om/get-query ci/StoreItem)}
     :query/locations])
  Object
  (render [this]
    (let [{:query/keys [locations streams stores online-stores]} (om/props this)
          streaming-stores (set (map #(get-in % [:stream/store :db/id]) streams))
          online-not-live (remove #(contains? streaming-stores (:db/id %)) online-stores)
          offline-stores (remove #(contains? (set (map :db/id online-not-live)) (:db/id %)) stores)]
      (debug "Live props: " (om/props this))
      (dom/div
        {:classes ["sulo-browse"]}
        (common/city-banner this locations)
        ;(grid/row
        ;  nil
        ;  (grid/column
        ;    nil
        ;    ))
        (grid/row
          (css/add-class :section)
          ;(grid/column
          ;  (->> (css/add-class :navigation)
          ;       (grid/column-size {:large 3})
          ;       (css/show-for :large))
          ;  (menu/vertical
          ;    (css/add-class :sl-navigation-parent)
          ;    (menu/item (css/add-class :is-active) (dom/a nil "Live now"))
          ;    ;(menu/item-link nil "Scheduled streams")
          ;    ;(menu/item-link nil "New arrivals")
          ;    ;(menu/item-link nil "Popular")
          ;    )
          ;  )
          (grid/column
            nil
            (my-dom/div
              (css/add-class :section-title)
              (my-dom/h2 nil "LIVE right now"))
            (if (not-empty streams)
              (my-dom/div {:classes ["sulo-items-container"]}
                          ;(my-dom/h3 (css/add-class :header) "LIVE right now")
                          (grid/row
                            (grid/columns-in-row {:small 2 :medium 3 :large 4})
                            (map (fn [s]
                                   (grid/column
                                     nil
                                     (ci/->OnlineChannel s)))
                                 streams)))
              (my-dom/div
                {:classes ["sulo-items-container empty-container"]}
                (my-dom/span (css/add-class :shoutout) "No stores are LIVE right now :'(")))

            (if (pos? (count online-not-live))
              (my-dom/div
                {:classes ["sulo-items-container section"]}
                (my-dom/div
                  (css/add-class :section-title)
                  (my-dom/h4 nil "Other cool stores currently online")
                  )
                (grid/row
                  (grid/columns-in-row {:small 2 :medium 3 :large 4})
                  (map (fn [store]
                         (grid/column
                           nil
                           (ci/->StoreItem store)))
                       online-not-live))
                (dom/div
                  (css/add-class :section-footer)
                  (button/default-hollow
                    (css/add-classes [:sulo-dark] {:href (routes/url :stores {:locality (:sulo-locality/path locations)})})
                    (dom/span nil "See all stores"))))

              (my-dom/div
                {:classes ["sulo-items-container section"]}
                (my-dom/div
                  (css/add-class :section-title)
                  (my-dom/h4 nil "Offline stores")
                  )
                (grid/row
                  (grid/columns-in-row {:small 2 :medium 3 :large 4})
                  (map (fn [store]
                         (grid/column
                           nil
                           (ci/->StoreItem store)))
                       offline-stores))
                (dom/div
                  (css/add-class :section-footer)
                  (button/default-hollow
                    (css/add-classes [:sulo-dark] {:href (routes/url :stores {:locality (:sulo-locality/path locations)})})
                    (dom/span nil "See all stores")))))))))))

(def ->Streams (om/factory Streams))

(router/register-component :live Streams)