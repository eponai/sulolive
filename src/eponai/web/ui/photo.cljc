(ns eponai.web.ui.photo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.photos :as photos]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug warn]]))

(defui Photo
  Object
  (componentDidMount [this]
    (let [{:keys [photo-id transformation]} (om/props this)
          url (photos/transform photo-id (or transformation :transformation/preview))]
      #?(:cljs
         (let [image-large (js/Image.)]
           (set! (.-onload image-large) #(do
                                          (debug "IMAGE LOADED LARGE") (om/update-state! this assoc :loaded-main? true)))
           (set! (.-src image-large) url)))))

  (render [this]
    (let [{:keys [overlay style photo-id transformation classes]} (om/props this)
          {:keys [loaded-main? loaded-small?]} (om/get-state this)
          url-small (photos/transform photo-id :transformation/micro)
          url (photos/transform photo-id (or transformation :transformation/preview))]
      (if-not (string? photo-id)
        (warn "Ignoring invalid photo src type, expecting a URL string. Got src: " photo-id)
        (let []
          (dom/div
            (cond->
              {:classes (conj classes ::css/photo)}
              loaded-main?
              (assoc :style {:backgroundImage (str "url(" url ")")}))

            (when url-small
              (dom/img
                (cond->> {:src     url-small
                          :classes ["small"]
                          :onLoad  #(do
                                     (debug "Small image loaded.")
                                     (om/update-state! this assoc :loaded-small? true))}
                         loaded-small?
                         (css/add-class :loaded))))
            (dom/img
              (cond->> {:src     (when loaded-main? url)
                        :classes ["main"]
                        :onLoad  #(do (debug "Large image loaded") (om/update-state! this assoc :loaded-main? true))}
                       loaded-main?
                       (css/add-class :loaded)))
            overlay))))))

(def ->Photo (om/factory Photo))

(defn photo [props & content]
  (dom/div
    (css/add-class ::css/photo-container)
    (->Photo props)
    content))

(defn square [props & content]
  (photo (css/add-class :square props) content))

(defn circle [props & content]
  (photo (css/add-class :circle props) content))

(defn cover [props & content]
  (photo (-> (css/add-class :cover props)
             (assoc :style :style/cover)) content))

(defn overlay [opts & content]
  (dom/div
    (css/add-class ::css/overlay opts)
    (dom/div
      (css/add-class ::css/photo-overlay-content)
      content)))

(defn product-photo [product & [{:keys [index transformation]}]]
  (let [{:store.item/keys [photos]} product
        {:store.item.photo/keys [photo]} (get (into [] (sort-by :store.item.photo/index photos)) (or index 0))
        photo-id (:photo/id photo "static/storefront")]
    (square {:photo-id photo-id})))

(defn stream-photo [store]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront")]
    (debug " Stream photo: " photo)
    (square
      {:photo-id photo-id}
      (overlay
        nil
        (dom/div (css/add-class :video)
                 (dom/i {:classes ["fa fa-play fa-fw"]}))))))
