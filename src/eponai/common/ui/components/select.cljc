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
         ;(om/update-state! component assoc :selected selected)
         (when on-change
           (on-change selected))))))

(defui SelectOne
  Object
  (render [this]
    (let [{:keys [selected]} (om/get-state this)
          {:keys [options value disabled clearable placeholder tab-index]} (om/props this)]
      #?(:cljs
         (dom/create-element js/Select
           (clj->js
             (cond->
               {:value     (:value value)
                :options   (clj->js options)
                :clearable (boolean clearable)
                :onChange  (on-select-fn this)
                :disabled  disabled}
               (some? placeholder)
               (assoc :placeholder placeholder)
               (some? tab-index)
               (assoc :tabIndex (str tab-index)))))))))

(def ->SelectOne (om/factory SelectOne))

#?(:cljs
   (modules/set-loaded! :react-select))
