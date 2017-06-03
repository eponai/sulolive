(ns eponai.server.external.email.templates
  (:require
    [eponai.common.ui.utils :as ui-utils]
    [hiccup.page :refer [xhtml]]
    [eponai.common.format.date :as date]
    [eponai.common.routes :as routes]
    [clojure.string :as string]))

(defn- container [title content]
  (xhtml
    [:head
     [:meta {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta {:content "width=device-width, initial-scale=1.0",
             :name    "viewport"}]
     [:title title]]
    [:body
     {:style   "margin: 0; padding: 0;"
      :bgcolor "#fefefe"}
     [:table
      {:align "center",
       :style "color: #2E2A31; width: 98%;"}
      [:tbody
       [:tr
        [:td {:align "center"}
         content]]]]]))

(def host "http://localhost:3000")

(defn receipt [{:keys [charge order]}]
  (let [{:keys [source created amount]} charge
        {:keys [last4 brand]} source
        {:order/keys [store shipping]} order
        {store-name :store.profile/name} (:store/profile store)]
    (container
      (str "Your SULO Live receipt from " store-name " #" (:db/id order))
      [:table {:style "width: 100%;"}
       [:tbody
        [:tr
         [:td
          {:align "center"}
          [:table {:width 600}
           [:tbody
            [:tr
             [:td {:align "center"}
              [:img {:src   "http://res.cloudinary.com/sulolive/image/upload/static/logo.png"
                     :style "width: 50px; height: 50px;"}]]]
            [:tr
             [:td {:align "center"}
              [:p {:style "font-size: 18px; padding-bottom:1em;margin-bottom:0em;"}
               [:span "Congratulations on your order from "]
               [:a {:href (str host (routes/path :store {:store-id (:db/id store)}))} (str store-name)]
               [:span "!"]
               [:br]
               [:br]
               [:span {:style "font-size: 16px;"} "Your order number is "]
               [:a {:href (str host (routes/path :user/order-list {:order-id (:db/id order)
                                                                   :user-id  123}))} (:db/id order)]]]]
            ;[:tr
            ; [:td {:align "center"}
            ;  [:p {:style "font-size: 16px; border-bottom:1px solid #e6e6e6; padding-bottom:0.25em;margin-bottom:0;"}
            ;   [:span (ui-utils/two-decimal-price (/ amount 100))]
            ;   [:small " at "]
            ;   [:span (str store-name)]
            ;
            ;   ;[:href (routes/path :store {:store-r (:db/id store)}) store-name]
            ;   ]]]

            ;[:tr
            ; [:td {:align "center"}
            ;  [:p
            ;   [:span {:style "margin-right: 0.5em;font-size: 16px;"} (str brand)]
            ;   [:span {:style "color:#8a8a8a;font-size: 14px;"} (str last4)]]]]
            ]]]]
        [:tr                                                ;{:style "background: #e6e6e6"}
         [:td {:align "center"}
          [:table {:width 600}
           [:tbody
            [:tr
             [:td
              [:p {:style "font-size:16px;margin-top:1em;margin:0;"}
               [:span (date/date->string (* created 1000) "MMM dd YYYY")]]]
             [:td {:align "right"}
              [:p {:style "font-size:16px;margin-top:1em;margin:0;"}
               [:span {:style "margin-right:0.5em;"} (str brand)]
               [:span (str last4)]]]]
            ;[:tr
            ; [:td {:align "left" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} brand]
            ;  [:span {:style "margin-left:0.5em;font-size:14px;"} last4]]
            ; [:td {:align "right" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} (date/date->string (* created 1000) "MMM dd YYYY")]]]
            ]]
          ]]

        [:tr                                                ;{:style "background: #e6e6e6"}
         [:td {:align "center"}
          [:table {:width "100%"}
           [:tbody
            [:tr
             [:td {:style "border-top: 1px solid #e6e6e6;padding-top:1em;"}]]
            ;[:tr
            ; [:td {:align "left" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} brand]
            ;  [:span {:style "margin-left:0.5em;font-size:14px;"} last4]]
            ; [:td {:align "right" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} (date/date->string (* created 1000) "MMM dd YYYY")]]]
            ]]
          ]]

        [:tr                                                ;{:style "background: #e6e6e6"}
         [:td {:align "center"}
          [:table {:width 600}
           [:tbody
            [:tr
             [:td
              [:p {:style "font-size:18px;margin-top:1em;margin-bottom:1em;"}
               [:strong {:style "letter-spacing:1px;"} "Order details"]]]]
            ;[:tr
            ; [:td {:align "left" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} brand]
            ;  [:span {:style "margin-left:0.5em;font-size:14px;"} last4]]
            ; [:td {:align "right" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} (date/date->string (* created 1000) "MMM dd YYYY")]]]
            ]]
          ]]
        (map
          (fn [order-item]
            (if (= order-item :separator)
              [:tr
               [:td {:align "center"}
                [:table {:width 600}
                 [:tbody
                  [:tr
                   [:td {:style "border-bottom: 1px solid #e6e6e6;"}]]]]]]
              (let [store-item (get-in order-item [:order.item/parent :store.item/_skus])]
                [:tr
                 [:td {:align "center"}
                  [:table {:width 600}
                   [:tbody
                    [:tr
                     [:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;"}
                      [:span {:style "font-size:16px;"} (str (:store.item/name store-item))]]
                     [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;"}
                      [:span {:style "font-size:16px;"} (str (ui-utils/two-decimal-price (:store.item/price store-item)))]]]]]]])))
          (interleave (:order/items order) (repeat :separator)))
        [:tr
         [:td {:align "center"}
          [:table {:width 600}
           [:tbody
            [:tr
             [:td {:align "left" :style "padding-top:0.25em; padding-bottom:0.25em;"}
              [:strong {:style "font-size:16px;"} "Total"]]
             [:td {:align "right" :style "padding-top:0.25em; padding-bottom:0.25em;"}
              [:strong {:style "font-size:16px;"} (ui-utils/two-decimal-price (/ amount 100))]]]]]]]
        [:tr
         [:td {:align "center"}
          [:table {:width "100%"}
           [:tbody
            [:tr
             [:td {:style "border-bottom: 1px solid #e6e6e6;padding-top:1em;"}]]]]]]
        ;[:tr
        ; [:td {:align "center"}
        ;  [:table {:width "100%"}
        ;   [:tbody
        ;    [:tr
        ;     [:td {:style "border-bottom: 1px solid #e6e6e6;padding-top:1em;"}]]]]]]

        ;; Shipping details
        (let [{:shipping/keys [address]
               to-name        :shipping/name} shipping]
          [:tr
           [:td {:align "center"}
            [:table {:width 600}
             [:tbody
              [:tr
               [:td
                [:p {:style "font-size:18px;margin-top:1em;margin-bottom:0;"}
                 [:strong {:style "letter-spacing:1px;"} "Shipping address"]]]]
              [:tr
               [:td
                [:p {:style "font-size:16px;margin-bottom:0;margin-top:1em;"} (str to-name)]]]
              [:tr
               [:td
                [:p {:style "font-size:16px;margin-top:1em;"}
                 [:span (:shipping.address/street address)]
                 [:br]
                 [:span (string/join ", " (remove nil? [(:shipping.address/city address)
                                                        (:shipping.address/postal address)
                                                        (:shipping.address/region address)]))]
                 [:br]
                 [:span (str (:shipping.address/country address))]]]]]]]])
        ;[:tr
        ; [:td
        ;  {:align "center"}
        ;  [:p
        ;   [:a
        ;    {:href ""                                            ;link
        ;     :style
        ;           "text-decoration:none;display:inline-block; border-radius:3px; padding:16px 20px;font-size:16px;border:1px solid transparent;background-color:#044e8a;color:#ffffff;font-weight:bold;"}
        ;    "View order"]]]]
        ;[:tr
        ; [:td
        ;  {:align "center"}
        ;  ;[:p
        ;  ; link-message
        ;  ; [:br]
        ;  ; [:a {:href link} link]]
        ;  ]]
        [:tr                                                ;{:style "background: #e6e6e6"}
         [:td {:align "center"}
          [:table {:width "100%"}
           [:tbody
            [:tr
             [:td {:style "border-top: 1px solid #e6e6e6;padding-top:1em;"}]]
            ;[:tr
            ; [:td {:align "left" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} brand]
            ;  [:span {:style "margin-left:0.5em;font-size:14px;"} last4]]
            ; [:td {:align "right" :style "padding-top:1em;padding-bottom:1em;"}
            ;  [:span {:style "font-size:14px;"} (date/date->string (* created 1000) "MMM dd YYYY")]]]
            ]]
          ]]
        [:tr
         [:td
          {:align "center"}
          [:table {:width 600}
           [:tbody
            [:tr
             [:td {:align "center"}
              [:p {:style "color:#8a8a8a;padding:1em;"}
               [:span "You are receiving this email because you made a purchase at "]
               [:a {:href "https://sulo.live"} "SULO Live"]
               [:span "."]
               [:br] [:br]
               [:span ". If you were not involved in this transaction, please contact us at "
                [:a {:href "mailto:hello@sulo.live"} "hello@sulo.live"] [:span "."]]]]]
            [:tr
             [:td {:align "center"}
              [:p {:style "color:#8a8a8a;padding:1em;"}
               "This email was sent by SULO Live"
               [:br]
               "eponai hb"
               [:br]
               "Stockholm, Sweden"]]]
            [:tr
             [:td {:align "center"}
              [:p {:style "color:#8a8a8a;margin-top:1em;"}
               [:small "© eponai hb 2017"]]]]]]]]]])))