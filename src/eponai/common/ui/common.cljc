(ns eponai.common.ui.common
  (:require
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.navbar :as nav]
    [eponai.web.ui.footer :as foot]
    [taoensso.timbre :refer [debug error]]
    [om.next :as om]
    [eponai.web.ui.photo :as photo]
    [eponai.web.social :as social]
    [eponai.web.ui.button :as button]
    [eponai.common.ui.search-bar :as search-bar]
    [clojure.string :as string]))

(defn render-shipping [shipping opts]
  (let [{:shipping/keys [address]
         full-name      :shipping/name} shipping
        {:shipping.address/keys [street locality postal region country]} address]
    (dom/p (css/add-class :sl-shipping)
           (when (not-empty full-name)
             [
              (dom/span (css/add-class :sl-shipping--name) full-name)
              (dom/br nil)])
           (when (some? street)
             [
              (dom/span (css/add-class :sl-shipping--address) (str street))
              (dom/br nil)])
           (dom/span (css/add-class :sl-shipping--address) (string/join ", " (filter not-empty [(str locality " " postal) region (or (:country/name country)
                                                                     (:country/code country))]))))))

(defn order-status-element [order]
  (let [status (:order/status order)]
    (when (some? status)
      (dom/span
        (->> (css/add-class :sl-orderstatus)
             (css/add-class (str "sl-orderstatus--" (name status))))
        (cond (#{:order.status/paid :order.status/created} status)
              "New"
              (#{:order.status/fulfilled} status)
              "Shipped"
              (#{:order.status/returned} status)
              "Returned"
              (#{:order.status/canceled} status)
              "Canceled")))))

(defn follow-button [opts]
  (dom/a
    (->> (css/button opts)
         (css/add-class :disabled))
    (dom/span nil "+ Follow")))

(defn contact-button [opts]
  (dom/a (->> (css/button-hollow opts)
              (css/add-class :disabled))
         (dom/span nil "Contact")))

(defn modal [opts & content]
  (let [{:keys [classes on-close size require-close?]} opts]
    (dom/div
      (->> {:id      "reveal-overlay"
            :onClick #(when (= "reveal-overlay" (.-id (.-target %)))
                       (when (and on-close (not require-close?))
                         (on-close)))}
           (css/add-class :reveal-overlay))
      (dom/div
        (css/add-class (str "reveal " (when (some? size) (name size))) {:classes classes})
        (when on-close
          (dom/a
            (css/add-class :close-button {:onClick on-close})
            (dom/span nil "x")))
        content))))

(defn loading-spinner [opts & content]
  (dom/div
    (css/add-class :sl-spinner-overlay)
    (dom/div
      (css/add-class :sl-spinner)
      (dom/img {:src "/assets/img/auth0-icon.png"}))
    content))

(defn online-channel-element [channel]
  (let [{:stream/keys [store]
         stream-name  :stream/title} channel
        {{:store.profile/keys [photo]
          store-name :store.profile/name} :store/profile} store
        store-link (routes/url :store {:store-id (:db/id store)})]
    (dom/div
      (->> (css/add-class :content-item)
           (css/add-class :stream-item))
      (dom/a
        {:href store-link}
        (photo/stream-photo store))
      ;(dom/div
      ;  (->> (css/add-class :text)
      ;       (css/add-class :header))
      ;  (dom/a {:href store-link}
      ;         (dom/span nil stream-name)))

      (dom/div
        (css/add-class :text)
        (dom/a {:href store-link}
               (dom/strong nil store-name))))))

(defn content-section [{:keys [href target class sizes]} header content footer]
  (dom/div
    (->> {:classes [class]}
         (css/add-class :section)
         (css/text-align :center))
    (dom/div
      (->> (css/add-class :section-title)
           (css/text-align :center))
      (dom/h2 (css/add-class :header) header))

    content
    (when (not-empty footer)
      (grid/row-column
        (->> (css/add-class :section-footer)
             (css/text-align :center))
        (button/button
          (css/add-classes [:hollow :sulo-dark] (cond-> {:href href}
                                                   (some? target)
                                                   (assoc :target target))) footer)))))

(defn page-footer [{:keys [sulo-localities selected-locality]}]
  )

(defn city-banner [component locations]
  (let [{:sulo-locality/keys [title photo]} locations]
    (debug "City locations: " locations)
    (dom/div
      (css/add-class :intro-header {:id "sulo-city-banner"})
      (photo/cover
        {:photo-id (:photo/id photo)}
        (grid/row
          (css/align :bottom)
          (grid/column
            (grid/column-size {:small 12 :medium 6})
            (dom/h1
              (css/add-class :header)
              (dom/i {:className "fa fa-map-marker"})
              (dom/span nil title)))
          (grid/column
            nil
            (dom/div
              (css/add-class :input-container)
              (search-bar/->SearchBar {:ref             (str ::search-bar-ref)
                                       :placeholder     "What are you looking for?"
                                       :mixpanel-source "index"
                                       :classes         [:drop-shadow]
                                       :locations locations})
              (button/button
                (->> (button/expanded {:onClick (fn []
                                                  (let [search-bar (om/react-ref component (str ::search-bar-ref))]
                                                    (when (nil? search-bar)
                                                      (error "NO SEARCH BAR :( " component))
                                                    (search-bar/trigger-search! search-bar)))})
                     (css/add-classes [:drop-shadow]))
                (dom/span nil "Search")))))))))

(defn page-container [{:keys [navbar id class-name no-footer? footer]} & content]
  (dom/div
    (css/add-class (str "sulo-page " class-name) {:id id})
    (dom/div
      (css/add-class :page-container)
      (nav/navbar navbar)
      (dom/div
        (css/add-class :page-content-container {:key "content-container"})
        (nav/->Sidebar navbar)
        (dom/div
          (css/add-class :page-content)
          content))
      (when-not no-footer?
        (foot/->Footer footer)))))



(defn sell-on-sulo [component]
  (let [{:query/keys [locations]} (om/props component)]
    ;(content-section
    ;  {:href    (routes/url :coming-soon/sell)}
    ;  "Sell on SULO"
    ;  (grid/row-column
    ;    (css/text-align :center)
    ;    (dom/p (css/add-class :sell-on-sulo)
    ;           (dom/span nil "Are you selling products locally? Start a store to tell your story and interact LIVE with your customers. ")))
    ;
    ;  "Contact us")
    (content-section
      {:href  (routes/url :sell)
       :class "sell-on-sulo"}
      "Sell on SULO"
      (grid/row
        (css/align :center)
        (grid/column
          (grid/column-size {:small 12 :medium 8 :large 8})
          (dom/p nil
                 (if locations
                   (dom/span nil (str "Are you a local business in " (:sulo-locality/title locations) "? Start your own SULO store and explore new ways of connecting LIVE with your customers."))
                   (dom/span nil (str "Are you a local business? Start your own SULO store and explore new ways of connecting LIVE with your customers."))))))
      "Contact us")))


(defn is-new-order? [component]
  (let [{:query/keys [current-route]} (om/props component)]
    (nil? (get-in current-route [:route-params :order-id]))))

(defn is-order-not-found? [component]
  (let [{:query/keys [current-route order]} (om/props component)]
    (and (some? (get-in current-route [:route-params :order-id]))
         (nil? order))))

(defn order-not-found [component return-href]
  (let [{:query/keys [current-route]} (om/props component)
        {:keys [order-id store-id]} (:route-params current-route)]
    (grid/row-column
      nil
      ;(dom/h1 nil "No such order")
      (callout/callout
        (->> (css/text-align :center)
             (css/add-class :not-found))
        (dom/h1 nil "Not found")
        (dom/p nil (dom/i {:classes ["fa fa-times-circle fa-2x"]}))
        (dom/p nil
               (dom/span nil "Order with number ")
               (dom/strong nil (str "#" order-id))
               (dom/span nil " was not found in ")
               (dom/a {:href return-href}
                      (dom/strong nil "your orders")))))))

(defn payment-logos []
  {"Visa"             "icon-cc-visa"
   "American Express" "icon-cc-amex"
   "MasterCard"       "icon-cc-mastercard"
   "Discover"         "icon-cc-discover"
   "JCB"              "icon-cc-jcb"
   "Diners Club"      "icon-cc-diners"
   "Unknown"          "icon-cc-unknown"})