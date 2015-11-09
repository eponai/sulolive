(ns flipmunks.budget.ui.modal_dc
  (:require [devcards.core :as dc]
            [flipmunks.budget.ui.modal :as m]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard generated-card
  (m/->Modal {:dialog-fn #(html [:div "ModalDialog :) with a lot of wordsdskoadks akdsdsakdoskdks dsoakdos dksaokdsoak dosak dosak odksaok dsaok dosak dosak odsak odksao dksaopk dsapk dosapk dospak dopsak dopsak dospak das"
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 [:p "foo"]
                                 ])}))
