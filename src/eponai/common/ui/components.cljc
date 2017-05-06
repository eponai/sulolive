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
    [eponai.common.ui.store.dashboard :as store-dashboard]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product-page :as product-page]
    [eponai.common.ui.streams :as streams]
    [eponai.common.ui.user :as user]
    [eponai.web.ui.start-store :as start-store]
    [eponai.web.ui.coming-soon :as coming-soon]
    [eponai.web.ui.login :as login]
    [eponai.web.ui.unauthorized :as unauthorized]
    [om.next :as om]
    [medley.core :as medley]])
    [clojure.walk :as walk]))

#?(:clj
   (defmacro compiled-queries []
     (let [routes {:index           {:component index/Index}
                   :coming-soon     {:component coming-soon/ComingSoon}
                   :sell            {:component start-store/StartStore}
                   :store           {:component store/Store}
                   :store-dashboard {:component store-dashboard/Dashboard}
                   :checkout        {:component checkout/Checkout}
                   :shopping-bag    {:component bag/ShoppingBag}
                   :help            {:component help/Help}
                   :browse          {:component goods/Goods}
                   :product         {:component product-page/ProductPage}
                   :live            {:component streams/Streams}
                   :user            {:component user/User}
                   :login           {:component login/Login}
                   :unauthorized    {:component unauthorized/Unauthorized}}]
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
