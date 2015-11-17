(ns eponai.devcards.ui.modal_dc
  (:require [devcards.core :as dc]
            [om.next :as om :refer-macros [defui]]
            [eponai.client.ui.modal :as m]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defui ModalCard
  Object
  (render [this]
          (let [{:keys [::show-modal]} (om/get-state this)]
            (html
             [:div
              [:button {:on-click #(om/update-state! this assoc ::show-modal true)}
               "show modal"]
              (when show-modal
                (m/->Modal (assoc (om/props this)
                             :on-close
                             #(om/update-state! this assoc ::show-modal false))))]))))

(def ->ModalCard (om/factory ModalCard))

(defcard generated-card
  (->ModalCard {:title "My Modal"
                :dialog-content #(html [:div "ModalDialog :) with a lot of wordsdskoadks akdsdsakdoskdks dsoakdos dksaokdsoak dosak dosak odksaok dsaok dosak dosak odsak odksao dksaopk dsapk dosapk dospak dopsak dopsak dospak das"
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 ])}))
