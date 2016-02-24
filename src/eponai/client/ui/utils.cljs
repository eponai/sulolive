(ns eponai.client.ui.utils
  (:require [eponai.client.ui :refer-macros [opts]]
            [sablono.core :refer-macros [html]]))

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

(defn tag [tag-name {:keys [on-delete
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

(defn- on-key-down [e f]
  (when (and (= 13 (.-keyCode e))
             (seq (.. e -target -value)))
    (.preventDefault e)
    (f (.. e -target -value))    ))

(defn tag-input [{:keys [value on-change on-add-tag]}]
  (html
    [:div.has-feedback
     [:input.form-control
      {:type        "text"
       :value       value
       :on-change   on-change
       :on-key-down #(on-key-down % on-add-tag)
       :placeholder "Filter tags..."}]
     [:span
      {:class "glyphicon glyphicon-tag form-control-feedback"}]]))