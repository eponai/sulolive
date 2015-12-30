(ns eponai.client.ui.datepicker
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [style]]
            [cljsjs.pikaday]
            [cljsjs.react.dom]
            [sablono.core :refer-macros [html]]
            [garden.core :refer [css]]))

;; Wrapper inspired by:
;; https://github.com/thomasboyt/react-pikaday

(def pikaday-ref-name "pikaday")

(defn get-pikaday-dom-node [this]
  (js/ReactDOM.findDOMNode (om/react-ref this pikaday-ref-name)))

(defn set-date-if-changed [this new-date old-date]
  (let [new-time (when new-date (.getTime new-date))
        old-time (when old-date (.getTime old-date))]
    (when (not= new-time old-time)
      (if new-date
        ;; pass true to avoid calling picker's :onSelect
        (some-> (om/get-state this) ::picker deref (.setDate new-date true))
        ;; workaround for pikaday not clearing value when date set to falsey
        (.value (get-pikaday-dom-node this) "")))))

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
                             :onSelect on-change})]
           (reset! (-> this om/get-state ::picker) picker)
           (set-date-if-changed this value nil)))
       (componentWillReceiveProps
         [this next-props]
         (let [{old-value :value} (om/props this)
               {next-value :value} next-props]
           (set-date-if-changed this next-value old-value)))
       (render [this]
               (html [:input {:type        "text"
                              :ref         pikaday-ref-name
                              :placeholder (-> this om/props :placeholder)}])))

;; props: {:value js/Date :on-change f :placeholder str}
(def ->Datepicker (om/factory DatePicker))
