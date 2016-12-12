(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate]]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

(defmethod client-mutate 'session/signout
  [_ _ _]
  {:action (fn []
             #?(:cljs
                (.removeItem js/localStorage "idToken")))})