(ns eponai.client.ui.datepicker
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [style opts]]
            [eponai.common.format :as format]
            [clojure.string :as s]
            [cljsjs.pikaday]
            [cljsjs.react.dom]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]
            [goog.events.KeyCodes]))

;; Wrapper inspired by:
;; https://github.com/thomasboyt/react-pikaday

(defn pikaday-ref-name [this] (str (:key (om/props this)) "pikaday"))

(defn get-pikaday-dom-node [this]
  (->> (pikaday-ref-name this)
       (om/react-ref this)
       (js/ReactDOM.findDOMNode)))

(defn set-date-if-changed [this new-date old-date]
  (let [new-time (when new-date (.getTime new-date))
        old-time (when old-date (.getTime old-date))]
    (when (not= new-time old-time)
      (if new-date
        ;; pass true to avoid calling picker's :onSelect
        (some-> (om/get-state this) ::picker deref (.setDate new-date true))
        ;; workaround for pikaday not clearing value when date set to falsey
        (aset (get-pikaday-dom-node this) "value" "")))))

(defn on-input-field-change
  "Fire our on-change function when the input field changes.
  Also fire the on-change function when enter is pressed and
  and when we're clearing the whole input field."
  [on-change]
  (fn [e]
    (let [target (.-target e)
         text (.-value target)
         [start end] [(aget target "selectionStart") (aget target "selectionEnd")]
         fire-on-change #(let [date (format/random-string->js-date text)]
                          (on-change date))]
     (condp = (.-keyCode e)
       goog.events.KeyCodes.ENTER (fire-on-change)
       ;; When we're deleting the last char or
       ;; When we're clearing the whole text.
       goog.events.KeyCodes.BACKSPACE (when (or (= 1 (count text))
                                                (and (pos? end) (= text (subs text start end))))
                                        ;; make sure we clear the text box so that Pikaday
                                        ;; doesn't convert the input to a date.
                                        (aset target "value" "")
                                        (fire-on-change))
       ;; else
       nil))))

(defui DatePicker
  Object
  (initLocalState [_] {::picker (atom nil)})
  (componentWillUnmount [this] (some-> this om/get-state ::picker (reset! nil)))
  (componentDidMount
    [this]
    (let [{:keys [on-change value]} (om/props this)
          picker (js/Pikaday.
                   #js {:field    (get-pikaday-dom-node this)
                        :format   "D MMM YYYY"
                        :onSelect (fn [d]
                                    (on-change (format/js-date->utc-ymd-date d)))})]
      (reset! (-> this om/get-state ::picker) picker)
      (set-date-if-changed this value nil)))
  (componentWillReceiveProps
    [this next-props]
    (let [{old-value :value} (om/props this)
          {next-value :value} next-props]
      (set-date-if-changed this next-value old-value)))
  (render [this]
    (let [{:keys [key on-change]} (om/props this)]
      (assert key "Datepicker needs :key in props")
      (html
       [:div.has-feedback
        [:input.form-control
         (opts {:on-key-down (on-input-field-change on-change)
                :type        "text"
                :ref         (pikaday-ref-name this)
                :placeholder (-> this om/props :placeholder)})]
        [:span
         {:class "glyphicon glyphicon-calendar form-control-feedback"}]]))))

;; props: {:value js/Date :on-change f :placeholder str}
(def ->Datepicker (om/factory DatePicker))
