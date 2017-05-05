(ns eponai.common.ui.components
  #?(:cljs (:require-macros [eponai.common.ui.components :refer [compiled-queries]]))
  (:require
    [eponai.common.ui.router :as router]
    [taoensso.timbre :refer [debug]]
    #?@(:clj
        [
    [eponai.common.ui.help :as help]
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.shopping-bag :as bag]
    [eponai.common.ui.store :as store]
    [eponai.web.ui.start-store :as start-store]
    [eponai.common.ui.store.dashboard :as store-dashboard]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]
    [eponai.common.ui.user :as user]
    [om.next :as om]
    [medley.core :as medley]])
    [clojure.walk :as walk]))

#?(:clj
   (defmacro compiled-queries []
     (let [routes {:index           {:component index/Index}
                   :coming-soon     {:component index/ComingSoon}
                   :sell            {:component start-store/StartStore}
                   :sell-soon       {:component index/ComingSoonBiz}
                   :store           {:component store/Store}
                   :store-dashboard {:component store-dashboard/Dashboard}
                   :checkout        {:component checkout/Checkout}
                   :shopping-bag    {:component bag/ShoppingBag}
                   :help            {:component help/Help}
                   :browse          {:component goods/Goods}
                   :product         {:component product/ProductPage}
                   :live            {:component streams/Streams}
                   :user            {:component user/User}
                   :login           {:component index/Login}
                   :unauthorized    {:component index/Unauthorized}}]
       (medley/map-vals (fn [{:keys [component]}]
                          {:query (->> (om/get-query component)
                                       (walk/postwalk
                                         #(cond-> %
                                                  (some-> % meta :component)
                                                  (vary-meta dissoc :component))))})
                        routes))))

(def route->queries (compiled-queries))

(reset! router/routes (keys route->queries))

(defmethod router/route->component :default
  [route]
  (get route->queries route))
