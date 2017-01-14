(ns eponai.common.business.budget)

(defn cad->usd [x]
  (/ x 1.35))

;; Q. What is Twitch?
;; A. Twitch is the world’s leading live social video platform and community for gamers with 9.6M daily active users with an average of 106 minutes watched per person per day and 1.7+ million unique broadcasters per month.

(def world
  {:businesses                             0
   :visitors                               0
   :visitor/time-watching-stream           (* 30 60 10)
   :conversion-rate/product-sales          0.03M
   :conversion-rate/ads                    0M
   :conversion-rate/viewer-subscribing     0M
   :conversion-rate/viewer-watching-stream 0.5M
   :product/comission-rate                 0.10M
   :product/stripe-rate                    0.029M
   ;; TODO: Include stripe fee in calculations
   :product/stripe-fee                     0.30M
   :price/ad-viewed                        0M
   :price/business-subscription            (cad->usd 99)
   :price/avg-product                      (cad->usd 60)
   :price/avg-viewer-subscription          5
   ;; cost of stream bandwidth per GB:
   :price/stream-bandwidth                 {:from-aws 0.05 :p2p 0.005}
   ;; ec2-server per month (m3.xlarge?)
   ;; TODO: Get real prices
   :price/ec2-server                       50
   :price/red5                             {:first-server 129 :rest-servers 79}
   :stream/avg-bit-rate                    1500
   ;; This is hopefully quite a low estimate:
   :stream/p2p-effectivity                 0.8M
   :stream/servers-per-visitor             (/ 1M 2000M)
   ;; It's really weird estimating this like this:
   :website/servers-per-visitor            (/ 1M 10000M)
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
     (:product/stripe-rate world)))


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
  (let [kbits-streamed
        (multiply world [:visitors
                         :conversion-rate/viewer-watching-stream
                         :visitor/time-watching-stream
                         :stream/avg-bit-rate])
        gb-streamed (/ kbits-streamed 8 1024 1024)
        p2p-portion (:stream/p2p-effectivity world)
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
                     #?(:clj  (Math/round)
                        :cljs (.round js/Math)))]
    (+ (get-in world [:price/red5 :first-server])
       (* (dec servers)
          (get-in world [:price/red5 :rest-servers])))))

(defn total-products-sold [world revenue]
  (-> (get-in revenue [:incomes :income/product-sales])
      (/ (our-comission-rate world))))

(defn revenue [world]
  (letfn [(compute-sum [fn-map]
            (fn [world]
              (let [mapped (reduce-kv (fn [m k f] (assoc m k (f world))) {} fn-map)]
                (assoc mapped :total (reduce + 0M (vals mapped))))))]
    (let [incomes ((compute-sum {:income/business-subscription business-subscription-income
                                 :income/viewer-subscription   viewier-subscription-income
                                 :income/product-sales         product-sales-income
                                 :income/stream-ads            stream-ads-income})
                    world)
          expenses ((compute-sum {:expense/stream-bandwidth stream-bandwidth-cost
                                  :expense/ec2-server-time  ec2-server-time-cost
                                  :expense/red5-license     red5-server-license-cost})
                     world)
          revenue {:incomes  incomes
                   :expenses expenses
                   :profit   (- (:total incomes)
                                (:total expenses))}]
      (assoc revenue :total-products-sold-usd
                     (total-products-sold world revenue)
                     :income-per-expense (/ (:total incomes)
                                            (:total expenses))
                     :profit-per-income (/ (:profit revenue)
                                           (:total incomes))))))

(defn revenue-in [businesses visitors]
  (-> world
      (add-businesses businesses)
      (add-visitors visitors)
      (revenue)))
