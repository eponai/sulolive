(ns eponai.web.routes.ui-handlers
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :refer [map->UiComponentMatch]]
            [eponai.web.ui.project :refer [Project ->Project]]
            [eponai.web.ui.all-transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.web.ui.settings :refer [Settings ->Settings]]
            [eponai.web.ui.stripe :refer [Payment ->Payment]]
            [eponai.web.ui.profile :refer [Profile ->Profile]]
            [om.next :as om]))

(defn param->x-fn [f validate-f]
  (fn [x]
    (let [ret (f x)]
      (assert (validate-f ret)
              (str "Route-param value: " x " was not casted correctly with: " validate-f
                   "."))
      ret)))

(def param->number (param->x-fn cljs.reader/read-string number?))
(def param->keyword (param->x-fn keyword keyword?))

(def route-param->mutation
  {:route-param/project-id            (fn [pid]
                                        `[(project/set-active-uuid {:project-dbid (param->number pid)})
                                          :proxy/side-bar])
   :route-param/project->selected-tab (fn [tab]
                                        `[(project/select-tab ~{:selected-tab (param->keyword tab)})])
   :route-param/transaction-id        (fn [tid]
                                        (if (nil? tid)
                                          `[(transactions/deselect)]
                                          `[(transactions/select-transaction ~{:transaction-dbid (param->number tid)})
                                            :query/selected-transaction]))
   :route-param/widget-id             (fn [wid]
                                        ;; TODO: What to do?
                                        )
   :route-param/widget-mode           #()
   :route-param/transaction-mode      #()})

(defn route-params->mutations [route-params]
  (reduce-kv (fn [mutations param value]
               (assert (contains? route-param->mutation param)
                       (str "route-param: " param " did not have a mutation function in: " route-param->mutation
                            ". Add the route-param to the map of key->function. Route-param value was: " value))
               (let [param-fn (get route-param->mutation param)]
                 (into mutations (param-fn value))))
             []
             route-params))

(defn mutate-route-params! [reconciler route-params]
  {:pre [(om/reconciler? reconciler)]}
  (om/transact! reconciler (route-params->mutations route-params)))

(def project-handler (map->UiComponentMatch
                       {:component      Project
                        :factory        ->Project
                        :route-param-fn mutate-route-params!}))

(def dashboard-handler (assoc project-handler
                         :route-param-fn
                         (fn [r p]
                           (mutate-route-params! r (assoc p :route-param/project->selected-tab :dashboard)))))
(def transactions-handler (assoc project-handler
                            :route-param-fn
                            (fn [r p]
                              (mutate-route-params! r (-> p
                                                          (assoc :route-param/project->selected-tab :transactions)
                                                          ;; (update {} :foo identity) => {:foo nil}
                                                          ;; Ensures :route-param/transaction-id is in the map.
                                                          (update :route-param/transaction-id identity))))))


(def route-handler->ui-component
  {:route/home                            project-handler
   :route/project                         project-handler
   :route/project-empty                   project-handler
   :route/project->dashboard              dashboard-handler
   :route/project->dashboard->widget      dashboard-handler
   :route/project->dashboard->widget+mode dashboard-handler
   :route/project->txs                    transactions-handler
   :route/project->txs->tx                transactions-handler
   :route/project->txs->tx+mode           transactions-handler
   :route/settings                        (map->UiComponentMatch {:component Settings
                                                                  :factory   ->Settings})
   :route/subscribe                       (map->UiComponentMatch {:component Payment
                                                                  :factory   ->Payment})
   :route/profile                         (map->UiComponentMatch {:component Profile
                                                                  :factory   ->Profile})
   :route/profile->txs                    (map->UiComponentMatch {:component AllTransactions
                                                                  :factory ->AllTransactions})})
