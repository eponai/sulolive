(ns eponai.client.parser.read
  (:require
    [eponai.common.parser :as parser :refer [client-read]]
    #?(:cljs
       [cljs.reader])))

;; ################ Local reads  ####################
;; Generic, client only local reads goes here.

;; ################ Remote reads ####################
;; Remote reads goes here. We share these reads
;; with all client platforms (web, ios, android).
;; Local reads should be defined in:
;;     eponai.<platform>.parser.read

;; ----------

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

(defmethod client-read :query/store
  [{:keys [query]} _ {:keys [store-id] :as p}]
  (prn "Got store id: " store-id)
  (let [store (some #(when (= #?(:cljs (cljs.reader/read-string store-id)) (:store/id %))
                      %) stores)]
    {:value (-> (select-keys store (conj (filter keyword? query) :store/goods))
                (update :store/goods
                        #(apply concat (take 4 (repeat %)))))}))