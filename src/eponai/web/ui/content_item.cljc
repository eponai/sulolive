(ns eponai.web.ui.content-item
  (:require
    [om.next :as om :refer [defui]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.dom :as dom]
    [taoensso.timbre :refer [debug]]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.firebase :as firebase])))

(defui OnlineChannel
  static om/IQuery
  (query [_]
    [:db/id
     :stream/title
     {:stream/store [:db/id
                     {:store/locality [:sulo-locality/path]}
                     {:store/profile [:store.profile/name
                                      {:store.profile/photo [:photo/path :photo/id]}]}
                     {:store/status [:status/type]}
                     :store/username]}])
  Object
  (initLocalState [_]
    {:visitor-count 0})
  (componentWillMount [this]
    #?(:cljs
       (let [channel (om/props this)
             fb (shared/by-key this :shared/firebase)
             presence-ref (firebase/-ref fb (str "visitors/" (-> channel :stream/store :db/id)))]
         (debug "Reference for path: " (str "visitors/" (-> channel :stream/store :db/id)))
         (firebase/-once fb (fn [snapshot]
                              (debug "Got presence value: " snapshot)
                              (let [visitors (count (or (:value snapshot) []))]
                                (om/update-state! this assoc :visitor-count visitors)))
                         presence-ref)
         (om/update-state! this assoc :presence-ref presence-ref))))
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [presence-ref]} (om/get-state this)]
         (firebase/-off (shared/by-key this :shared/firebase) presence-ref))))
  (render [this]
    (let [channel (om/props this)
          {:keys [visitor-count]} (om/get-state this)
          {:stream/keys [store]
           stream-name  :stream/title} channel
          {{:store.profile/keys [photo]
            store-name          :store.profile/name} :store/profile} store
          store-link (routes/store-url store :store)]
      (debug "Stream: " channel)
      (debug "Stream store: " (:stream/state channel))
      (dom/div
        (cond->> (css/add-classes [:content-item :stream-item])
                 (pos? visitor-count)
                 (css/add-class :show-visitor-count))
        (dom/a
          {:href store-link}
          (photo/stream-photo store
                              nil
                              ;(when (pos? visitor-count))
                              (dom/div
                                (css/add-class :visitor-count)
                                (dom/i {:classes ["fa fa-user"]})
                                (dom/span nil (str visitor-count)))
                              ))
        ;(dom/div
        ;  (->> (css/add-class :text)
        ;       (css/add-class :header))
        ;  (dom/a {:href store-link}
        ;         (dom/span nil stream-name)))


        (dom/div
          (css/add-classes [:text :is-live])

          (dom/a {:href store-link}
                 (dom/strong nil store-name))
          )))))

(def ->OnlineChannel (om/factory OnlineChannel))

(defui StoreItem
  static om/IQuery
  (query [this]
    [:db/id
     {:store/profile [:store.profile/name
                      {:store.profile/photo [:photo/path
                                             :photo/id]}]}])
  Object
  (render [this]
    (let [store (om/props this)
          store-name (-> store :store/profile :store.profile/name)]
      (dom/div
        (->> (css/add-class :content-item)
             (css/add-class :store-item))
        (dom/a
          {:href (routes/store-url store :store)}
          (photo/store-photo store {:transformation :transformation/thumbnail-large}))
        (dom/div
          (->> (css/add-class :text)
               (css/add-class :header))
          (dom/a {:href (routes/store-url store :store)}
                 (dom/strong nil store-name)))))))

(def ->StoreItem (om/factory StoreItem))