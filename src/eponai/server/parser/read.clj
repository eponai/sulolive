(ns eponai.server.parser.read
  (:require
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read]]
    [eponai.server.datomic.query :as query]
    [taoensso.timbre :as timbre :refer [error debug trace warn]]))

(def stores
  [{:store/name         "HeadnTail"
    :store/id           0
    :store/review-count 13
    :store/rating       4.5
    :store/photo        "https://img1.etsystatic.com/122/0/10558959/isla_500x500.21872363_66njj7uo.jpg"
    :store/goods        [{:item/name    "Vintage 90's Fila Sport USA Sweatshirt Red Black Sport Trainer Sweater"
                          :item/price   "$34.00"
                          :item/img-src "https://img0.etsystatic.com/104/0/10558959/il_570xN.1015873526_1oed.jpg"
                          :item/id      "00"}
                         {:item/name    "Jean Michel Basquiat Pink T Shirt"
                          :item/price   "$52.00"
                          :item/img-src "https://img1.etsystatic.com/108/0/10558959/il_570xN.1060411775_k1ob.jpg"
                          :item/id      "01"}
                         {:item/name    "Vintage Tommy Hilfiger Short Pant Size 34"
                          :item/price   "$134.00"
                          :item/img-src "https://img1.etsystatic.com/146/0/10558959/il_570xN.1138598331_iu0t.jpg"
                          :item/id      "02"}
                         {:item/name    "Majestic Utah Jazz Hardwood Classics White Throwback NBA Basketball Jersey"
                          :item/price   "$34.00"
                          :item/img-src "https://img1.etsystatic.com/101/0/10558959/il_570xN.1038331841_gk1f.jpg"
                          :item/id      "03"}]}
   {:store/name         "MagicLinen"
    :store/id           1
    :store/rating       5
    :store/cover        "https://img0.etsystatic.com/151/0/11651126/isbl_3360x840.22956500_1bj341c6.jpg"
    :store/photo        "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg"
    :store/review-count 8
    :store/goods        [{:item/name    "Linen duvet cover. Woodrose colour. Linen bedding. Stonewashed linen duvet cover. Taupe bedding. Linen bedding queen, king, double, twin."
                          :item/price   "$34.00"
                          :item/img-src "https://img1.etsystatic.com/141/1/11651126/il_570xN.1142044641_1j6c.jpg"
                          :item/id      "10"}
                         {:item/name    "Linen pillowcases with ribbons. Bow tie linen pillow cover. Natural linen pillow case. Pillow cases with ties. Romantic pillow cases"
                          :item/price   "$52.00"
                          :item/img-src "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg"
                          :item/id      "11"}
                         {:item/name    "Stone washed linen duvet cover, pleated. White linen bedding. Linen quilt cover. White duvet cover. Luxury, original, modern, handmade"
                          :item/price   "$134.00"
                          :item/img-src "https://img0.etsystatic.com/133/0/11651126/il_570xN.915745904_opjr.jpg"
                          :item/id      "12"}
                         {:item/name    "Linen fitted sheet. Aquamarine colour. Blue linen fitted sheet. Natural bed sheet. Softened. Green blue stone washed linen bed sheet"
                          :item/price   "$34.00"
                          :item/img-src "https://img1.etsystatic.com/126/0/11651126/il_570xN.1098073811_5ca0.jpg"
                          :item/id      "13"}]}
   {:store/name         "thislovesthat"
    :store/id           2
    :store/cover        "https://img1.etsystatic.com/126/0/6396625/iusb_760x100.17290451_heo8.jpg"
    :store/review-count 43
    :store/rating       3.5
    :store/photo        "https://img1.etsystatic.com/121/0/6396625/isla_500x500.17289961_hkw1djlp.jpg"
    :store/goods        [{:item/name    "Glitter & Navy Blue Envelope Clutch"
                          :item/img-src "https://img1.etsystatic.com/030/0/6396625/il_570xN.635631611_4c3s.jpg"
                          :item/price   "$34.00"
                          :item/id      "20"}
                         {:item/name    "Mint Green & Gold Scallop Canvas Clutch"
                          :item/img-src "https://img0.etsystatic.com/031/0/6396625/il_570xN.581066412_s3ff.jpg"
                          :item/price   "$52.00"
                          :item/id      "21"}
                         {:item/name    "Modern Geometric Wood Bead Necklace"
                          :item/price   "$134.00"
                          :item/img-src "https://img0.etsystatic.com/045/1/6396625/il_570xN.723123424_ht5e.jpg"
                          :item/id      "22"}
                         {:item/name    "Modern Wood Teardrop Stud Earrings"
                          :item/price   "$34.00"
                          :item/img-src "https://img1.etsystatic.com/033/0/6396625/il_570xN.523107737_juvf.jpg"
                          :item/id      "23"}]}
   {:store/name         "Nafsika"
    :store/cover        "https://img1.etsystatic.com/133/0/5243597/isbl_3360x840.20468865_f7kumdbt.jpg"
    :store/review-count 22
    :store/rating       4.5
    :store/id           3
    :store/photo        "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg"
    :store/goods        [{:item/name    "Silver Twig Ring Milky Aquamarine Cabochon Light Blue March Birthstone Gifts for her Botanical Jewelry"
                          :item/img-src "https://img0.etsystatic.com/036/1/5243597/il_570xN.654738182_2k08.jpg"
                          :item/price   "$34.00"
                          :item/id      "30"}
                         {:item/name    "Bunny Charm Necklace, Bunny Necklace, Rabbit Necklace, Easter Gift, Child Necklace, Sterling silver, Rabbit Jewelry, Bunny Pendant"
                          :item/img-src "https://img0.etsystatic.com/024/0/5243597/il_570xN.519102094_4gu0.jpg"
                          :item/price   "$52.00"
                          :item/id      "31"}
                         {:item/name    "Red Moss Planter Fall Cube Necklace Sterling Silver Pendant Botanical Jewelry Novelty Pendant"
                          :item/img-src "https://img0.etsystatic.com/140/1/5243597/il_570xN.964805038_b4eq.jpg"
                          :item/price   "$134.00"
                          :item/id      "32"}
                         {:item/name    "Citrine Ring Elvish Twig Ring Branch Ring Thorn Jewelry November Birthstone Gifts for her Fine Jewelry"
                          :item/img-src "https://img0.etsystatic.com/139/3/5243597/il_570xN.931188156_qhqe.jpg"
                          :item/price   "$34.00"
                          :item/id      "33"}]}])

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :query/store
  [{:keys [query params]} _ _]
  (let [{:keys [store-id]} params]
    (let [store (some #(when (= (Long/parseLong store-id) (:store/id %))
                        %) stores)]
      {:value (-> (select-keys store query)
                  (update :store/goods
                          #(apply concat (take 4 (repeat %)))))})))

(defmethod server-read :query/featured-stores
  [{:keys [query]} _ _]
  (let [featured-stores (take 4 (shuffle stores))
        photos-fn (fn [s]
                    (let [[img-1 img-2] (take 2 (shuffle (map :item/img-src (:store/goods s))))]
                      (assoc s :store/featured-img-src [img-1 (:store/photo s) img-2])))
        xf (comp (map photos-fn) (map #(select-keys % query)))]
    {:value (transduce xf conj [] featured-stores)}))

(defmethod server-read :query/all-items
  [{:keys [query]} _ _]
  (prn "query/all-items: " query)
  (let [goods (map #(select-keys % query) (mapcat :store/goods stores))
        xf (comp (mapcat :store/goods) (map #(select-keys % query)))]

    (prn "Got goods: " goods)
    {:value (transduce xf conj [] stores)}))

(defmethod server-read :query/featured-items
  [{:keys [query]} _ _]
  (prn "query/all-items: " query)
  (let [goods (map #(select-keys % query) (mapcat :store/goods stores))
        xf (comp (mapcat :store/goods) (map #(select-keys % query)))]

    (prn "Got goods: " goods)
    {:value (map #(select-keys % query) (take 4 (shuffle (transduce xf conj [] stores))))}))

(defmethod server-read :query/item
  [{:keys [query params]} _ _]
  (let [{:keys [product-id]} params]
    (prn "Read query/item: " product-id)
    (let [all-items (mapcat :store/goods stores)
          product (some #(when (= product-id (:item/id %)) %) all-items)
          store (some #(when (some #{product-id} (mapv :item/id (:store/goods %)))
                        %) stores)]
      {:value (select-keys (assoc product :item/store store) query)})))

(defmethod server-read :query/featured-streams
  [{:keys [query]} _ _]
  {:value (map #(select-keys % query)
               (shuffle [{:stream/name         "Wear and tear proof your clothes"
                          :stream/store        (get stores 0)
                          :stream/viewer-count 8
                          :stream/img-src      "https://img1.etsystatic.com/122/0/10558959/isla_500x500.21872363_66njj7uo.jpg"}
                         {:stream/name         "What's up with thread count"
                          :stream/store        (get stores 1)
                          :stream/viewer-count 13
                          :stream/img-src      "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg"}
                         {:stream/name         "Old looking leather, how?"
                          :stream/store        (get stores 2)
                          :stream/viewer-count 43
                          :stream/img-src      "https://img1.etsystatic.com/121/0/6396625/isla_500x500.17289961_hkw1djlp.jpg"}
                         {:stream/name         "Talking wedding bands"
                          :stream/store        (get stores 3)
                          :stream/viewer-count 3
                          :stream/img-src      "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg"}]))})