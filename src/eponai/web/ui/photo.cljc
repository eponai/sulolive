(ns eponai.web.ui.photo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.photos :as photos]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug warn]]))

#?(:cljs
   (defn is-loaded? [photo-url]
     (let [img (js/Image.)]
       (set! (.-src img) photo-url)
       (.-complete img))))

(defui Photo
  Object
  (large-image-url [this]
    (let [{:keys [photo-id transformation ext]} (om/props this)]
      (photos/transform photo-id (or transformation :transformation/preview) ext)))
  (initLocalState [this]
    {:loaded-main? #?(:clj  false
                      :cljs (when (string? (:photo-id (om/props this)))
                              (is-loaded? (.large-image-url this))))})
  (componentDidMount [this]
    (let [{:keys [photo-id]} (om/props this)]
      #?(:cljs
         (when (some? photo-id)
           (let [image-large (js/Image.)
                 url (.large-image-url this)]
             (set! (.-onload image-large) #(do
                                             (om/update-state! this assoc :loaded-main? true)))
             (set! (.-src image-large) url))))))

  (render [this]
    (let [{:keys [content src photo-id classes ext background?]} (om/props this)]
      (cond (some? photo-id)
            (let [{:keys [loaded-main?]} (om/get-state this)
                  url-small (photos/transform photo-id :transformation/micro ext)
                  url (.large-image-url this)]
              (if-not (string? photo-id)
                (warn "Ignoring invalid photo src type, expecting a URL string. Got src: " photo-id)
                (dom/div
                  {:classes (conj classes ::css/photo)
                   :style   (when background? {:backgroundImage (str "url(" url-small ")")})}
                  (if background?
                    [(dom/div
                       (cond-> (css/add-class :background {:style {:backgroundImage (str "url(" url ")")}})
                               ;loaded-main?
                               ;(assoc :style {:backgroundImage (str "url(" url ")")})
                               loaded-main?
                               (update :classes conj :loaded)))
                     (dom/div (css/add-class :content)
                              content)]
                    [(when url-small
                       (dom/img
                         {:src     url-small
                          :classes ["small"]}))
                     (dom/img
                       (cond->> {:src     (when loaded-main? url)
                                 :classes ["main"]
                                 :onLoad  #(om/update-state! this assoc :loaded-main? true)}
                                loaded-main?
                                (css/add-class :loaded)))]))))

            (some? src)
            (dom/div
              {:classes (conj classes ::css/photo)
               :style   {:backgroundImage (str "url(" src ")")}}
              (dom/img
                {:src     src
                 :classes ["main loaded"]})
              (dom/div (css/add-class :content)
                       content))

            :else
            (warn "Photo component got no data source, expecing a photo key or a src.")))))

(def ->Photo (om/factory Photo))

(defn photo [props & content]
  (dom/div
    (css/add-class ::css/photo-container)
    (->Photo props)
    content))

(defn square [props & content]
  (photo (css/add-class :square props) content))

(defn circle [props & content]
  (dom/div
    (->> (css/add-class ::css/photo-container)
         (css/add-class :circle))
    (->Photo (css/add-class :circle props))
    content))

(defn cover [{:keys [placeholder? photo-id] :as props} & content]
  (let [photo-key (if placeholder?
                    (or photo-id "static/storefront")
                    photo-id)]
    (photo (-> (css/add-class :cover props)
               (assoc :style :style/cover)
               (assoc :background? true)
               (assoc :photo-id photo-key))
           content)))

(defn header [props & content]
  (->Photo
    (-> (css/add-class ::css/photo-header props)
        (assoc :content content)
        (assoc :background? true))))

(defn overlay [opts & content]
  (dom/div
    (css/add-class ::css/overlay opts)
    (dom/div
      (css/add-class ::css/photo-overlay-content)
      content)))

(defn product-photo [product & [{:keys [index transformation classes]} & content]]
  (let [{:store.item/keys [photos]} product
        {item-photo :store.item.photo/photo} (get (into [] (sort-by :store.item.photo/index photos)) (or index 0))
        photo-id (:photo/id item-photo "static/storefront")]
    (photo {:photo-id       photo-id
            :transformation transformation
            :classes        classes}
           content)))

(defn product-preview [product & [opts & content]]
  (product-photo product (css/add-class :square opts) content))

(defn product-thumbnail [product & [opts]]
  (product-preview product (css/add-class :thumbnail opts)))

(defn store-photo [store {:keys [transformation]} & content]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront")]
    (circle (->> {:photo-id       photo-id
                  :transformation transformation}
                 (css/add-class :store-photo))
            content)))

(defn user-photo [user {:keys [transformation]} & content]
  (let [photo (get-in user [:user/profile :user.profile/photo])
        p (if (:photo/id photo)
            {:photo-id (:photo/id photo)}
            {:photo-id "static/cat-profile"
             :ext      "png"})]
    (dom/div
      (css/add-class :user-profile-photo)
      (circle
        (merge p {:transformation transformation})
        content))))

(defn stream-photo [store]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront")]
    (square
      {:photo-id photo-id}
      (overlay
        nil
        (dom/div (css/add-class :video)
                 (dom/i {:classes ["fa fa-play fa-fw"]}))))))
