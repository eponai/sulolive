(ns eponai.web.ui.nav.loading-bar
  (:require
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as web.utils])
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.dom :as dom]
    [clojure.string :as string]))

(defui LoadingBar
  static om/IQuery
  (query [this] [{:query/loading-bar [:ui.singleton.loading-bar/show?]}])
  Object
  (initLocalState [this]
    {:is-active? true
     #?@(:cljs
         [:on-transition-iteration-fn (fn []
                                        (let [{:query/keys [loading-bar]} (om/props this)
                                              is-loading? (:ui.singleton.loading-bar/show? loading-bar)]
                                          (when-let [spinner (web.utils/element-by-id "sl-global-spinner")]
                                            (when-not is-loading?
                                              (web.utils/remove-class-to-element spinner "is-active")))
                                          ))])})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [on-transition-iteration-fn]} (om/get-state this)
             spinner (web.utils/element-by-id "sl-global-spinner")]
         (when spinner
           (.removeEventListener spinner "webkitAnimationIteration" on-transition-iteration-fn)
           (.removeEventListener spinner "animationiteration" on-transition-iteration-fn)))))
  (componentDidMount [this]
    #?(:cljs
       (let [{:keys [on-transition-iteration-fn]} (om/get-state this)
             spinner (web.utils/element-by-id "sl-global-spinner")]
         (when spinner
           (.addEventListener spinner "webkitAnimationIteration" on-transition-iteration-fn)
           (.addEventListener spinner "animationiteration" on-transition-iteration-fn))
         (om/update-state! this assoc :is-active? false))))
  (componentWillReceiveProps [this next-props]
    (let [{:query/keys [loading-bar]} next-props]
      #?(:cljs
         (when-let [spinner (web.utils/element-by-id "sl-global-spinner")]
           (let [is-loading? (:ui.singleton.loading-bar/show? loading-bar)
                 spinner-active? (string/includes? (.-className spinner) "is-active")]
             (if is-loading?
               (when-not spinner-active?
                 (debug "ADD LOADER ACTIVE")
                 (web.utils/add-class-to-element spinner "is-active"))
               (when spinner-active?
                 (debug "REMOVE LOADER ACTIVE")
                 (web.utils/remove-class-to-element spinner "is-active"))))))))
  (render [this]
    (let [{:keys [is-active?]} (om/get-state this)]
      (dom/div
        (cond->> (css/add-classes [:sl-global-spinner] {:id "sl-global-spinner"})
                 is-active?
                 (css/add-class :is-active))))))

(def ->LoadingBar (om/factory LoadingBar))