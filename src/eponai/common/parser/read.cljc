(ns eponai.common.parser.read)

(def item-details "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.")

(defn multiply-store-items [store]
  (cond-> store
          (:item/_store store)
          (update :item/_store
                  #(into [] (comp (take 16)
                                  (map (fn [item]
                                         (assoc item :item/details item-details))))
                         (cycle %)))))

(defn compute-cart-price [cart]
  (cond-> cart
          (:cart/items cart)
          (assoc :cart/price (transduce (map :item/price) + 0M (:cart/items cart)))))