(ns eponai.common.parser
  (:require [eponai.common.parser.read :as read]
            [eponai.common.parser.mutate :as mutate]

    #?(:clj  [om.next.server :as om]
       :cljs [om.next :as om])))

(defn parser
  ([] (om/parser {:read read/read :mutate mutate/mutate})))
