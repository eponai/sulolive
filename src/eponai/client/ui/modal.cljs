(ns eponai.client.ui.modal
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [sablono.core :as html :refer-macros [html]]
            [eponai.client.ui.add_transaction :as add.t :refer [->AddTransaction AddTransaction]]
            [eponai.client.ui.dashboard :as new-widget :refer [->NewWidget NewWidget]]
            [eponai.client.ui.stripe :refer [->Payment]]
            [garden.core :refer [css]]))

(defn content [modal props]
  (let [modal-content (:ui.singleton.modal/content modal)]

    (cond (= modal-content :ui.singleton.modal.content/add-transaction)
          (->AddTransaction (:proxy/add-transaction props))

          (= modal-content :ui.singleton.modal.content/add-widget)
          (->NewWidget {:on-save (:ui.singleton.modal/on-save modal)
                        :on-close (:ui.singleton.modal/on-close modal)}))))

(defn header [modal]
  (let [modal-content (:ui.singleton.modal/content modal)]

    (cond (= modal-content :ui.singleton.modal.content/add-transaction)
          (add.t/header)

          (= modal-content :ui.singleton.modal.content/add-widget)
          (new-widget/header))))

(defui Modal
  static om/IQuery
  (query [_]
    [{:query/modal [:ui.singleton.modal/visible
                    :ui.singleton.modal/content
                    :ui.singleton.modal/on-save
                    :ui.singleton.modal/on-close]}
     {:proxy/add-transaction (om/get-query AddTransaction)}])
  Object
  (render [this]
    (let [{:keys [query/modal] :as props} (om/props this)
          {:keys [ui.singleton.modal/visible]} modal
          on-close #(om/transact! this `[(ui.modal/hide) :query/modal])]

      (html
        [:div
         (opts {:style {:top              0
                        :bottom           0
                        :right            0
                        :left             0
                        :position         "fixed"
                        :z-index          1050
                        :opacity          1
                        :background       "rgba(0,0,0,0.6)"
                        :background-color "#123"
                        :display          (if visible "block" "none")}})
         [:div#click-outside-target
          (opts {:style    {:top      0
                            :bottom   0
                            :right    0
                            :left     0
                            :position "fixed"}
                 :on-click on-close})]

         (when visible
           [:div.modal-dialog
            [:div.modal-content
             [:div.modal-header
              [:button.close
               {:on-click on-close}
               "x"]
              (header modal)]
             [:div.modal-body
              (content modal props)]]])]))))

(def ->Modal (om/factory Modal))
