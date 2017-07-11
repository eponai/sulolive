(ns eponai.common.ui.product-item
  (:require
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]
    [cemerick.url :as url]
    #?(:cljs
       [eponai.web.firebase :as firebase])
    [eponai.common.shared :as shared]))

(defn show-status [current-route]
  (not (contains? #{:store} (:route current-route))))

(defn product-element [opts product & children]
  (let [{:keys [on-click open-url? store-status current-route]} opts
        goods-href (when (or open-url? (nil? on-click)) (product/product-url product))
        on-click (when-not open-url? on-click)
        {:store.item/keys [photos price]
         item-name        :store.item/name
         store            :store/_items} product
        {:store.item.photo/keys [photo]} (first (sort-by :store.item.photo/index photos))]
    (dom/div
      (cond->> (css/add-classes [:content-item :product-item])
               (= store-status :online)
               (css/add-class :is-online)
               (= store-status :live)
               (css/add-class :is-live))

      (dom/a
        (->> {:onClick on-click
              :href    goods-href}
             (css/add-class :primary-photo))
        (photo/product-preview product nil
                               ;(when store-online?
                               ;              (dom/p (css/add-class :online-status) "online"))
                               ))

      (dom/div
        (css/add-classes [:header :text])
        (dom/a {:onClick on-click
                :href    goods-href}
               (dom/span nil item-name)))
      (dom/a
        (css/add-classes [:text :store-name :sl-tooltip] {:href (routes/store-url store :store)})

        (dom/small
          nil
          (str "by " (:store.profile/name (:store/profile store))))
        (when (#{:live :online} store-status)
          (dom/small (css/add-class :sl-tooltip-text) (str (:store.profile/name (:store/profile store))
                                                           " is " (name store-status) " right now, say hi in their store."))))

      (dom/div
        (css/add-class :text)
        (dom/strong nil (ui-utils/two-decimal-price price)))
      children)))

(defui ProductItem
  Object
  (initLocalState [this]
    #?(:cljs {:resize-listener #(.on-window-resize this)
              :breakpoint      (utils/breakpoint js/window.innerWidth)}))
  (on-window-resize [this]
    #?(:cljs (om/update-state! this assoc :breakpoint (utils/breakpoint js/window.innerWidth))))
  (componentDidMount [this]
    #?(:cljs
       (let [{:keys [product]} (om/props this)
             stream-state (-> product :store/_items :stream/_store first :stream/state)]
         (debug "Product stream state: " stream-state)
         (.addEventListener js/window "resize" (:resize-listener (om/get-state this))))))
  (componentWillMount [this]
    #?(:cljs
       (let [fb (shared/by-key this :shared/firebase)
             {:keys [product]} (om/props this)
             {:keys [current-route]} (om/get-computed this)
             store-owner (-> product :store/_items :store/owners :store.owner/user)
             stream-state (-> product :store/_items :stream/_store first :stream/state)
             presence-ref (firebase/-ref fb "presence")]
         (if (and (= :stream.state/live stream-state)
                  (show-status current-route))
           (om/update-state! this assoc :store-live? true)
           (firebase/-once fb (fn [snapshot]
                                (let [is-online? (and (= true (get (:value snapshot) (str (:db/id store-owner))))
                                                      (show-status current-route))]
                                  (om/update-state! this assoc :store-online? is-online?)))
                           presence-ref)))))

  (componentWillUnmount [this]
    #?(:cljs (.removeEventListener js/window "resize" (:resize-listener (om/get-state this)))))

  (render [this]
    (let [{:keys [product]} (om/props this)
          {:keys [display-content current-route open-url?]} (om/get-computed this)
          {:keys [show-item? breakpoint store-online? store-live?]} (om/get-state this)
          on-click #(om/update-state! this assoc :show-item? true)
          #?@(:cljs [open-url? (if (some? open-url?) open-url? (utils/bp-compare :large breakpoint >))]
              :clj  [open-url? (if (some? open-url?) open-url? false)])]

      (product-element
        {:on-click      on-click
         :open-url?     open-url?
         :store-status  (cond store-live? :live store-online? :online :else :offline)
         :current-route current-route}
        product
        (when show-item?
          (common/modal {:on-close #(om/update-state! this assoc :show-item? false)
                         :size     :large}
                        (product/->Product product)))))))

(def ->ProductItem (om/factory ProductItem))
