(ns eponai.common.routes
  (:require
    [eponai.common.ui.checkout :as checkout]
    [eponai.common.ui.store :as store]
    [eponai.common.ui.goods :as goods]
    [eponai.common.ui.index :as index]
    [eponai.common.ui.product :as product]
    [eponai.common.ui.streams :as streams]))

(def routes
  ["/" {""                     :index
        ["store/" :store-id]   :store
        "goods"                :goods
        ["goods/" :product-id] :goods
        "streams"              :streams
        "checkout"             :checkout}])

(def route->component
  {:index    {:id        "sulo-index"
              :component index/Index
              :factory   index/->Index}
   :store    {:id        "sulo-store"
              :component store/Store
              :factory   store/->Store}
   :checkout {:id        "sulo-checkout"
              :component checkout/Checkout
              :factory   checkout/->Checkout}
   :goods    {:id        "sulo-items"
              :component goods/Goods
              :factory   goods/->Goods}
   :product  {:id        "sulo-product-page"
              :component product/ProductPage
              :factory   product/->ProductPage}
   :streams  {:id        "sulo-streams"
              :component streams/Streams
              :factory   streams/->Streams}})
