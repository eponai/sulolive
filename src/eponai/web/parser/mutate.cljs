(ns eponai.web.parser.mutate
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [eponai.common.database.transact :as t]
            [eponai.common.parser :refer [mutate]]
            [eponai.client.parser.mutate]
            [datascript.core :as d]
            [cljs-http.client :as http]
            [eponai.web.ui.utils :as utils]
            [eponai.web.homeless :as homeless]
            [om.next :as om]
            [taoensso.timbre :refer-macros [info debug error trace]]))

;; ################ Local mutations ####################
;; Local mutations goes here. These are specific to the
;; app running on this platform.
;; Remote mutations should be defined in:
;;     eponai.client.parser.mutate

;;####################### Singletons show/hide
(defmethod mutate 'ui.modal/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible true}])})

(defmethod mutate 'ui.modal/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/modal
                                 :ui.singleton.modal/visible false}])})

(defmethod mutate 'ui.menu/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible true}])})

(defmethod mutate 'ui.menu/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton               :ui.singleton/menu
                                 :ui.singleton.menu/visible false}])})

(defmethod mutate 'ui.loader/show
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible true}])})

(defmethod mutate 'ui.loader/hide
  [{:keys [state]} _ _]
  {:action #(t/transact state [{:ui/singleton                :ui.singleton/loader
                                 :ui.singleton.loader/visible false}])})

;; ################## Project ###############################

(defmethod mutate 'project/set-active-uuid
  [{:keys [state]} _ {:keys [project-dbid]}]
  {:action #(t/transact state [{:ui/component             :ui.component/project
                                :ui.component.project/eid project-dbid}])})

(defmethod mutate 'project/select-tab
  [{:keys [state]} _ {:keys [selected-tab]}]
  {:action #(t/transact state [{:ui/component             :ui.component/project
                                :ui.component.project/selected-tab selected-tab}])})

(defmethod mutate 'ui.component.project/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/project] :ui.component.project/eid]])})

;; ################### Transactions ################

(defmethod mutate 'transactions.filter/update
  [{:keys [state]} _ {:keys [filter]}]
  {:action #(t/transact state
                        [{:ui/component                     :ui.component/transactions
                          :ui.component.transactions/filter filter}])})

(defmethod mutate 'transactions.filter/clear
  [{:keys [state]} _ _]
  {:action #(t/transact state
                         [[:db.fn/retractAttribute [:ui/component :ui.component/transactions] :ui.component.transactions/filter]])})

(defmethod mutate 'transactions/deselect
  [{:keys [state]} _ _]
  {:action #(t/transact-one state [:db.fn/retractAttribute
                                   [:ui/component :ui.component/transactions]
                                   :ui.component.transactions/selected-transaction])})

(defmethod mutate 'transactions/select-transaction
  [{:keys [state]} _ {:keys [transaction-dbid]}]
  {:action #(t/transact-one state
                            {:ui/component                                   :ui.component/transactions
                             :ui.component.transactions/selected-transaction transaction-dbid})})

;;; ####################### Dashboard #########################

(defmethod mutate 'widget/set-active-id
  [{:keys [state]} _ {:keys [widget-id]}]
  {:action #(t/transact-one state
                            {:ui/component              :ui.component/widget
                             :ui.component.widget/id    (or widget-id :new)})})

(defmethod mutate 'widget/select-type
  [{:keys [state]} _ {:keys [type]}]
  {:action #(t/transact-one state
                            {:ui/component             :ui.component/widget
                             :ui.component.widget/type type})})

;;; ####################### Root component #########################

(defmethod mutate 'root/set-app-content
  [{:keys [state]} _ {:keys [handler]}]
  {:action #(t/transact-one state
                            {:ui/component                    :ui.component/root
                             :ui.component.root/route-handler handler})})

(defmethod mutate 'root/ack-route-changed
  [{:keys [state]} _ _]
  {:action #(t/transact-one state {:ui/component :ui.component/root
                                   :ui.component.root/route-changed false})})
;;; ####################### Playground ##############################

(defmethod mutate 'playground/subscribe
  [{:keys [state]} k {:keys [email]}]
  {:action (fn []
             (go
               (let [ret (<! (http/post homeless/email-endpoint-subscribe {:form-params {:email email}}))]
                 (om/merge! (deref utils/reconciler-atom) {:result {k ret :routing/app-root {}}}))))})


