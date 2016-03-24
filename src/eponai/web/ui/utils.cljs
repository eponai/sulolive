(ns eponai.web.ui.utils
  (:require [eponai.client.ui :refer-macros [opts] :refer [map-all update-query-params!]]
            [eponai.web.ui.datepicker :refer [->Datepicker]]
            [sablono.core :refer-macros [html]]
            [om.next :as om]
            [taoensso.timbre :refer-macros [debug]]))

(defonce reconciler-atom (atom nil))

;;;;;;; UI component helpers

(defn loader []
  (html
    [:div.loader-circle-black
     (opts {:style {:top      "50%"
                    :left     "50%"
                    :position :absolute
                    :z-index  1050}})]))

(defn click-outside-target [on-click]
  (html
    [:div#click-outside-target
     (opts {:style    {:top      0
                       :bottom   0
                       :right    0
                       :left     0
                       :position :fixed}
            :on-click on-click})]))

(defn modal [{:keys [content on-close size]}]
  (let [click-outside-target-id (name :click-outside-target)]
    (html
      [:div.reveal-overlay
       (opts {:id       click-outside-target-id
              :style    {:z-index  2050
                         :display  "block"}
              :on-click #(when (= click-outside-target-id (.-id (.-target %)))
                          (on-close))})
       [:div
        (opts {:class (if size (str size " reveal") "reveal")
               :style (cond-> {:display  "block"
                               :position :relative}
                              (= size "large")
                              (assoc :margin-top 0))})
        [:a.close-button
         {:on-click on-close}
         "x"]
        content]])))

(defn tag [{tag-name :tag/name} {:keys [on-delete
                                        on-click]}]
  (html
    [:div.label.secondary.tag
     (opts {:style {:display :inline-block}
            :key [tag-name]})

     [:a.button
      (opts {:on-click (or on-click #(prn (.. % -target)))})
      tag-name]

     (when on-delete
       [:a.button
        (opts {:on-click #(on-delete)})
        "x"])]))

(defn- on-enter-down [e f]
  (when (and (= 13 (.-keyCode e))
             (seq (.. e -target -value)))
    (.preventDefault e)
    (f (.. e -target -value))))

(defn tag-input [{:keys [input-tag
                         selected-tags
                         on-change
                         on-add-tag
                         on-delete-tag
                         placeholder
                         no-render-tags?]}]
  (html
    [:div
     [:div.input-group
      [:input.input-group-field
       {:type        "text"
        :value       (:tag/name input-tag)
        :on-change   #(on-change {:tag/name (.-value (.-target %))})
        :on-key-down (fn [e]
                       (on-enter-down e #(on-add-tag {:tag/name %})))
        :placeholder (or placeholder "Filter tags...")}]
      [:span.input-group-label
       [:i.fa.fa-tag]]]

     (when-not no-render-tags?
       [:div
        (map-all
          selected-tags
          (fn [t]
            (tag t
                 {:on-delete #(on-delete-tag t)})))])]))

(defn on-change-in
  "Function that updates state in component c with assoc-in for the specified keys ks.
  Calls f on the input value and updates state with that, (or identity if not provided).
  Function f takes one argument that's the value of the input."
  ([c ks]
    (on-change-in c ks identity))
  ([c ks f]
   {:pre [(om/component? c) (vector? ks)]}
   (fn [e]
     (om/update-state! c assoc-in ks (f (.-value (.-target e)))))))

(defn on-change [c k]
  {:pre [(keyword? k)]}
  (on-change-in c [k]))

;################## Filter ##############

(defn update-filter [component input-filter]
  (update-query-params! component assoc :filter input-filter))

(defn add-tag-filter [component tag]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (update input-filter :filter/include-tags #(conj % tag))]

    (om/update-state! component assoc
                      :input-filter new-filters
                      :input-tag nil)
    (update-filter component new-filters)))

(defn- delete-tag-filter [component tag]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (update input-filter :filter/include-tags #(disj % tag))]

    (om/update-state! component assoc
                      :input-filter new-filters)
    (update-filter component new-filters)))

(defn tag-filter [component include-tags]
  (let [{:keys [input-tag]} (om/get-state component)]
    (html
      [:div
       (opts {:style {:display        :flex
                      :flex-direction :column}})

       (tag-input {:input-tag     input-tag
                   :selected-tags include-tags
                   :on-change     #(om/update-state! component assoc :input-tag %)
                   :on-add-tag    #(add-tag-filter component %)
                   :on-delete-tag #(delete-tag-filter component %)})])))

(defn select-date-filter [component k date]
  (let [{:keys [input-filter]} (om/get-state component)
        new-filters (assoc input-filter k date)]

    (om/update-state! component assoc
                      :input-filter new-filters)
    (update-filter component new-filters)))