(ns eponai.server.datomic.mocked-data
  (:require
    [eponai.common.database :as db]
    [clojure.string :as str]
    [taoensso.timbre :refer [debug]]
    [medley.core :as medley]
    [clojure.walk :as walk]))

(defn missing-personal-id-account []
  {:stripe/id     "acct_19k3ozC0YaFL9qxh"
   :stripe/publ   "pk_test_zkG9ip9pZ984hIVoNN1dApoi"
   :stripe/secret "sk_test_NnEAbrZu6mCMn2Z8Bs8Bvw4T"})

(defn no-details-account []
  {:stripe/id     "acct_1A9udfFhS10xugIf"
   :stripe/publ   "pk_test_c5VS6ZK5mHacWIylwV9HpNwh"
   :stripe/secret "sk_test_ISVuDM5UfrfTLUqV2HNvspLf"})

(defn stripe-account []
  (missing-personal-id-account))

(def test-user-email "dev@sulo.live")

(defn photo [url]
  {:db/id      (db/tempid :db.part/user)
   :photo/path url})

(defn sku [& [v]]
  (cond-> {:store.item.sku/inventory {:store.item.sku.inventory/type  :store.item.inventory.type/bucket
                                      :store.item.sku.inventory/value :store.item.inventory.value/in-stock}}
          (some? v)
          (assoc :store.item.sku/variation v)))

(defn item-photo [url & [i]]
  {:db/id                  (db/tempid :db.part/user)
   :store.item.photo/photo (photo url)
   :store.item.photo/index (or i 0)})

(defn kids-category [label {:keys [unisex-kids unisex-fn boys-fn girls-fn]
                            :or   {unisex-fn vals
                                   boys-fn   vals
                                   girls-fn  vals}}]
  [#:category {:path     "unisex-kids"
               :label    (str "Unisex Kids' " label)
               :children (unisex-fn unisex-kids)}
   #:category {:path     "boys"
               :label    (str "Boy's " label)
               :children (boys-fn unisex-kids)}
   #:category {:path     "girls"
               :label    (str "Girl's " label)
               :children (girls-fn unisex-kids)}])

(defn adult-category [label {:keys [unisex-adult unisex-fn men-fn women-fn]
                             :or   {unisex-fn identity
                                    men-fn    identity
                                    women-fn  identity}}]
  [#:category {:path     "unisex-adult"
               :label    (str "Unisex Adult " label)
               :children (vals (unisex-fn unisex-adult))}
   #:category {:path     "men"
               :label    (str "Men's " label)
               :children (vals (men-fn unisex-adult))}
   #:category {:path     "women"
               :label    (str "Women's " label)
               :children (vals (women-fn unisex-adult))}])

(def category-path-separator "_")
(def category-name-separator "-")

(defn leaf [& name-parts]
  #:category {:path  (str/join category-name-separator name-parts)
              :label (str/capitalize (str/join " " name-parts))})

(defn hash-map-by [f coll]
  (into {} (map (juxt f identity)) coll))

(def cats
  [#:category {:path     "clothing"
               :label    "Clothing"
               :children (fn []
                           (adult-category "Clothing" {:unisex-adult {"pants" (leaf "pants")}
                                                       :women-fn     #(-> %
                                                                          (assoc "skirts" (leaf "skirts"))
                                                                          (assoc "dresses" (leaf "dresses")))}))}
   #:category {:path     "shoes"
               :label    "Shoes"
               :children (fn []
                           (adult-category "Shoes" {:unisex-adult {"boots" (leaf "boots")}}))}
   #:category {:path     "jewelry"
               :label    "Jewelry"
               :children (fn []
                           (adult-category "Jewelry" {:unisex-adult (hash-map-by :category/path
                                                                                 [(leaf "earrings")
                                                                                  (leaf "rings")
                                                                                  (leaf "necklaces")])}))}
   #:category {:path  "home"
               :label "Home"}
   #:category {:path     "accessories"
               :label    "Accessories"
               :children (fn []
                           (let [unisex-cats (hash-map-by :category/path
                                                          [(leaf "belt")
                                                           (leaf "caps")
                                                           (leaf "clothing" "accessories")
                                                           (leaf "eyewear")
                                                           (leaf "gloves")
                                                           (leaf "hats")
                                                           (leaf "keychains")
                                                           (leaf "outdoor" "wear" "accessories")
                                                           (leaf "patches")
                                                           (leaf "rain" "accessories")
                                                           (leaf "scarves")
                                                           (leaf "shoe" "accessories")
                                                           (leaf "special" "occasion" "accessories")
                                                           (leaf "sunglasses")
                                                           (leaf "tech" "accessories")
                                                           (leaf "umbrellas")
                                                           (leaf "watches")])]
                             (into
                               [#:category {:path     "childrens"
                                            :label    "Children's Accessories"
                                            :children (-> unisex-cats
                                                          (assoc "socks" (leaf "socks"))
                                                          (vals))}]
                               (adult-category "Accessories" {:unisex-adult unisex-cats
                                                              :men-fn       #(assoc % "socks" (leaf "socks"))
                                                              :women-fn     #(-> %
                                                                                 (assoc "hair-acc" (leaf "hair" "accessories"))
                                                                                 (assoc "handbag" (leaf "handbag" "accessories"))
                                                                                 (assoc "socks" (leaf "socks"))
                                                                                 (assoc "wallets" (leaf "wallets")))}))))}])

