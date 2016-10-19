(ns eponai.web.routes.ui-handlers
  (:require [bidi.bidi :as bidi]
            [eponai.client.route-helper :refer [map->UiComponentMatch]]
            [eponai.web.ui.project :refer [Project ->Project]]
            [eponai.web.ui.project.all-transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.web.ui.settings :refer [Settings ->Settings]]
            ;[eponai.web.ui.stripe :refer [Payment ->Payment]]
            [eponai.web.ui.profile :refer [Profile ->Profile]]
            [medley.core :as medley]
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
   :route-param/project->selected-tab (fn [_ tab]
                                        `[(project/select-tab ~{:selected-tab (param->keyword tab)})])
   :route-param/widget-id             (fn [_ wid]
                                        `[(widget/set-active-id ~{:widget-id (when wid
                                                                               (if (= "new" wid)
                                                                                 (keyword wid)
                                                                                 (param->number wid)))})])
   :route-param/goal-id               (fn [_ gid]
                                        (debug "Goal id: " gid))
   :route-param/widget-type           (fn [_ type]
                                        `[(widget/select-type ~{:type (param->keyword type)})])
   :route-param/transaction-mode      (constantly nil)})

(defn route-params->mutations [route-params]
  (reduce-kv (fn [mutations param value]
               (assert (contains? route-param->mutation param)
                       (str "route-param: " param " did not have a mutation function in: " route-param->mutation
                            ". Add the route-param to the map of key->function. Route-param value was: " value))
               (let [param-fn (get route-param->mutation param)]
                 (into mutations (param-fn route-params value))))
             []
             route-params))

(def project-handler (map->UiComponentMatch
                       {:route-key      :route/project
                        :component      Project
                        :factory        ->Project
                        :route-param-fn route-params->mutations}))

(def dashboard-handler (assoc project-handler
                         :route-param-fn
                         (fn [p]
                           (route-params->mutations (assoc p :route-param/project->selected-tab :dashboard)))))

(def psettings-handler (assoc project-handler
                         :route-param-fn
                         (fn [p]
                           (route-params->mutations (assoc p :route-param/project->selected-tab :settings)))))

(def goal-handler (assoc project-handler
                    :route-param-fn
                    (fn [p]
                      (route-params->mutations (-> p
                                                   (assoc :route-param/project->selected-tab :goal)
                                                   (update :route-param/goal-id identity))))))

(def widget-handler (assoc project-handler
                      :route-param-fn
                      (fn [p]
                        (route-params->mutations (-> p
                                                     (assoc :route-param/project->selected-tab :widget)
                                                     (update :route-param/widget-id identity))))))

(defn is-transactions-handler? [handler]
  (true? (:transactions-handler handler)))

(def transactions-handler (assoc project-handler
                            :transactions-handler true
                            :route-param-fn
                            (fn [p]
                              (route-params->mutations (-> p (assoc :route-param/project->selected-tab :transactions))))))

(def settings-handler (map->UiComponentMatch {:route-key :route/settings
                                              :component Settings
                                              :factory   ->Settings}))

;(def payment-handler (map->UiComponentMatch {:route-key :route/payment
;                                             :component Payment
;                                             :factory   ->Payment}))

(def profile-handler (map->UiComponentMatch {:route-key :route/profile
                                             :component Profile
                                             :factory   ->Profile}))

(def route-handler->ui-component
  {:route/home                    dashboard-handler
   :route/project                 dashboard-handler
   :route/project-empty           dashboard-handler
   :route/project->dashboard      dashboard-handler
   :route/project->settings       psettings-handler
   :route/project->widget+type+id widget-handler
   :route/project->txs            transactions-handler
   ;:route/project->txs->tx        transactions-handler
   ;:route/project->txs->tx+mode   transactions-handler
   :route/settings                settings-handler
   ;:route/subscribe               payment-handler
   :route/profile                 profile-handler})

(def root-handlers [project-handler settings-handler profile-handler])

(def route-key->root-handler (->> (group-by :route-key root-handlers)
                                  (medley/map-vals first)))
