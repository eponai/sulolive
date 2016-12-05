(ns eponai.server.parser.read
  (:require
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser :refer [server-read]]
    [eponai.server.datomic.query :as query]
    [taoensso.timbre :as timbre :refer [error debug trace warn]]))

(defmethod server-read :datascript/schema
  [{:keys [db db-history]} _ _]
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defmethod server-read :foo
  [{:keys [db db-history]} _ _]
  {:value {:FOO "IS THE SHIT"}})


(def stores
  [{:store/name         "HeadnTail"
    :store/id           0
    :store/review-count 13
    :store/photo        "https://img1.etsystatic.com/122/0/10558959/isla_500x500.21872363_66njj7uo.jpg"
    :store/goods        [{:name    "Vintage 90's Fila Sport USA Sweatshirt Red Black Sport Trainer Sweater"
                          :price   "$34.00"
                          :img-src "https://img0.etsystatic.com/104/0/10558959/il_570xN.1015873526_1oed.jpg"
                          :id "00"}
                         {:name    "Jean Michel Basquiat Pink T Shirt"
                          :price   "$52.00"
                          :img-src "https://img1.etsystatic.com/108/0/10558959/il_570xN.1060411775_k1ob.jpg"
                          :id "01"}
                         {:name    "Vintage Tommy Hilfiger Short Pant Size 34"
                          :price   "$134.00"
                          :img-src "https://img1.etsystatic.com/146/0/10558959/il_570xN.1138598331_iu0t.jpg"
                          :id "02"}
                         {:name    "Majestic Utah Jazz Hardwood Classics White Throwback NBA Basketball Jersey"
                          :price   "$34.00"
                          :img-src "https://img1.etsystatic.com/101/0/10558959/il_570xN.1038331841_gk1f.jpg"
                          :id "03"}]}
   {:store/name         "MagicLinen"
    :store/id           1
    :store/cover "https://img0.etsystatic.com/151/0/11651126/isbl_3360x840.22956500_1bj341c6.jpg"
    :store/photo        "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg"
    :store/review-count 8
    :store/goods        [{:name    "Linen duvet cover. Woodrose colour. Linen bedding. Stonewashed linen duvet cover. Taupe bedding. Linen bedding queen, king, double, twin."
                          :price   "$34.00"
                          :img-src "https://img1.etsystatic.com/141/1/11651126/il_570xN.1142044641_1j6c.jpg"
                          :id "10"}
                         {:name    "Linen pillowcases with ribbons. Bow tie linen pillow cover. Natural linen pillow case. Pillow cases with ties. Romantic pillow cases"
                          :price   "$52.00"
                          :img-src "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg"
                          :id "11"}
                         {:name    "Stone washed linen duvet cover, pleated. White linen bedding. Linen quilt cover. White duvet cover. Luxury, original, modern, handmade"
                          :price   "$134.00"
                          :img-src "https://img0.etsystatic.com/133/0/11651126/il_570xN.915745904_opjr.jpg"
                          :id "12"}
                         {:name    "Linen fitted sheet. Aquamarine colour. Blue linen fitted sheet. Natural bed sheet. Softened. Green blue stone washed linen bed sheet"
                          :price   "$34.00"
                          :img-src "https://img1.etsystatic.com/126/0/11651126/il_570xN.1098073811_5ca0.jpg"
                          :id "13"}]}
   {:store/name         "thislovesthat"
    :store/id           2
    :store/cover "https://img1.etsystatic.com/126/0/6396625/iusb_760x100.17290451_heo8.jpg"
    :store/review-count 43
    :store/photo        "https://img1.etsystatic.com/121/0/6396625/isla_500x500.17289961_hkw1djlp.jpg"
    :store/goods        [{:name    "Glitter & Navy Blue Envelope Clutch"
                          :img-src "https://img1.etsystatic.com/030/0/6396625/il_570xN.635631611_4c3s.jpg"
                          :price   "$34.00"
                          :id "20"}
                         {:name    "Mint Green & Gold Scallop Canvas Clutch"
                          :img-src "https://img0.etsystatic.com/031/0/6396625/il_570xN.581066412_s3ff.jpg"
                          :price   "$52.00"
                          :id "21"}
                         {:name    "Modern Geometric Wood Bead Necklace"
                          :price   "$134.00"
                          :img-src "https://img0.etsystatic.com/045/1/6396625/il_570xN.723123424_ht5e.jpg"
                          :id "22"}
                         {:name    "Modern Wood Teardrop Stud Earrings"
                          :price   "$34.00"
                          :img-src "https://img1.etsystatic.com/033/0/6396625/il_570xN.523107737_juvf.jpg"
                          :id "23"}]}
   {:store/name         "Nafsika"
    :store/cover "https://img1.etsystatic.com/133/0/5243597/isbl_3360x840.20468865_f7kumdbt.jpg"
    :store/review-count 22
    :store/id           3
    :store/photo        "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg"
    :store/goods        [{:name    "Silver Twig Ring Milky Aquamarine Cabochon Light Blue March Birthstone Gifts for her Botanical Jewelry"
                          :img-src "https://img0.etsystatic.com/036/1/5243597/il_570xN.654738182_2k08.jpg"
                          :price   "$34.00"
                          :id "30"}
                         {:name    "Bunny Charm Necklace, Bunny Necklace, Rabbit Necklace, Easter Gift, Child Necklace, Sterling silver, Rabbit Jewelry, Bunny Pendant"
                          :img-src "https://img0.etsystatic.com/024/0/5243597/il_570xN.519102094_4gu0.jpg"
                          :price   "$52.00"
                          :id "31"}
                         {:name    "Red Moss Planter Fall Cube Necklace Sterling Silver Pendant Botanical Jewelry Novelty Pendant"
                          :img-src "https://img0.etsystatic.com/140/1/5243597/il_570xN.964805038_b4eq.jpg"
                          :price   "$134.00"
                          :id "32"}
                         {:name    "Citrine Ring Elvish Twig Ring Branch Ring Thorn Jewelry November Birthstone Gifts for her Fine Jewelry"
                          :img-src "https://img0.etsystatic.com/139/3/5243597/il_570xN.931188156_qhqe.jpg"
                          :price   "$34.00"
                          :id "33"}]}])

(defmethod server-read :query/store
  [{:keys [query params]} _ _]
  (let [{:keys [store-id]} params]
    (prn "Read query/store: " store-id)
    (let [store (some #(when (= (Long/parseLong store-id) (:store/id %))
                        %) stores)]
      {:value (select-keys store query)})))