(defn category-path [& path-parts]
  (str/join category-path-separator path-parts))

(defn mock-categories3 []
  (letfn [(join-children-paths [category path]
            (when (vector? category)
              (debug "Got vector for category: " category " path: " path))
            (let [new-path (str/join category-path-separator (filter some? [path (:category/path category)]))]
              (cond-> (assoc category :category/path new-path)
                      (some? (:category/children category))
                      (update :category/children (fn [children]
                                                   (->> (if (fn? children) (children) children)
                                                        (into [] (map #(join-children-paths % new-path)))))))))]
    (->> cats
         (into [] (map #(join-children-paths % nil)))
         ;; (walk/postwalk #(cond-> % (map? %) (assoc :db/id (db/tempid :db.part/user))))
         )))

(defn mock-stores []
  [
   ;; ikcha
   {:db/id          (db/tempid :db.part/user)
    :store/profile  {:store.profile/name  "ikcha"
                     :store.profile/photo (photo "https://img1.etsystatic.com/151/0/6380862/isla_500x500.24111301_nvjpi6zo.jpg")}
    :store/stripe   (stripe-account)
    :store/sections [{:db/id               (db/tempid :db.part/user -1000)
                      :store.section/path  "earrings"
                      :store.section/label "Earrings"}
                     {:db/id               (db/tempid :db.part/user -1001)
                      :store.section/path  "necklaces"
                      :store.section/label "Necklaces"}
                     {:db/id               (db/tempid :db.part/user -1002)
                      :store.section/path  "rings"
                      :store.section/label "Rings"}]
    :store/items    [{:store.item/name     "Rutilated Quartz & Yellow Citrine Sterling Silver Cocktail Ring - Bohemian"
                      :store.item/price    318.00M
                      :store.item/photos   [(item-photo "https://img1.etsystatic.com/106/1/6380862/il_570xN.883668651_pp7m.jpg")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                      :store.item/section  (db/tempid :db.part/user -1002)
                      :store.item/uuid     #uuid "58a4b30e-3c8b-49c4-ab08-796c05b4275b"
                      :store.item/skus     [(sku "S")
                                            (sku "M")
                                            (sku "L")]}
                     {:store.item/name     "Emerald silver choker"
                      :store.item/price    219.00M
                      :store.item/photos   [(item-photo "https://img1.etsystatic.com/178/0/6380862/il_570xN.1122315115_m1kt.jpg")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                      :store.item/uuid     #uuid "58a4b2b8-4489-4661-9580-c0fe2d132966"
                      :store.item/skus     [(sku)]
                      :store.item/section  (db/tempid :db.part/user -1001)}
                     {:store.item/name     "Ear Floral Cuff in Sterling Silver"
                      :store.item/section  (db/tempid :db.part/user -1000)
                      :store.item/price    68.00M
                      :store.item/photos   [(item-photo "https://img1.etsystatic.com/124/0/6380862/il_570xN.883522367_34xx.jpg")]
                      :store.item/category [:category/path (category-path "jewelry" "women")]
                      :store.item/uuid     #uuid "58a4b270-fd5d-4cd9-a5ec-ee6c683c679b"
                      :store.item/skus     [(sku "M")]}
                     {:store.item/name     "Sun Stone geometrical Sterling Silver Ring"
                      :store.item/section  (db/tempid :db.part/user -1002)
                      :store.item/price    211.00M
                      :store.item/photos   [(item-photo "https://img0.etsystatic.com/130/1/6380862/il_570xN.883902058_swjc.jpg")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "rings")]}]
    :store/owners   {:store.owner/user {:db/id        (db/tempid :db.part/user)
                                        :user/email   test-user-email
                                        :user/profile {:user.profile/photo (photo "https://s3.amazonaws.com/sulo-images/photos/real/5f/ef/5fef55ce7dcc3057db6e4c8f1739fe0d0574a8882611e40c37950fa82f816d40/men.jpg")
                                                       :user.profile/name  "Diana"}
                                        :user/stripe  {:stripe/id "cus_A9paOisnJJQ0wS"}}
                     :store.owner/role :store.owner.role/admin}}
   ;; MagicLinen
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "MagicLinen"
                    :store.profile/cover (photo "https://img0.etsystatic.com/151/0/11651126/isbl_3360x840.22956500_1bj341c6.jpg")
                    :store.profile/photo (photo "https://img0.etsystatic.com/125/0/11651126/isla_500x500.17338368_6u0a6c4s.jpg")}
    :store/items   [{:store.item/name     "Linen duvet cover - Woodrose"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "https://img1.etsystatic.com/141/1/11651126/il_570xN.1142044641_1j6c.jpg")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Linen pillowcases with ribbons"
                     :store.item/price    52.00M
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/137/0/11651126/il_570xN.1003284712_ip5e.jpg")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Stone washed linen duvet cover"
                     :store.item/price    134.00M
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/133/0/11651126/il_570xN.915745904_opjr.jpg")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Linen fitted sheet - Aquamarine"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "https://img1.etsystatic.com/126/0/11651126/il_570xN.1098073811_5ca0.jpg")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}]}

   ;; thislovesthat
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "thislovesthat"
                    :store.profile/cover (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/175704-27dcee8b2fd94212b2cc7dcbe43bb80c.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
                    :store.profile/photo (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/175704-27dcee8b2fd94212b2cc7dcbe43bb80c.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")}
    :store/items   [{:store.item/name     "Glitter & Navy Blue Envelope Clutch"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/175704-f4b3f5a3acdd4997a3a4ea18186cca19.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "accessories" "men")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Mint Green & Gold Scallop Canvas Clutch"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/175704-78f7ed01cfc44fa690640e04ee83a81e.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    52.00M
                     :store.item/category [:category/path (category-path "accessories" "men")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Modern Geometric Wood Bead Necklace"
                     :store.item/price    134.00M
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/175704-bae48bd385d64dc0bb6ebad3190cc317.jpeg?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/category [:category/path (category-path "jewelry" "men" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Modern Wood Teardrop Stud Earrings"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/175704-ba70e3b49b0f4a9084ce14f569d1cf60.jpeg?q=50&w=1000&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/category [:category/path (category-path "jewelry" "men" "earrings")]
                     :store.item/skus     [(sku)]}]}

   ;; Nafsika
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "Nafsika"
                    :store.profile/cover (photo "https://img1.etsystatic.com/133/0/5243597/isbl_3360x840.20468865_f7kumdbt.jpg")
                    :store.profile/photo (photo "https://img0.etsystatic.com/139/0/5243597/isla_500x500.22177516_ath1ugrh.jpg")}
    :store/items   [{:store.item/name     "Silver Twig Ring Milky"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img0.etsystatic.com/164/1/7745893/il_570xN.1094898766_ewls.jpg"
                                                                            "https://img0.etsystatic.com/156/0/7745893/il_570xN.1094898750_jnvm.jpg"])
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Bunny Charm Necklace"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img1.etsystatic.com/121/1/7745893/il_570xN.1116392641_6zg2.jpg"])
                     :store.item/price    52.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Red Moss Planter Fall Cube Necklace"
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/127/0/7745893/il_570xN.988546292_nvbz.jpg")]
                     :store.item/price    134.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Elvish Twig Ring"
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/123/1/7745893/il_570xN.987968604_8ix5.jpg")]
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}]}

   ;; FlowerRainbowNJ
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name    "FlowerRainbowNJ"
                    :store.profile/tagline "Keep calm and wear pretty jewelry."
                    :store.profile/cover   (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/449892-2b1249e4cb424d5a937a0f67fb22ccc0.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")
                    :store.profile/photo   (photo "https://imgix.ttcdn.co/i/wallpaper/original/0/449892-2b1249e4cb424d5a937a0f67fb22ccc0.jpeg?q=50&w=2000&auto=format%2Ccompress&fm=jpeg&h=1333&crop=faces%2Centropy&fit=crop")}
    :store/items   [{:store.item/name     "Nose Stud"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/449892-c7eed40ca74a4ed7abc555640c0936ad.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "jewelry" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tragus Earring"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/449892-7340ea71653e4b53a9057de4f64c1018.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "jewelry" "women" "earrings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Nose Ring"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/449892-4603b04cdd4e4a41b281a4aff4a39fe0.png?q=50&w=640&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Nose Ring"
                     :store.item/photos   [(item-photo "https://imgix.ttcdn.co/i/product/original/0/449892-18406d9dfa7e449e8d36627c088c92c1.png?q=50&w=1000&auto=format%2Ccompress&fm=jpeg")]
                     :store.item/price    6.74M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}]}

   ;; BangiShop
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "BangiShop"
                    :store.profile/cover (photo "https://img1.etsystatic.com/142/0/8829348/isbl_3360x840.24031443_roffucs6.jpg")
                    :store.profile/photo (photo "https://img1.etsystatic.com/136/0/8829348/isla_500x500.18128391_dro0qzqd.jpg")}
    :store/stripe  {:stripe/id "acct_19jze1BbOp8CGZPS"}
    :store/items   [{:store.item/name     "Leather Shoes (silver)"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img1.etsystatic.com/138/1/8829348/il_570xN.1040522475_mbon.jpg"
                                                                            "https://img0.etsystatic.com/133/0/8829348/il_570xN.993989824_3pdl.jpg"])
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "shoes" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Shoes (yellow)"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img1.etsystatic.com/120/0/8829348/il_570xN.988317879_5pik.jpg"
                                                                            "https://img1.etsystatic.com/125/0/8829348/il_570xN.988317889_kzc9.jpg"])
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "shoes" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Boots"
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/172/4/8829348/il_570xN.1104988862_cb12.jpg")]
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "shoes" "women" "boots")]
                     :store.item/skus     [(sku)]}]}

   ;; MIRIMIRIFASHION
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name    "MIRIMIRIFASHION"
                    :store.profile/tagline "Handmade exclusive fashion designer shop."
                    ;:store/cover #db/id[:db.part/user -51]
                    :store.profile/photo   (photo "https://img0.etsystatic.com/132/0/5695768/isla_500x500.17344782_h4dngp5g.jpg")}
    :store/items   [{:store.item/name     "Hoodie Dress"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img1.etsystatic.com/109/1/5695768/il_570xN.1088263217_thkk.jpg"
                                                                            "https://img0.etsystatic.com/119/0/5695768/il_570xN.1041709156_noxy.jpg"
                                                                            "https://img0.etsystatic.com/108/0/5695768/il_570xN.1041709214_ae4i.jpg"])
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "clothing" "women" "dresses")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Maxi skirt"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img0.etsystatic.com/000/0/5695768/il_570xN.272372530.jpg"
                                                                            "https://img0.etsystatic.com/000/0/5695768/il_570xN.272372548.jpg"])
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "clothing" "women" "skirts")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Boots"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["https://img1.etsystatic.com/136/1/5695768/il_570xN.1087733031_du1y.jpg"
                                                                            "https://img1.etsystatic.com/125/0/5695768/il_570xN.1087733249_hz9c.jpg"])
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "shoes" "women" "boots")]
                     :store.item/skus     [(sku)]}]}

   ;; RecycledBeautifully
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "RecycledBeautifully"
                    :store.profile/cover (photo "https://img0.etsystatic.com/130/0/7946526/isbl_3360x840.18460378_4d4b1gyn.jpg")
                    :store.profile/photo (photo "https://img1.etsystatic.com/142/0/7946526/isla_500x500.23870003_5l3vsjlx.jpg")}
    :store/items   [{:store.item/name     "Tree of Life wire"
                     :store.item/photos   [(item-photo "https://img1.etsystatic.com/059/2/7946526/il_570xN.728670429_e1dd.jpg")]
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tree of Life copper"
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/142/1/7946526/il_570xN.1094904882_t58t.jpg")]
                     :store.item/price    42.49M
                     :store.item/category [:category/path (category-path "jewelry" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tree of Life wire"
                     :store.item/photos   [(item-photo "https://img0.etsystatic.com/166/1/7946526/il_570xN.1074937810_dh62.jpg")]
                     :store.item/price    64.37M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}]}
   ])

(defn mock-chats [stores]
  (vec (map (fn [s]
              {:chat/store (:db/id s)})
            stores)))

(defn mock-streams [stores state]
  (vec (map-indexed
         (fn [i s]
           {:stream/title (str "Stream " i)
            :stream/state state
            :stream/store (:db/id s)})
         stores)))

(defn user-no-store []
  {:db/id        (db/tempid :db.part/user)
   :user/email   "dev+nostore@sulo.live"
   :user/profile {:user.profile/photo (photo "/assets/img/categories/women-clothing.jpg")
                  :user.profile/name  "Storeless"}
   :user/stripe  {:stripe/id "cus_AT7bKjMaCIWpei"}})

(defn add-data [conn]
  (let [categories (mock-categories3)
        stores (mock-stores)
        chats (mock-chats stores)
        live-streams (mock-streams (take 4 stores) :stream.state/offline)
        streams (mock-streams (drop 4 stores) :stream.state/offline)
        storeless-user (user-no-store)]
    (db/transact conn categories)
    (db/transact-one conn storeless-user)
    (debug "Categories added")
    (db/transact conn (concat stores live-streams streams chats))
    (debug "Stores with items, chats and streams added")))