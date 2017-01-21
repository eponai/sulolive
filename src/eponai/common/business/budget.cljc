(ns eponai.common.business.budget)

(def days-per-month 30)

(defn cad->usd [x]
  (/ x 1.35))

(defn per-month->per-day [x]
  (/ x days-per-month))

(defn per-hour->per-day [x]
  (* x 24))

;; Q. What is Twitch?
;; A. Twitch is the world’s leading live social video platform and community for gamers with 9.6M daily active users with an average of 106 minutes watched per person per day and 1.7+ million unique broadcasters per month.

(def world
  {:businesses                             0
   :visitors                               0
   ;; 106 minutes watched per person per day (is what twitch has)
   :visitor/time-watching-stream           (* 106 60)
   :conversion-rate/product-sales          0.02
   :conversion-rate/ads                    0
   :conversion-rate/viewer-subscribing     0
   :conversion-rate/viewer-watching-stream 0.5
   :product/commission-rate                0.10
   :product/stripe-rate                    0.029
   :product/stripe-fee                     0.30
   :price/ad-viewed                        0
   :price/business-subscription            (per-month->per-day (cad->usd 99))
   :price/avg-viewer-subscription          (per-month->per-day 5)
   :price/avg-product                      (cad->usd 60)
   ;; cost of stream bandwidth per GB:
   :price/stream-bandwidth                 {:to-aws         0.00
                                            :from-ec2       0.05
                                            ;; Streamroot CDN+p2p solution is $0.01/GB
                                            :cdn-streamroot 0.01
                                            :p2p-streamroot 0.005}
   ;; ec2-server per month (m3.xlarge?)
   ;; TODO: Get real prices
   :price.ec2/t2.small                     (per-hour->per-day 0.023)
   :price.ec2/t2.large                     (per-hour->per-day 0.094)
   :price.ec2/m4.large                     (per-hour->per-day 0.108)
   :price.ec2/m4.xlarge                    (per-hour->per-day 0.215)
   :price.ec2/m4.2xlarge                   (per-hour->per-day 0.431)
   :price/ec2-server                       50
   :price/red5-license                     {:first-server (per-month->per-day 129)
                                            :rest-servers (per-month->per-day 79)}
   :red5/server-capacity                   {:m4.2xlarge 2600}
   :red5.server-type/stream-manager        :t2.small
   :red5.server-type/origin                :t2.large
   :red5.server-type/edge                  :m4.xlarge
   ;; Streamroot saves bandwidth by using p2p tech, so we can reduce the bandwidth costs?
   ;; According to a livestreaming usecase - retrievable from streamroot.io - the bandwidth savings range is 60%-75%.
   :streamroot/bandwidth-reduction         0.50
   :stream/avg-bit-rate                    1500
   ;; This is hopefully quite a low estimate, but it doesn't matter if we're using streamroot's CDN+p2p solution.
   :stream/p2p-efficiency                  0.8
   :stream/avg-edges                       2
   :stream/avg-stream-managers             1
   :stream/avg-origins                     1
   ;; It's really weird estimating this like this:
   :stream/servers-per-visitor             (/ 1 2000)
   ;; It's really weird estimating this like this:
   :website/servers-per-visitor            (/ 1 10000)
   :website/avg-servers                    1
   :website/server-type                    :m4.large
   })

(defn price-by-ec2-type [world ec2-type]
  {:pre [(keyword? ec2-type)] :post [(number? %)]}
  (get world (keyword "price.ec2" (name ec2-type))))

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
  (- (:product/commission-rate world)
     (:product/stripe-rate world)))


(defn product-sales-income [world]
  (let [products-sold (multiply world [:visitors
                                       :conversion-rate/product-sales])]
    (- (* products-sold
          (:price/avg-product world)
          (our-comission-rate world))
       (* products-sold
          (:product/stripe-fee world)))))

(defn stream-ads-income [world]
  (multiply world [:visitors
                   ;; assuming we only have ads on the streams:
                   :conversion-rate/viewer-watching-stream
                   :conversion-rate/ads
                   :price/ad-viewed]))

(defn stream-bandwidth-cost [world]
  (let [kbits-per-stream (multiply world [:stream/avg-bit-rate
                                          :visitor/time-watching-stream])
        gb-per-stream (/ kbits-per-stream 8 1024 1024)
        cost-between-aws-and-streamroot (* gb-per-stream
                                           ;; We only stream once between aws and streamroot?
                                           1
                                           (get-in world [:price/stream-bandwidth :from-ec2]))
        cost-between-streamroot-and-visitors (* gb-per-stream
                                                (multiply world [:visitors
                                                                 :conversion-rate/viewer-watching-stream
                                                                 :streamroot/bandwidth-reduction])
                                                (get-in world [:price/stream-bandwidth
                                                               :cdn-streamroot]))]
    (+ cost-between-aws-and-streamroot
       cost-between-streamroot-and-visitors)))

(defn ec2-server-time-cost [world]
  (letfn [(server-price [amount-key price-key]
            (* (get world amount-key)
               (price-by-ec2-type world (get world price-key))))]
    (+ (server-price :website/avg-servers
                     :website/server-type)
       (server-price :stream/avg-edges
                     :red5.server-type/edge)
       (server-price :stream/avg-origins
                     :red5.server-type/origin)
       (server-price :stream/avg-stream-managers
                     :red5.server-type/stream-manager))))


(defn red5-server-license-cost [world]
  (let [servers (+ (:stream/avg-origins world)
                   (:stream/avg-edges world))]
    (+ (get-in world [:price/red5-license :first-server])
       (* (dec servers)
          (get-in world [:price/red5-license :rest-servers])))))

(defn total-products-sold [world]
  (multiply world [:visitors
                   :conversion-rate/product-sales
                   :price/avg-product]))

(defn revenue [world]
  (letfn [(compute-sum [fn-map]
            (fn [world]
              (let [mapped (reduce-kv (fn [m k f] (assoc m k (f world))) {} fn-map)]
                (assoc mapped :total (reduce + 0 (vals mapped))))))]
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
                     (total-products-sold world)
                     :income-per-expense (/ (:total incomes)
                                            (:total expenses))
                     :profit-per-income (/ (:profit revenue)
                                           (:total incomes))))))

(defn revenue-in [businesses visitors]
  (-> world
      (add-businesses businesses)
      (add-visitors visitors)
      (revenue)))
