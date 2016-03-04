(ns eponai.client.ui.utils
  (:require [eponai.client.ui :refer-macros [opts] :refer [map-all]]
            [eponai.client.ui.datepicker :refer [->Datepicker]]
            [sablono.core :refer-macros [html]]
            [om.next :as om]
            [taoensso.timbre :refer-macros [debug]]))

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

(defn modal [{:keys [content on-close class]}]
  (html
    [:div
     (opts {:style {:top      0
                    :bottom   0
                    :right    0
                    :left     0
                    :position :absolute
                    :z-index  1050
                    :opacity  1
                    ;:background       "rgba(0,0,0,0.6)"
                    ;:background-color "#123"
                    :display  "block"}})
     (click-outside-target on-close)

     [:div
      {:class (str "modal-dialog " (or class "modal-sm"))}
      [:div.modal-content
       content]]]))

(defn tag [{tag-name :tag/name} {:keys [on-delete
                                        on-click]}]
  (html
    [:div
     (opts {:class "btn-group btn-group-xs"
            :style {:padding "0.1em"}
            :key [tag-name]})

     [:button
      {:class    "btn btn-info"
       :on-click (or on-click #(prn (.. % -target)))}
      tag-name]

     (when on-delete
       [:button
        {:class    "btn btn-info"
         :on-click #(on-delete)}
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
                         placeholder]}]
  (html
    [:div
     [:div.has-feedback
      [:input.form-control
       {:type        "text"
        :value       (:tag/name input-tag)
        :on-change   #(on-change {:tag/name (.-value (.-target %))})
        :on-key-down (fn [e]
                       (on-enter-down e #(on-add-tag {:tag/name %})))
        :placeholder (or placeholder "Filter tags...")}]
      [:span
       {:class "glyphicon glyphicon-tag form-control-feedback"}]]

     [:div
      (opts {:style {:display        :flex
                     :flex-direction :row
                     :flex-wrap      :wrap
                     :width          "100%"}})
      (map-all
        selected-tags
        (fn [t]
          (tag t
               {:on-delete #(on-delete-tag t)})))]]))

(defn date-picker [{:keys [value placeholder on-change]}]
  [:div#date-input
   (->Datepicker
     (opts {:key         [placeholder]
            :placeholder placeholder
            :value       value
            :on-change   on-change}))])

(defn on-change-in [c ks]
  {:pre [(om/component? c) (vector? ks)]}
  (fn [e]
    (om/update-state! c assoc-in ks (.-value (.-target e)))))

(defn on-change [c k]
  {:pre [(keyword? k)]}
  (on-change-in c [k]))
