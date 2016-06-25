(ns eponai.mobile.ios.ui.root
  ""
  (:require [eponai.client.ui :as ui :refer-macros [opts]]
            [eponai.mobile.components :refer [navigator]]
            [eponai.mobile.om-helper :as om-helper :refer-macros [with-om-vars]]
            [eponai.mobile.ios.routes.ui-handlers :as ui.routes]
            [eponai.mobile.ios.linking :as linking]
            [goog.object :as gobj]
            [om.next :as om :refer-macros [defui]]
            [taoensso.timbre :refer-macros [debug]]))

(def route->transition
  {:route/login {:route/transactions js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}
   :route/transactions {:route/login js/ReactNative.Navigator.SceneConfigs.FloatFromBottom}})

(defn props->route [props]
  (or (get-in props [:query/app :ui.component.app/route])
      :route/login))

(defui ^:once RootView
  static om/IQuery
  (query [this]
    (let [route (props->route (when (om/component? this) (om/props this)))
          static-query (get-in ui.routes/route-handler->ui-component [route :component])
          ;; Our navigator will contain the ref to the component which we'll
          ;; want to subquery.
          subquery (om-helper/subquery-in this [:nav route] static-query)
          query (cond-> [:datascript/schema
                         :user/current
                         {:query/app [:ui.component.app/route]}]
                        (some? subquery)
                        (conj {:proxy/route-data subquery}))]
      query))
  Object
  ;; Need to pass the identical function to add/removeEventListener.
  (componentWillMount [this]
    (om/update-state! this assoc :url-handler (fn [event]
                                                (linking/load-url! this (gobj/get event "url")))))
  (componentDidMount [this]
    (.addEventListener linking/linking "url" (-> this om/get-state :url-handler)))
  (componentWillUnmount [this]
    (.removeEventListener linking/linking "url", (-> this om/get-state :url-handler)))

  (render-scene [this _ _]
    (with-om-vars
      this
      (let [props (om/props this)
            route (props->route props)
            factory (get-in ui.routes/route-handler->ui-component [route :factory])]
        (factory (-> props :proxy/route-data (assoc :ref route))))))
  (componentWillUpdate [this next-props _]
    (let [route (props->route (om/props this))
          next-route (props->route next-props)]
      (when (not= next-route route)
        (debug "Changing route to: " next-route)
        (om/update-state! this assoc
                          :route next-route
                          :prev-route route)
        (.replace (om-helper/react-ref this :nav)
                  next-route))))
  (componentDidUpdate [this prev-props _]
    (let [prev-route (props->route prev-props)
          route (props->route (om/props this))]
      (when (not= prev-route route)
        (debug "Changed route from:" prev-route " to: " route)
        (debug "nav ref: " (om-helper/get-ref-in this [:nav route]))
        (om-helper/set-runtime-query! this))))
  (render [this]
    ;; Not sure if navigator's :on-did-focus needs .forceUpdate here.
    ;; But we've had a vicious bug when we didn't forceUpdate in another navigator.
    (navigator {:on-did-focus   #(.forceUpdate this)
                :ref            :nav
                :renderScene    #(.render-scene this %1 %2)
                :configureScene #(get-in route->transition [(:prev-route (om/get-state this))
                                                            (:route (om/get-state this))]
                                         js/ReactNative.Navigator.SceneConfigs.FloatFromRight)})))