(ns eponai.server.ui.store
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.server.ui.common :as common]))

(def mocked-goods
  [{:name "Kids clothes"
    :price "$34.00"
    :img-src "https://img0.etsystatic.com/112/0/10558959/il_570xN.1006376182_5fke.jpg"}
   {:name "Beddings"
    :price "$52.00"
    :img-src "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg"}
   {:name "Accessories"
    :price "$134.00"
    :img-src "https://img1.etsystatic.com/030/0/6396625/il_570xN.635631611_4c3s.jpg"}
   {:name "Jewel"
    :price "$34.00"
    :img-src "https://img0.etsystatic.com/057/2/5243597/il_570xN.729877080_d5f4.jpg"}])

(defui Store
  Object
  (render [this]
    (let [{:keys [release?]} (om/props this)]
      (dom/html
        {:lang "en"}

        (apply dom/head nil (common/head release?))

        (dom/body
          {:id "sulo-store"}
          (common/navbar nil)
          (dom/div {:className "cover-photo"})

          (dom/div {:className "store-nav"}
            (dom/div {:className "row column"}
              (dom/ul {:className "menu"}
                      (dom/li nil "Store title"))))

          (dom/div {:className "items"}
            (apply dom/div {:className "featured-items-container row small-up-2 medium-up-4"}
              (mapcat (fn [p]
                        (map #(common/product-element %) (shuffle p)))
                      (take 4 (repeat mocked-goods)))))
          )))))
