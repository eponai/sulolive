(ns eponai.common.ui.components.select
  (:require
    #?(:cljs
       [cljsjs.react-select])
    #?(:cljs
       [eponai.web.modules :as modules])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]))

#?(:cljs
   (defn on-select-fn [component]
     (fn [sel]
       (let [selected (js->clj sel :keywordize-keys true)
             {:keys [on-change]} (om/get-computed component)]
         (when on-change
           (on-change selected))))))

(defui SelectOne
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [options value disabled clearable placeholder tab-index id creatable?]} (om/props this)]
      #?(:cljs
         (dom/create-element
           (if creatable?
             js/Select.Creatable
             js/Select)
           (clj->js
             (cond->
               {:value     (:value value)
                :options   (clj->js options)
                :addLabelText "New section"
                :clearable (boolean clearable)
                :onChange  (on-select-fn this)
                :disabled  disabled}
               (some? placeholder)
               (assoc :placeholder placeholder)
               (some? tab-index)
               (assoc :tabIndex (str tab-index))
               (some? id)
               (assoc :id id))))))))

(def ->SelectOne (om/factory SelectOne))
