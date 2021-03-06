(ns eponai.server.external.email.templates
  (:require
    [eponai.common.ui.utils :as ui-utils]
    [hiccup.page :refer [xhtml]]
    [eponai.common.format.date :as date]
    [eponai.common.routes :as routes]
    [clojure.string :as string]
    [eponai.common.photos :as photos]
    [eponai.client.routes :as client.routes]))

(defn- container [title & content]
  (xhtml
    [:head
     [:meta {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta {:content "width=device-width, initial-scale=1.0",
             :name    "viewport"}]
     [:title title]
     [:style
      "@media screen and (max-width:600px) {table[class=\"container-table\"] {width: 100% !important;}}"]]
    [:body
     {:style   "margin: 0; padding: 0;"
      :bgcolor "#fefefe"}
     [:table
      {:align "center",
       :style "color: #2E2A31; width: 98%;"}
      [:tbody
       [:tr
        [:td {:align "center"}
         [:table {:style "width: 100%;"}
          [:tbody
           content

           [:tr
            [:td {:align "center"}
             [:p {:style "color:#8a8a8a;padding:1em;font-size:0.8em;"}
              "This email was sent by SULO Live"
              [:br]
              "eponai hb"
              [:br]
              "Stockholm, Sweden"]]]
           [:tr
            [:td {:align "center"}
             [:p {:style "color:#8a8a8a;margin-top:1em;font-size:0.8em;"}
              [:span "&copy; eponai hb 2017"]]]]]]]]]]]))

(def host "https://sulo.live")

(defn table-column [& content]
  [:tr
   [:td {:align "center"}
    [:table {:width 600 :cellpadding "0" :class "container-table"}
     [:tbody
      content]]]])

(defn sulo-logo []
  [:tr
   [:td {:align "center"}
    [:img {:src   "http://res.cloudinary.com/sulolive/image/upload/static/logo.png"
           :style "width: 50px; height: 50px;"}]]])

(defn open-store-request [{:field/keys [locality website email brand message]
                           user-id :user-id}]
  (container
    (str "SULO Live user - Request to open store")
    (table-column
      [:tr
       [:td {:align "center"}
        [:p {:style "font-size: 1.2em; padding-bottom:1em;margin-bottom:0em;"}
         (if (some? user-id)
           [:span "Existing SULO Live user has requested to open a store"]
           [:span "A visitor has requested to open a store"])]]]
      (when (some? user-id)
        [:tr
         [:td {:align "center"}
          [:p {:style "font-size: 1.2em; padding-bottom:1em;margin-bottom:0em;"}
           [:span "User id: "]
           [:strong user-id]]]])
      (table-column
        [:tr
         [:td {:align "right" :style "padding: 0.5em;"}
          [:label {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           "Brand: "]]
         [:td {:style "padding: 0.5em;"}
          [:p {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           brand]]]
        [:tr
         [:td {:align "right" :style "padding: 0.5em;"}
          [:label {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           "Website: "]]
         [:td {:style "padding: 0.5em;"}
          [:p {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           website]]]
        [:tr
         [:td {:align "right" :style "padding: 0.5em;"}
          [:label {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           "Locality: "]]
         [:td {:style "padding: 0.5em;"}
          [:p {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           locality]]]
        [:tr
         [:td {:align "right" :style "padding: 0.5em;"}
          [:label {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           "Email: "]]
         [:td {:style "padding: 0.5em;"}
          [:p {:style "font-size:1.1em;margin-top:1em;margin:0;"}
           email]]])
      [:tr
       [:td
        [:p {:style "font-size:1.1em;margin-top:1em;"}
         [:strong
          "Message:"]]]]
      [:tr
       [:td
        [:p {:style "font-size:1.1em;margin-top:1em;;"}
         message]]])))

(defn order-notification [{:keys [charge order]}]
  (let [{:keys [source created amount]} charge
        {:keys [last4 brand]} source
        {:order/keys [store shipping]} order
        {store-name :store.profile/name} (:store/profile store)]
    (container
      (str "Your received a SULO Live order from " (:shipping/name shipping))
      (table-column
        (sulo-logo)
        [:tr
         [:td {:align "center"}
          [:p {:style "font-size: 1.2em; padding-bottom:1em;margin-bottom:0em;"}
           [:span (str "Your received a SULO Live order from " (:shipping/name shipping))]
           [:span "!"]]]])

      (table-column
        [:tr
         [:td
          [:p {:style "font-size:1em;margin-top:1em;margin:0;"}
           [:span (str "Order #" (str (:db/id order)) " on " (date/date->string (* created 1000) "MMM dd YYYY"))]]]
         [:td {:align "right"}
          [:p {:style "font-size:1em;margin-top:1em;margin:0;"}
           [:span {:style "margin-right:0.5em;"} (str brand)]
           [:span (str last4)]]]])


      (table-column
        [:tr
         [:td {:style "border-top: 1px solid #e6e6e6;padding-top:1em;"}]])


      (table-column
        [:tr
         [:td
          [:p {:style "font-size:1.2em;margin-top:1em;margin-bottom:1em;"}
           [:span {:style "letter-spacing:1px;"} "Order details"]]]])
      (map
        (fn [order-item]
          (let [sku (:order.item/parent order-item)
                store-item (:store.item/_skus sku)
                photo-id (:photo/id (:store.item.photo/photo (first (:store.item/photos store-item))))
                photo (photos/transform photo-id :transformation/thumbnail)]

            (table-column
              [:tr
               [:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;" :width 50}
                [:img {:src photo :width 50 :height 50}]]
               [:td {:align "left" :style "padding:0.25em;"}
                [:p [:span {:style "font-size:1em;"} (str (:store.item/name store-item))]
                 [:br]
                 [:span {:style "color:#8a8a8a;"} (:store.item.sku/variation sku)]]]
               [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;"}
                [:span {:style "font-size:1em;"} (str (ui-utils/two-decimal-price (:store.item/price store-item)))]]])))
        (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))

      (table-column
        [:tr
         ;[:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;" :width 50}]

         ;[:td {:align "left" :style "padding:0.25em;margin-bottom:1em;"}
         ; [:strong {:style "font-size:1.1em;"} "TOTAL"]]
         [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;margin-bottom:1em;"}
          [:strong {:style "font-size:1.1em;"} (str "TOTAL: " (ui-utils/two-decimal-price (/ amount 100)))]]])

      (table-column
        [:tr
         [:td {:style "border-bottom: 1px solid #e6e6e6;padding-top:1em;margin-top:1em;"}]])
      ;; Shipping details
      (let [{:shipping/keys [address]
             to-name        :shipping/name} shipping]

        (table-column
          [:tr
           [:td
            [:p {:style "font-size:1.2em;margin-top:1em;margin-bottom:0;margin-top:1em;"}
             [:span {:style "letter-spacing:1px;"} "Shipping address"]]]]
          [:tr
           [:td
            [:p {:style "font-size:1em;margin-bottom:0;margin-top:1em;"} (str to-name)]]]
          [:tr
           [:td
            [:p {:style "font-size:1em;margin-top:1em;"}
             [:span (:shipping.address/street address)]
             [:br]
             [:span (string/join ", " (remove nil? [(:shipping.address/city address)
                                                    (:shipping.address/postal address)
                                                    (:shipping.address/region address)]))]
             [:br]
             [:span (str (-> address :shipping.address/country :country/name))]]]]))
      (table-column
        [:tr
         [:td {:align "center"}
          [:p
           [:a {:style "border-radius:2px;text-decoration: none;background:#4e9a9e; color:#fefefe; padding: 0.75em 1em;"
                :href  (str host (client.routes/store-url store :store-dashboard/order {:order-id (:db/id order)}))}
            [:strong "View order"]]]]])

      (table-column
        [:tr
         [:td {:align "center"}
          [:p {:style "color:#8a8a8a;padding:1em;border-top: 1px solid #e6e6e6;font-size:0.9em;"}]]]))))

(defn order-receipt [{:keys [charge order]}]
  (let [{:keys [source created amount]} charge
        {:keys [last4 brand]} source
        {:order/keys [store shipping]} order
        {store-name :store.profile/name} (:store/profile store)]
    (container
      (str "Your SULO Live receipt from " store-name " #" (:db/id order))
      (table-column
        (sulo-logo)
        [:tr
         [:td {:align "center"}
          [:p {:style "font-size: 1.2em; padding-bottom:1em;margin-bottom:0em;"}
           [:span "Congratulations on your order from "]
           [:a {:style "text-decoration: none;"
                :href  (str host (client.routes/store-url store :store))}
            [:strong (str store-name)]]
           [:span "!"]]]]
        [:tr
         [:td {:align "center"}
          [:p {:style "font-size: 1em; padding-bottom:1em;margin-bottom:0em;"}
           [:span "Your order number is "]
           [:a {:style "text-decoration: none;"
                :href  (str host (routes/path :user/order {:order-id (:db/id order)}))}
            [:strong (str (:db/id order))]]]]])

      (table-column
        [:tr
         [:td
          [:p {:style "font-size:1em;margin-top:1em;margin:0;"}
           [:span (date/date->string (* created 1000) "MMM dd YYYY")]]]
         [:td {:align "right"}
          [:p {:style "font-size:1em;margin-top:1em;margin:0;"}
           [:span {:style "margin-right:0.5em;"} (str brand)]
           [:span (str last4)]]]])


      (table-column
        [:tr
         [:td {:style "border-top: 1px solid #e6e6e6;padding-top:1em;"}]])


      (table-column
        [:tr
         [:td
          [:p {:style "font-size:1.2em;margin-top:1em;margin-bottom:1em;"}
           [:span {:style "letter-spacing:1px;"} "Order details"]]]])
      (map
        (fn [order-item]
          (let [sku (:order.item/parent order-item)
                store-item (:store.item/_skus sku)
                photo-id (:photo/id (:store.item.photo/photo (first (:store.item/photos store-item))))
                photo (photos/transform photo-id :transformation/thumbnail)]

            (table-column
              [:tr
               [:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;" :width 50}
                [:img {:src photo :width 50 :height 50}]]
               [:td {:align "left" :style "padding:0.25em;"}
                [:p [:span {:style "font-size:1em;"} (str (:store.item/name store-item))]
                 [:br]
                 [:span {:style "color:#8a8a8a;"} (:store.item.sku/variation sku)]]]
               [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;"}
                [:span {:style "font-size:1em;"} (str (ui-utils/two-decimal-price (:store.item/price store-item)))]]])))
        (filter #(= (:order.item/type %) :order.item.type/sku) (:order/items order)))

      (table-column
        [:tr
         ;[:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;" :width 50}]

         ;[:td {:align "left" :style "padding:0.25em;margin-bottom:1em;"}
         ; [:strong {:style "font-size:1.1em;"} "TOTAL"]]
         [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;margin-bottom:1em;"}
          [:strong {:style "font-size:1.1em;"} (str "TOTAL: " (ui-utils/two-decimal-price (/ amount 100)))]]])

      (table-column
        [:tr
         [:td {:style "border-bottom: 1px solid #e6e6e6;padding-top:1em;margin-top:1em;"}]])
      ;; Shipping details
      (let [{:shipping/keys [address]
             to-name        :shipping/name} shipping]

        (table-column
          [:tr
           [:td
            [:p {:style "font-size:1.2em;margin-top:1em;margin-bottom:0;margin-top:1em;"}
             [:span {:style "letter-spacing:1px;"} "Shipping address"]]]]
          [:tr
           [:td
            [:p {:style "font-size:1em;margin-bottom:0;margin-top:1em;"} (str to-name)]]]
          [:tr
           [:td
            [:p {:style "font-size:1em;margin-top:1em;"}
             [:span (:shipping.address/street address)]
             [:br]
             [:span (string/join ", " (remove nil? [(:shipping.address/city address)
                                                    (:shipping.address/postal address)
                                                    (:shipping.address/region address)]))]
             [:br]
             [:span (str (-> address :shipping.address/country :country/name))]]]]))

      (table-column
        [:tr
         [:td {:align "center"}
          [:p {:style "color:#8a8a8a;padding:1em;border-top: 1px solid #e6e6e6;font-size:0.9em;"}
           [:span "You are receiving this email because you made a purchase at "]
           [:a {:style "color:#8a8a8a;text-decoration: underline;"
                :href  "https://sulo.live"} "SULO Live"]
           [:span "."]
           [:br] [:br]
           [:span ". If you were not involved in this transaction, please contact us at "
            [:a {:style "color:#8a8a8a;text-decoration: underline;"
                 :href  "mailto:hello@sulo.live"} "hello@sulo.live"] [:span "."]]]]]))))