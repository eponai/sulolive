(ns eponai.server.ui.store
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.server.ui.common :as common]))

(def mocked-goods
  [{:name "Duvet Dream"
    :price "$34.00"
    :img-src "https://img1.etsystatic.com/141/1/11651126/il_570xN.1142044641_1j6c.jpg"}
   {:name "Pillows"
    :price "$52.00"
    :img-src "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg"}
   {:name "Organic Linen"
    :price "$134.00"
    :img-src "https://img0.etsystatic.com/133/0/11651126/il_570xN.915745904_opjr.jpg"}
   {:name "Jewel Sheets"
    :price "$34.00"
    :img-src "https://img1.etsystatic.com/126/0/11651126/il_570xN.1098073811_5ca0.jpg"}])

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
                      (dom/li nil (dom/a nil "Sheets"))
                      (dom/li nil (dom/a nil "Pillows"))
                      (dom/li nil (dom/a nil "Duvets")))))

          (dom/div {:className "items"}
            (apply dom/div {:className "featured-items-container row small-up-2 medium-up-4"}
              (mapcat (fn [p]
                        (map #(common/product-element %) (shuffle p)))
                      (take 4 (repeat mocked-goods)))))
          )))))
