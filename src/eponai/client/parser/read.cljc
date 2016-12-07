(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    [eponai.common.test-data :as td]
    #?(:cljs
       [cljs.reader])))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

(def data td/mocked-data)
(def stores (:stores td/mocked-data))

(defmethod client-read :query/store
  [{:keys [query]} _ {:keys [store-id] :as p}]
  (let [store (some #(when (= #?(:cljs (cljs.reader/read-string store-id)) (:store/id %))
                      %) stores)
        details "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."]
    {:value (-> (select-keys store (conj (filter keyword? query) :store/goods))
                (update :store/goods
                        (fn [gs] (map #(assoc % :item/details details) (apply concat (take 4 (repeat gs)))))))}))