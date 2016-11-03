(ns eponai.web.parser.mutate
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [eponai.common.database.transact :as t]
            [eponai.common.parser :refer [client-mutate]]
            [eponai.client.parser.mutate]
            [eponai.client.utils :as utils]
            [datascript.core :as d]
            [cljs-http.client :as http]
            [eponai.web.homeless :as homeless]
            [om.next :as om]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;; ################ Local mutations ####################
;; Local mutations goes here. These are specific to the
;; app running on this platform.
;; Remote mutations should be defined in:
;;     eponai.client.parser.mutate

;;####################### Singletons show/hide

(defmethod client-mutate 'ui.modal/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true}])})

(defmethod client-mutate 'ui.modal/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible false}])})

(defmethod client-mutate 'ui.menu/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible true}])})

(defmethod client-mutate 'ui.menu/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible false}])})

(defmethod client-mutate 'ui.loader/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible true}])})

(defmethod client-mutate 'ui.loader/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible false}])})

;; ################## Project ###############################

(defmethod client-mutate 'project/set-active-uuid
  [{:keys [state]} _ {:keys [project-dbid]}]
  {:action #(t/transact state [{:ui/component             :ui.component/project
                                :ui.component.project/eid project-dbid}])})

(defmethod client-mutate 'project/select-tab
  [{:keys [state]} _ {:keys [selected-tab]}]
  {:action #(t/transact state [{:ui/component             :ui.component/project
                                :ui.component.project/selected-tab selected-tab}])})

(defmethod client-mutate 'ui.component.project/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/project] :ui.component.project/eid]])})

;; ################### Transactions ################

(defmethod client-mutate 'transactions.filter/update
  [{:keys [state]} _ {:keys [filter]}]
  {:action #(t/transact state
                        [{:ui/component                     :ui.component/transactions
                          :ui.component.transactions/filter filter}])})

(defmethod client-mutate 'transactions.filter/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/transactions] :ui.component.transactions/filter]])})

;;; ####################### Dashboard #########################

(defmethod client-mutate 'widget/set-active-id
  [{:keys [state]} _ {:keys [widget-id]}]
  {:action #(t/transact-one state
                            {:ui/component              :ui.component/widget
                             :ui.component.widget/id    (or widget-id :new)})})

(defmethod client-mutate 'widget/select-type
  [{:keys [state]} _ {:keys [type]}]
  {:action #(t/transact-one state
                            {:ui/component             :ui.component/widget
                             :ui.component.widget/type type})})

;;; ####################### Root component #########################

(defmethod client-mutate 'root/set-app-content
  [{:keys [state]} _ {:keys [handler]}]
  {:action #(t/transact-one state
                            {:ui/component                    :ui.component/root
                             :ui.component.root/route-handler handler})})

(defmethod client-mutate 'root/ack-route-changed
  [{:keys [state]} _ _]
  {:action #(t/transact-one state {:ui/component :ui.component/root
                                   :ui.component.root/route-changed false})})
;;; ####################### Playground ##############################

(defmethod client-mutate 'playground/subscribe
  [{:keys [state reconciler]} k {:keys [email]}]
  (assert (some? reconciler) (str "No reconciler passed to the env of " k))
  {:action (fn []
             (go
               ;; TODO: Turn this into a remote mutation instead!
               (let [ret (<! (http/post homeless/email-endpoint-subscribe {:form-params {:email email}}))]
                 (om/merge! reconciler {:db (d/db (om/app-state reconciler))
                                        :history-id "THIS IS HOPEFULLY NEVER USED :D"
                                        :result {k ret :routing/app-root {}}}))))})


