(ns eponai.web.routes.ui-handlers
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :refer [map->UiComponentMatch]]
            [eponai.web.ui.project :as project :refer [Project ->Project]]
            [eponai.web.ui.all-transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.web.ui.settings :refer [Settings ->Settings]]
            [eponai.web.ui.stripe :refer [Payment ->Payment]]
            [eponai.web.ui.profile :refer [Profile ->Profile]]
            [eponai.web.ui.root :as root]
            [om.next :as om]
            [om.next.protocols :as om.p]
            [taoensso.timbre :refer-macros [warn debug error]]
            [eponai.common.parser :as parser]))

(defn param->x-fn [f validate-f]
  (fn [x]
    (let [ret (f x)]
      (if-not (validate-f ret)
        (do
          (warn "Route-param value: " x " was not casted correctly with: " validate-f ".")
          nil)
        ret))))

(def param->number (param->x-fn cljs.reader/read-string number?))
(def param->keyword (param->x-fn keyword keyword?))

(def route-param->mutation
  {:route-param/project-id            (fn [_ pid]
                                        `[(project/set-active-uuid ~{:project-dbid (param->number pid)})
                                          :proxy/side-bar])
   :route-param/project->selected-tab (fn [env tab]
                                        (let [r (:reconciler env)]
                                          `[(project/select-tab ~{:selected-tab      (param->keyword tab)})]))
   :route-param/widget-id             (fn [_ wid]
                                        `[(widget/set-active-id ~{:widget-id (when wid
                                                                               (param->number wid))})])
   :route-param/goal-id               (fn [_ gid]
                                        (debug "Goal id: " gid))
   :route-param/widget-type           (fn [_ type]
                                        `[(widget/select-type ~{:type (param->keyword type)})])
   :route-param/transaction-mode      #()})

(defn route-params->mutations [reconciler route-params]
  (reduce-kv (fn [mutations param value]
               (assert (contains? route-param->mutation param)
                       (str "route-param: " param " did not have a mutation function in: " route-param->mutation
                            ". Add the route-param to the map of key->function. Route-param value was: " value))
               (let [param-fn (get route-param->mutation param)]
                 (into mutations (param-fn {:reconciler reconciler
                                            :route-params route-params} value))))
             []
             route-params))

(defn mutate-route-params! [reconciler route-params]
  {:pre [(om/reconciler? reconciler)]}
  (let [{:keys [reads mutations]} (reduce (fn [m expr]
                                            (if (and (sequential? expr) (symbol? (first expr)))
                                              (update m :mutations conj expr)
                                              (update m :reads conj expr)))
                                          {:mutations [] :reads []}
                                          (route-params->mutations reconciler route-params))]
    (binding [parser/*parser-allow-remote* false]
      (debug "Transacting mutations: " mutations)
      (om/transact! reconciler (into mutations reads)))))

(def project-handler (map->UiComponentMatch
                       {:component      Project
                        :factory        ->Project
                        :route-param-fn mutate-route-params!}))

(def dashboard-handler (assoc project-handler
                         :route-param-fn
                         (fn [r p]
                           (mutate-route-params! r (assoc p :route-param/project->selected-tab :dashboard)))))

(def goal-handler (assoc project-handler
                    :route-param-fn
                    (fn [r p]
                      (mutate-route-params! r (-> p
                                                  (assoc :route-param/project->selected-tab :goal)
                                                  (update :route-param/goal-id identity))))))

(def widget-handler (assoc project-handler
                      :route-param-fn
                      (fn [r p]
                        (mutate-route-params! r (-> p
                                                    (assoc :route-param/project->selected-tab :widget)
                                                    (update :route-param/widget-id identity))))))
(def transactions-handler (assoc project-handler
                            :route-param-fn
                            (fn [r p]
                              (mutate-route-params! r (-> p (assoc :route-param/project->selected-tab :transactions))))))


(def route-handler->ui-component
  {:route/home                    dashboard-handler
   :route/project                 dashboard-handler
   :route/project-empty           dashboard-handler
   :route/project->dashboard      dashboard-handler
   :route/project->widget+type+id widget-handler
   :route/project->txs            transactions-handler
   ;:route/project->txs->tx        transactions-handler
   ;:route/project->txs->tx+mode   transactions-handler
   :route/settings                (map->UiComponentMatch {:component Settings
                                                          :factory   ->Settings})
   :route/subscribe               (map->UiComponentMatch {:component Payment
                                                          :factory   ->Payment})
   :route/profile                 (map->UiComponentMatch {:component Profile
                                                          :factory   ->Profile})
   :route/profile->txs            (map->UiComponentMatch {:component AllTransactions
                                                          :factory   ->AllTransactions})})
