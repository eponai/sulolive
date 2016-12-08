(ns eponai.client.run
  (:require
    [eponai.client.utils :as utils]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.parser :as parser]
    [eponai.client.parser.read]
    [goog.dom :as gdom]
    [om.next :as om]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.store :as store]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.goods :as goods]))

(defn run-element [{:keys [id component]}]
  (let [reconciler (om/reconciler {:state   (utils/init-conn)
                                   :parser  (parser/client-parser)
                                   :remotes []
                                   :migrate nil})]
    ;(reset! utils/reconciler-atom reconciler)
    (om/add-root! reconciler component (gdom/getElement id))))

(def inline-containers
  {:navbar   {:id        "sulo-navbar"
              :component nav/Navbar}
   :store    {:id        "sulo-store-container"
              :component store/Store}
   :checkout {:id        "sulo-checkout-container"
              :component checkout/Checkout}
   :goods    {:id        "sulo-items-container"
              :component goods/Goods}})

(defn run [k]
  (prn "RUN: " k)
  (run-element (:navbar inline-containers))
  (run-element (get inline-containers k)))