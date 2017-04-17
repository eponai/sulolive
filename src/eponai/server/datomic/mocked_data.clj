(ns eponai.server.datomic.mocked-data
  (:require
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug]]))

(def test-user-email "dev@sulo.live")

(defn photo [url]
  {:db/id      (db/tempid :db.part/user)
   :photo/path url})

(defn mock-categories []
  [{:db/id             (db/tempid :db.part/user)
    :category/path     "women"
    :category/label    "Women"
    :category/children [{:db/id          (db/tempid :db.part/user)
                         :category/path  "women-clothing"
                         :category/label "Clothing"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/women-clothing.jpg")}
                        {:db/id          (db/tempid :db.part/user)
                         :category/path  "women-shoes"
                         :category/label "Shoes"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/women-shoes.jpg")}
                        {:db/id          (db/tempid :db.part/user)
                         :category/path  "women-jewelry"
                         :category/label "Jewelry"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/women-jewelry.jpg")}]
    :category/level    0}
   {:db/id          (db/tempid :db.part/user)
    :category/path  "home"
    :category/label "Home"
    :category/level 0}

   {:db/id             (db/tempid :db.part/user)
    :category/path     "men"
    :category/label    "Men"
    :category/children [{:db/id          (db/tempid :db.part/user)
                         :category/path  "men-accessories"
                         :category/label "Accessories"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/men-accessories.jpg")}
                        {:db/id          (db/tempid :db.part/user)
                         :category/path  "men-clothing"
                         :category/label "Clothing"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/men-clothing.jpg")}
                        {:db/id          (db/tempid :db.part/user)
                         :category/path  "men-shoes"
                         :category/label "Shoes"
                         :category/level 1
                         :category/photo (photo "/assets/img/categories/men-shoes.jpg")}]
    :category/level    0}
   {:db/id          (db/tempid :db.part/user)
    :category/path  "kids"
    :category/label "Kids"
    :category/level 0}
   {:db/id          (db/tempid :db.part/user)
    :category/path  "art"
    :category/label "Art"
    :category/level 0}])

(defn mock-stores []
  [
   ;; ikcha
   {:db/id             (db/tempid :db.part/user)
    :store/name        "ikcha"
    :store/stripe      {:stripe/id     "acct_19k3ozC0YaFL9qxh"
                        :stripe/publ   "pk_test_zkG9ip9pZ984hIVoNN1dApoi"
                        :stripe/secret "sk_test_NnEAbrZu6mCMn2Z8Bs8Bvw4T"}
    :store/navigations [{:db/id                  (db/tempid :db.part/user -1000)
                         :store.navigation/path  "earrings"
                         :store.navigation/label "Earrings"}
                        {:db/id                  (db/tempid :db.part/user -1001)
                         :store.navigation/path  "necklaces"
                         :store.navigation/label "Necklaces"}
                        {:db/id                  (db/tempid :db.part/user -1002)
                         :store.navigation/path  "rings"
                         :store.navigation/label "Rings"}]
    :store/photo       (photo "https://img1.etsystatic.com/151/0/6380862/isla_500x500.24111301_nvjpi6zo.jpg")
    :store/items       [{:store.item/name       "Rutilated Quartz & Yellow Citrine Sterling Silver Cocktail Ring - Bohemian"
                         :store.item/price      318.00M
                         :store.item/photos     [(photo "https://img1.etsystatic.com/106/1/6380862/il_570xN.883668651_pp7m.jpg")]
                         :store.item/categories [[:category/path "women"]
                                                 [:category/path "women-jewelry"]]
                         :store.item/navigation (db/tempid :db.part/user -1002)
                         :store.item/uuid       #uuid "58a4b30e-3c8b-49c4-ab08-796c05b4275b"
                         :store.item/skus       [{:store.item.sku/uuid     #uuid "58a4b30e-e33e-442f-b018-a18284604e13"
                                                  :store.item.sku/value    "S"
                                                  :store.item.sku/type     :store.item.sku.type/finite
                                                  :store.item.sku/quantity 321M
                                                  }]}
                        {:store.item/name       "Emerald silver choker"
                         :store.item/price      219.00M
                         :store.item/photos     [(photo "https://img1.etsystatic.com/178/0/6380862/il_570xN.1122315115_m1kt.jpg")]
                         :store.item/categories [[:category/path "women"]
                                                 [:category/path "women-jewelry"]]
                         :store.item/uuid       #uuid "58a4b2b8-4489-4661-9580-c0fe2d132966"
                         :store.item/skus       [{:store.item.sku/uuid     #uuid "58a4b2b8-9c8d-49e1-ab53-8d5c98374f79"
                                                  :store.item.sku/value    "L"
                                                  :store.item.sku/type     :store.item.sku.type/finite
                                                  :store.item.sku/quantity 321M}]
                         :store.item/navigation (db/tempid :db.part/user -1001)}
                        {:store.item/name       "Ear Floral Cuff in Sterling Silver"
                         :store.item/navigation (db/tempid :db.part/user -1000)
                         :store.item/price      68.00M
                         :store.item/photos     [(photo "https://img1.etsystatic.com/124/0/6380862/il_570xN.883522367_34xx.jpg")]
                         :store.item/categories [[:category/path "women"]
                                                 [:category/path "women-jewelry"]]
                         :store.item/uuid       #uuid "58a4b270-fd5d-4cd9-a5ec-ee6c683c679b"
                         :store.item/skus       [{:db/id                   (db/tempid :db.part/user -100)
                                                  :store.item.sku/uuid     #uuid "58a4b270-b918-4007-a9f4-93508411e496"
                                                  :store.item.sku/value    "M"
                                                  :store.item.sku/type     :store.item.sku.type/finite
                                                  :store.item.sku/quantity 32M}]}
                        {:store.item/name       "Sun Stone geometrical Sterling Silver Ring"
                         :store.item/navigation (db/tempid :db.part/user -1002)
                         :store.item/price      211.00M
                         :store.item/photos     [(photo "https://img0.etsystatic.com/130/1/6380862/il_570xN.883902058_swjc.jpg")]
                         :store.item/categories [[:category/path "women"]
                                                 [:category/path "women-jewelry"]]}]
    :store/owners      {:store.owner/user {:db/id       (db/tempid :db.part/user)
                                           :user/email  test-user-email
                                           :user/photo  (photo "https://s3.amazonaws.com/sulo-images/photos/real/5f/ef/5fef55ce7dcc3057db6e4c8f1739fe0d0574a8882611e40c37950fa82f816d40/men.jpg")
                                           :user/name "Diana"
                                           :user/stripe {:stripe/id "cus_A9paOisnJJQ0wS"}
                                           :user/cart   {:db/id      (db/tempid :db.part/user)
                                                         :cart/items [(db/tempid :db.part/user -100)]}}
                        :store.owner/role :store.owner.role/admin}}
   ;; MagicLinen
   {:db/id       (db/tempid :db.part/user)
    :store/name  "MagicLinen"
    :store/cover (photo "https://img0.etsystatic.com/151/0/11651126/isbl_3360x840.22956500_1bj341c6.jpg")
    :store/photo (photo "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg")
    :store/items [{:store.item/name       "Linen duvet cover - Woodrose"
                   :store.item/price      34.00M
                   :store.item/photos     [(photo "https://img1.etsystatic.com/141/1/11651126/il_570xN.1142044641_1j6c.jpg")]
                   :store.item/categories [[:category/path "home"]]}
                  {:store.item/name       "Linen pillowcases with ribbons"
                   :store.item/price      52.00M
                   :store.item/photos     [(photo "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg")]
                   :store.item/categories [[:category/path "home"]]}
                  {:store.item/name       "Stone washed linen duvet cover"
                   :store.item/price      134.00M
                   :store.item/photos     [(photo "https://img0.etsystatic.com/133/0/11651126/il_570xN.915745904_opjr.jpg")]
                   :store.item/categories [[:category/path "home"]]}
                  {:store.item/name       "Linen fitted sheet - Aquamarine"
                   :store.item/price      34.00M
                   :store.item/photos     [(photo "https://img1.etsystatic.com/126/0/11651126/il_570xN.1098073811_5ca0.jpg")]
                   :store.item/categories [[:category/path "home"]]}]}

   ;; thislovesthat
   {:db/id       (db/tempid :db.part/user)
    :store/name  "thislovesthat"
    :store/cover (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/175704-27dcee8b2fd94212b2cc7dcbe43bb80c.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
    :store/photo (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/175704-27dcee8b2fd94212b2cc7dcbe43bb80c.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
    :store/items [{:store.item/name       "Glitter & Navy Blue Envelope Clutch"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/175704-f4b3f5a3acdd4997a3a4ea18186cca19.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      34.00M
                   :store.item/categories [[:category/path "men"]
                                           [:category/path "men-accessories"]]}
                  {:store.item/name       "Mint Green & Gold Scallop Canvas Clutch"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/175704-78f7ed01cfc44fa690640e04ee83a81e.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      52.00M
                   :store.item/categories [[:category/path "men"]
                                           [:category/path "men-accessories"]]}
                  {:store.item/name       "Modern Geometric Wood Bead Necklace"
                   :store.item/price      134.00M
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/175704-bae48bd385d64dc0bb6ebad3190cc317.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/categories [[:category/path "men"]
                                           [:category/path "men-accessories"]]}
                  {:store.item/name       "Modern Wood Teardrop Stud Earrings"
                   :store.item/price      34.00M
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/175704-ba70e3b49b0f4a9084ce14f569d1cf60.jpeg?q=50&w=1000&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/categories [[:category/path "men"]
                                           [:category/path "men-accessories"]]}]}

   ;; Nafsika
   {:db/id       (db/tempid :db.part/user)
    :store/name  "Nafsika"
    :store/cover (photo "https://img1.etsystatic.com/133/0/5243597/isbl_3360x840.20468865_f7kumdbt.jpg")
    :store/photo (photo "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg")
    :store/items [{:store.item/name       "Silver Twig Ring Milky"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/164/1/7745893/il_570xN.1094898766_ewls.jpg")
                                           (photo "https://img0.etsystatic.com/156/0/7745893/il_570xN.1094898750_jnvm.jpg")]
                   :store.item/price      34.00M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Bunny Charm Necklace"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/121/1/7745893/il_570xN.1116392641_6zg2.jpg")]
                   :store.item/price      52.00M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Red Moss Planter Fall Cube Necklace"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/127/0/7745893/il_570xN.988546292_nvbz.jpg")]
                   :store.item/price      134.00M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Elvish Twig Ring"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/123/1/7745893/il_570xN.987968604_8ix5.jpg")]
                   :store.item/price      34.00M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}]}

   ;; FlowerRainbowNJ
   {:db/id       (db/tempid :db.part/user)
    :store/name  "FlowerRainbowNJ"
    :store/tagline "Keep calm and wear pretty jewelry."
    :store/cover (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/449892-2b1249e4cb424d5a937a0f67fb22ccc0.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
    :store/photo (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/449892-2b1249e4cb424d5a937a0f67fb22ccc0.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
    :store/items [{:store.item/name       "Nose Stud"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/449892-c7eed40ca74a4ed7abc555640c0936ad.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      24.74M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Tragus Earring"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/449892-7340ea71653e4b53a9057de4f64c1018.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      4.49M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Nose Ring"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/449892-4603b04cdd4e4a41b281a4aff4a39fe0.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      6.37M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Nose Ring"
                   :store.item/photos     [(photo "https://imgix.ttcdn.co/i/product/original/0/449892-18406d9dfa7e449e8d36627c088c92c1.png?q=50&w=1000&auto=format%2Ccompress&fm=jpeg")]
                   :store.item/price      6.74M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}]}

   ;; BangiShop
   {:db/id       (db/tempid :db.part/user)
    :store/name  "BangiShop"
    :store/cover (photo "https://img1.etsystatic.com/142/0/8829348/isbl_3360x840.24031443_roffucs6.jpg")
    :store/photo (photo "https://img1.etsystatic.com/136/0/8829348/isla_500x500.18128391_dro0qzqd.jpg")
    :store/items [{:store.item/name       "Leather Shoes (silver)"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/138/1/8829348/il_570xN.1040522475_mbon.jpg")
                                           (photo "https://img0.etsystatic.com/133/0/8829348/il_570xN.993989824_3pdl.jpg")]
                   :store.item/price      24.74M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-shoes"]]}
                  {:store.item/name       "Leather Shoes (yellow)"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/120/0/8829348/il_570xN.988317879_5pik.jpg")
                                           (photo "https://img1.etsystatic.com/125/0/8829348/il_570xN.988317889_kzc9.jpg")]
                   :store.item/price      4.49M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-shoes"]]}
                  ;{:store.item/name       "Leather Shoes (green)"
                  ; :store.item/photos     [(photo "https://img1.etsystatic.com/032/0/8829348/il_570xN.636027815_eg26.jpg")
                  ;                         (photo "https://img1.etsystatic.com/028/0/8829348/il_570xN.636027807_ozll.jpg")]
                  ; :store.item/price      4.49M
                  ; :store.item/categories [[:category/path "women"]
                  ;                         [:category/path "women-shoes"]]}
                  {:store.item/name       "Leather Boots"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/172/4/8829348/il_570xN.1104988862_cb12.jpg")]
                   :store.item/price      6.37M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-shoes"]]}]}

   ;; MIRIMIRIFASHION
   {:db/id       (db/tempid :db.part/user)
    :store/name  "MIRIMIRIFASHION"
    :store/tagline "Handmade exclusive fashion designer shop."
    ;:store/cover #db/id[:db.part/user -51]
    :store/photo (photo "https://img0.etsystatic.com/132/0/5695768/isla_500x500.17344782_h4dngp5g.jpg")
    :store/items [{:store.item/name       "Hoodie Dress"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/109/1/5695768/il_570xN.1088263217_thkk.jpg")
                                           (photo "https://img0.etsystatic.com/119/0/5695768/il_570xN.1041709156_noxy.jpg")
                                           (photo "https://img0.etsystatic.com/108/0/5695768/il_570xN.1041709214_ae4i.jpg")]
                   :store.item/price      24.74M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-clothing"]]}
                  {:store.item/name       "Maxi skirt"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/000/0/5695768/il_570xN.272372530.jpg")
                                           (photo "https://img0.etsystatic.com/000/0/5695768/il_570xN.272372548.jpg")]
                   :store.item/price      4.49M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-clothing"]]}
                  {:store.item/name       "Leather Boots"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/136/1/5695768/il_570xN.1087733031_du1y.jpg")
                                           (photo "https://img1.etsystatic.com/125/0/5695768/il_570xN.1087733249_hz9c.jpg")]
                   :store.item/price      6.37M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-clothing"]]}]}

   ;; RecycledBeautifully
   {:db/id       (db/tempid :db.part/user)
    :store/name  "RecycledBeautifully"
    :store/cover (photo "https://img0.etsystatic.com/130/0/7946526/isbl_3360x840.18460378_4d4b1gyn.jpg")
    :store/photo (photo "https://img1.etsystatic.com/142/0/7946526/isla_500x500.23870003_5l3vsjlx.jpg")
    :store/items [{:store.item/name       "Tree of Life wire"
                   :store.item/photos     [(photo "https://img1.etsystatic.com/059/2/7946526/il_570xN.728670429_e1dd.jpg")]
                   :store.item/price      24.74M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Tree of Life copper"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/142/1/7946526/il_570xN.1094904882_t58t.jpg")]
                   :store.item/price      42.49M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}
                  {:store.item/name       "Tree of Life wire"
                   :store.item/photos     [(photo "https://img0.etsystatic.com/166/1/7946526/il_570xN.1074937810_dh62.jpg")]
                   :store.item/price      64.37M
                   :store.item/categories [[:category/path "women"]
                                           [:category/path "women-jewelry"]]}]}
   ])

(defn mock-chats [stores]
  (vec (map (fn [s]
              {:chat/store (:db/id s)})
            stores)))

(defn mock-streams [stores]
  (vec (map-indexed
         (fn [i s]
           {:stream/title (str "Stream " i)
            :stream/state :stream.state/offline
            :stream/store (:db/id s)})
         stores)))


(defn add-data [conn]
  (let [categories (mock-categories)
        stores (mock-stores)
        chats (mock-chats stores)
        streams (mock-streams (take 4 stores))]
    (db/transact conn categories)
    (debug "Categories added")
    (db/transact conn (concat stores streams chats))
    (debug "Stores with items, chats and streams added")))