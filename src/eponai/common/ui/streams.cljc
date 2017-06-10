(ns eponai.common.ui.streams
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as my-dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.router :as router]
    [eponai.client.routes :as routes]
    [eponai.web.ui.photo :as photo]))

(defui Streams
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/streams [:stream/title
                      :stream/state
                      {:stream/store [{:store/profile [:store.profile/name
                                                       {:store.profile/photo [:photo/path
                                                                              :photo/id]}]}]}]}
     {:query/stores [:db/id
                     {:stream/_store [:stream/state]}
                     {:store/profile [:store.profile/name
                                      {:store.profile/photo [:photo/path
                                                             :photo/id]}]}]}
     :query/locations])
  Object
  (render [this]
    (let [{:keys [query/streams query/stores proxy/navbar]
           :query/keys [locations]} (om/props this)]
      (debug "Live props: " (om/props this))
      (common/page-container
        {:navbar navbar :id "sulo-live" :class-name "sulo-browse"}
        (common/city-banner this locations)
        (grid/row
          nil
          (grid/column
            nil
            (my-dom/h1 nil "LIVE")))
        (grid/row
          nil
          (grid/column
            (->> (css/add-class :navigation)
                 (grid/column-size {:large 3})
                 (css/show-for :large))
            (menu/vertical
              nil
              (menu/item-link nil "Live now")
              (menu/item-link nil "Scheduled streams")
              (menu/item-link nil "New arrivals")
              (menu/item-link nil "Popular")))
          (grid/column
            nil
            (if (not-empty streams)
              (my-dom/div {:classes ["sulo-items-container"]}
                          (my-dom/p (css/add-class :header) "LIVE right now")
                          (grid/row
                            (grid/columns-in-row {:small 2 :medium 3})
                            (map (fn [s]
                                   (grid/column
                                     nil
                                     (common/online-channel-element s)))
                                 streams)))
              (my-dom/div
                {:classes ["sulo-items-container empty-container"]}
                (my-dom/span (css/add-class :shoutout) "No stores are LIVE right now :'(")))
            (my-dom/div
              {:classes ["sulo-items-container"]}
              (my-dom/p (css/add-class :header) "Other cool stores currently offline")
              (grid/row
                (grid/columns-in-row {:small 2 :medium 3})
                (map (fn [store]
                       (let [store-name (get-in store [:store/profile :store.profile/name])]
                         (grid/column
                           nil
                           (my-dom/div
                             (->> (css/add-class :content-item)
                                  (css/add-class :stream-item))
                             (my-dom/a
                               {:href (routes/url :store {:store-id (:db/id store)})}
                               (photo/store-photo store {:transformation :transformation/thumbnail-large}))
                             (my-dom/div
                               (->> (css/add-class :text)
                                    (css/add-class :header))
                               (my-dom/a {:href (routes/url :store {:store-id (:db/id store)})}
                                         (my-dom/strong nil store-name)))))))
                     stores)))))))))

(def ->Streams (om/factory Streams))

(router/register-component :live Streams)