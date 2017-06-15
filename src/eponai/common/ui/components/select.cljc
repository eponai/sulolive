(ns eponai.common.ui.components.select
  (:require
    #?(:cljs
       [cljsjs.react-select])
    #?(:cljs
       [eponai.web.modules :as modules])
    [eponai.common.ui.dom :as dom]
    [om.dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]))

(defn on-select-fn [component]
  (let [{:keys [on-change]} (om/get-computed component)]
    (fn [sel]
      (let [selected (-> sel
                         #?(:cljs (js->clj :keywordize-keys true)))]
        ;(om/update-state! component assoc :selected selected)
        (when on-change
          (on-change selected))))))

(defui SelectOne
  Object
  (render [this]
    (let [{:keys [addLabelText classes value clearable tab-index creatable?] :as props} (om/props this)
          handled-props (cond-> {:value        (:value value)
                                 :addLabelText (or addLabelText "New section")
                                 :clearable    (boolean clearable)
                                 :onChange     (on-select-fn this)}
                                (some? tab-index)
                                (assoc :tabIndex (str tab-index))
                                (some? classes)
                                (assoc :className (css/keys->class-str classes)))
          component-props (merge
                            (apply dissoc props (conj (keys handled-props) :classes :tab-index))
                            handled-props)]
      (debug ":className: " (css/keys->class-str classes))
      (assert (nil? (:onChange props))
              (str ":onChange was passed in props. Pass it in computed instead."))
      #?(:clj (dom/input (assoc component-props :type "text"))
         :cljs (om.dom/create-element
                 (if creatable?
                   js/Select.Creatable
                   js/Select)
                 (clj->js component-props))))))

(def ->SelectOne (om/factory SelectOne))
