(ns eponai.common.business.budget)

(defn cad->usd [x]
  (/ x 1.35))

"World"
(def world 
 {:businesses                              0
  :visitors                                0
 :visitor/time-watching-stream            (* 30 60)
 :conversion-rate/product-sales           0.05M
 :conversion-rate/ads                     0M
 :conversion-rate/viewer-subscribing      0M
 :conversion-rate/viewer-watching-stream  0.5M
 :product/comission-rate                  0.05M
 :product/visa-rate                       0.025M
 :price/ad-viewed                         0M
 :price/business-subscription             (cad->usd 89)     ;; TO USD
 :price/avg-product                       (cad->usd 40)
 :price/avg-viewer-subscription           5
 ;; cost of stream bandwidth per GB:
 :price/stream-bandwidth                  {:from-aws 0.03 :p2p 0.005}
 ;; ec2-server per month (m3.xlarge?)
 ;; TODO: Get real prices
 :price/ec2-server                        50
 :price/red5                              {:first-server 129 :rest-servers 79}
 :stream/avg-bit-rate                     1500
 ;; This is hopefully quite a low estimate:
 :stream/p2p-effectivity                  0.5M
 :stream/servers-per-visitor              (/ 1M 2000M)
 ;; It's really weird estimating this like this:
 :website/servers-per-visitor             (/ 1M 10000M)
 })

(defn add-businesses [world bs]
  (update world :businesses + bs))

(defn add-visitors [world vs]
  (update world :visitors + vs))


(defn multiply [world ks]
  (assert (every? (partial contains? world) ks)
          (str "World did not contain keys: "
               (into [] (remove (partial contains? world)) ks)))
  (->> (select-keys world ks)
       (vals)
       (apply *)))

(defn business-subscription-income [world]
  (multiply world [:businesses :price/business-subscription]))

(defn viewier-subscription-income [world]
  (multiply world [:visitors :conversion-rate/viewer-subscribing]))

(defn- our-comission-rate [world]
  (- (:product/comission-rate world)
     (:product/visa-rate world)))
  

(defn product-sales-income [world]
  (* (multiply world
               [:visitors
                :conversion-rate/product-sales
                :price/avg-product])
     (our-comission-rate world)))

(defn stream-ads-income [world]
  (multiply world [:visitors
                   ;; assuming we only have ads on the streams:
                   :conversion-rate/viewer-watching-stream
                   :conversion-rate/ads
                   :price/ad-viewed]))

(defn stream-bandwidth-cost [world]
  (let [bits-streamed
        (multiply world [:visitors
                         :conversion-rate/viewer-watching-stream
                         :visitor/time-watching-stream
                         :stream/avg-bit-rate])
        gb-streamed (/ bits-streamed 8 1024 1024 1024)
        p2p-portion (- 1 (:stream/p2p-effectivity world))
        aws-portion (- 1 p2p-portion)]
    (+ (* gb-streamed
          (get-in world [:price/stream-bandwidth :from-aws])
          aws-portion)
       (* gb-streamed
          (get-in world [:price/stream-bandwidth :p2p])
          p2p-portion))))

(defn ec2-server-time-cost [world]
  (+ (multiply world
               [:visitors
                :conversion-rate/viewer-watching-stream
                :stream/servers-per-visitor
                :price/ec2-server])
     (multiply world
               [:visitors
                :website/servers-per-visitor
                :price/ec2-server])))


(defn red5-server-license-cost [world]
  (let [servers (->> (multiply world
                              [:visitors
                               :conversion-rate/viewer-watching-stream
                               :stream/servers-per-visitor])
                    (+ 0.5M)
                    (double)
                    #?(:clj (Math/round)
                       :cljs (.round js/Math)))]
    (+ (get-in world [:price/red5 :first-server])
       (* (dec servers)
          (get-in world [:price/red5 :rest-servers])))))

(defn total-products-sold [world revenue]
  (-> (get-in revenue [:incomes :income/product-sales])
      (/ (our-comission-rate world))))

(defn revenue [world]
  (letfn [(adder [fn-map]
              (fn [world]
                (let [mapped (reduce-kv (fn [m k f] (assoc m k (f world))) {} fn-map)]
                  (assoc mapped :total (reduce + 0M (vals mapped))))))]
    (let [incomes ((adder {:income/business-subscription business-subscription-income
                          :income/viewer-subscription viewier-subscription-income
                          :income/product-sales product-sales-income
                          :income/stream-ads stream-ads-income}) world)
          expenses ((adder {:expense/stream-bandwidth stream-bandwidth-cost
                           :expense/ec2-server-time ec2-server-time-cost
                           :expense/red5-license red5-server-license-cost}) world)
          revenue {:revenue (- (:total incomes)
                               (:total expenses))
                   :incomes  incomes
                   :expenses expenses}]
      (assoc revenue 
             :total-products-sold-usd 
             (total-products-sold world revenue)))))

(defn revenue-in-world [businesses visitors]
  (-> world
      (add-businesses businesses)
      (add-visitors visitors)
      (revenue)))

