(ns eponai.web.ui.photo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.photos :as photos]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug warn]]))

#?(:cljs
   (let [loaded-url-cache (atom nil)]
     (defn is-loaded? [photo-url]
       (let [img (js/Image.)
             _ (set! (.-src img) photo-url)
             loaded? (.-complete img)]
         (if loaded?
           (if (contains? @loaded-url-cache photo-url)
             ;; It's loaded and it's not the first time we check this.
             true
             ;; Return false the first time to let the animation happen.
             (do (swap! loaded-url-cache (fnil conj #{}) photo-url)
                 false))
           (do
             (swap! loaded-url-cache (fnil disj #{}) photo-url)
             false))))))

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
    (let [{:keys [content src photo-id classes ext]} (om/props this)]
      (cond (some? photo-id)
            (let [{:keys [loaded-main?]} (om/get-state this)
                  url-small (photos/transform photo-id :transformation/micro ext)
                  url (.large-image-url this)]
              (if-not (string? photo-id)
                (warn "Ignoring invalid photo src type, expecting a URL string. Got src: " photo-id)
                (dom/div
                  {:classes (conj classes ::css/photo)}
                  ;(if background?)
                  ;[(dom/div
                  ;   (cond-> (css/add-class :background {:style {:backgroundImage (str "url(" url ")")}})
                  ;           ;loaded-main?
                  ;           ;(assoc :style {:backgroundImage (str "url(" url ")")})
                  ;           loaded-main?
                  ;           (update :classes conj :loaded)))
                  ; (dom/div (css/add-class :content)
                  ;          content)]
                  (when url-small
                    (dom/img
                      {
                       ;:data-src url-small
                       :src      url-small
                       :classes  ["small"]}))
                  (dom/img
                    (cond->> {
                              ;:data-src (when loaded-main? url)
                              :src      url
                              :classes  ["main"]
                              :onLoad   #(om/update-state! this assoc :loaded-main? true)}
                             loaded-main?
                             (css/add-class :loaded))))))

            (some? src)
            (dom/div
              {:classes (conj classes ::css/photo)
               ;:style   {:backgroundImage (str "url(" src ")")}
               }
              (dom/img
                {
                 ;:data-src src
                 :src      src
                 :classes  ["main loaded"]})

              (when (some? content)
                (dom/div (css/add-class :content)
                         content)))

            :else
            (warn "Photo component got no data source, expecing a photo key or a src.")))))

(def ->Photo (om/factory Photo))

(defn overlay [opts & content]
  (dom/div
    (css/add-class ::css/overlay opts)
    (dom/div
      (css/add-class ::css/photo-overlay-content)
      content)))

(defn photo [{:keys [status classes] :as props} & content]
  (dom/div
    {:classes (disj (set (conj classes ::css/photo-container status)) :thumbnail)}
    ;(update props :classes into [::css/photo-container status]) ;(css/add-classes [::css/photo-container status])
    (->Photo props)
    (cond (= status :edit)
          (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))

          (= status :loading)
          (overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))
          :else
          content)))

(defn square [props & content]
  (photo (css/add-class :square props) content))

(defn circle [{:keys [status] :as props} & content]
  (dom/div
    (css/add-classes [::css/photo-container :circle status])
    (->Photo (css/add-class :circle props))
    (cond (= status :edit)
          (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))

          (= status :loading)
          (overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))
          :else
          content)))

(defn cover [{:keys [photo-id src transformation] :as props} & content]
  (let [photo-key (when-not src (or photo-id "static/storefront"))]
    (photo (-> (css/add-class :cover props)
               (assoc :style :style/cover)
               (assoc :photo-id photo-key)
               (assoc :transformation (or transformation :transformation/cover)))
           (overlay nil content))))

;(defn edit-cover [{:keys [photo-id] :as props} & content]
;  (cover (merge props {:placeholder? true
;                       :transformation :transformation/preview})
;         (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))))

(defn product-photo [product & [{:keys [index transformation classes]} & content]]
  (let [{:store.item/keys [photos]} product
        {item-photo :store.item.photo/photo} (get (into [] (sort-by :store.item.photo/index photos)) (or index 0))
        photo-id (:photo/id item-photo "static/storefront")]
    (photo {:photo-id       photo-id
            :transformation transformation
            :classes        (conj classes :product-photo)}
           content)))

(defn product-preview [product & [opts & content]]
  (product-photo product (css/add-class :square opts) content))

(defn product-thumbnail [product & [opts]]
  (product-preview product (css/add-class :thumbnail opts)))

(defn store-photo [store props & content]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront")]
    (circle (->> (assoc props :photo-id photo-id)
                 (css/add-class :store-photo))
            content)))

(defn store-cover [store props & content]
  (let [{cover-photo :store.profile/cover} (:store/profile store)]
    (cover (merge props {:photo-id       (:photo/id cover-photo)
                         :transformation :transformation/cover})
           content)))

(defn user-photo [user props & content]
  (let [photo (get-in user [:user/profile :user.profile/photo])
        p (if (:photo/id photo)
            {:photo-id (:photo/id photo)}
            {:photo-id "static/cat-profile"
             :ext      "png"})]
    (dom/div
      (css/add-class :user-profile-photo)
      (circle
        (merge props p)
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
