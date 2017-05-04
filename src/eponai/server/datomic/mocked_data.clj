(ns eponai.server.datomic.mocked-data
  (:require
    [eponai.common.database :as db]
    [eponai.common.api.products :as products]
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

(defn photo [id]
  {:db/id      (db/tempid :db.part/user)
   :photo/id id
   :photo/path (str "http://res.cloudinary.com/sulolive/image/upload/" id ".jpg")})
(defn sku [& [v]]
  (cond-> {:store.item.sku/inventory {:store.item.sku.inventory/type  :store.item.inventory.type/bucket
                                      :store.item.sku.inventory/value :store.item.inventory.value/in-stock}}
          (some? v)
          (assoc :store.item.sku/variation v)))

(defn item-photo [id & [i]]
  {:db/id                  (db/tempid :db.part/user)
   :store.item.photo/photo (photo id)
   :store.item.photo/index (or i 0)})

(defn kids-category [label {:keys [unisex-kids unisex-fn boys-fn girls-fn]
                            :or   {unisex-fn vals
                                   boys-fn   vals
                                   girls-fn  vals}}]
  [#:category {:name     "unisex-kids"
               :label    (str "Unisex Kids' " label)
               :children (unisex-fn unisex-kids)}
   #:category {:name     "boys"
               :label    (str "Boy's " label)
               :children (boys-fn unisex-kids)}
   #:category {:name     "girls"
               :label    (str "Girl's " label)
               :children (girls-fn unisex-kids)}])

(defn adult-category [label {:keys [unisex-adult unisex-fn men-fn women-fn]
                             :or   {unisex-fn identity
                                    men-fn    identity
                                    women-fn  identity}}]
  [#:category {:name     "unisex-adult"
               :label    (str "Unisex Adult " label)
               :children (vals (unisex-fn unisex-adult))}
   #:category {:name     "men"
               :label    (str "Men's " label)
               :children (vals (men-fn unisex-adult))}
   #:category {:name     "women"
               :label    (str "Women's " label)
               :children (vals (women-fn unisex-adult))}])

(defn leaf [& name-parts]
  #:category{:name  (str/join products/category-name-separator name-parts)
             :label (str/capitalize (str/join " " name-parts))})

(defn hash-map-by [f coll]
  (into {} (map (juxt f identity)) coll))

(def cats
  [#:category{:name     "clothing"
              :label    "Clothing"
              :children (fn []
                          (adult-category "Clothing" {:unisex-adult {"pants" (leaf "pants")}
                                                      :women-fn     #(-> %
                                                                         (assoc "skirts" (leaf "skirts"))
                                                                         (assoc "dresses" (leaf "dresses")))}))}
   #:category{:name     "shoes"
              :label    "Shoes"
              :children (fn []
                          (adult-category "Shoes" {:unisex-adult {"boots" (leaf "boots")}}))}
   #:category{:name     "jewelry"
              :label    "Jewelry"
              :children (fn []
                          (adult-category "Jewelry" {:unisex-adult (hash-map-by :category/name
                                                                                [(leaf "earrings")
                                                                                 (leaf "rings")
                                                                                 (leaf "necklaces")])}))}
   #:category{:name  "home"
              :label "Home"}
   #:category{:name     "accessories"
              :label    "Accessories"
              :children (fn []
                          (let [unisex-cats (hash-map-by :category/name
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
                              [#:category{:name     "childrens"
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
  (str/join products/category-path-separator path-parts))

(defn mock-categories3 []
  (letfn [(category-path-from-names [category path]
            (let [new-path (->> [path (:category/name category)]
                                (filter some?)
                                (str/join products/category-path-separator))]
              (cond-> (assoc category :category/path new-path)
                      (some? (:category/children category))
                      (update :category/children (fn [children]
                                                   (->> (if (fn? children) (children) children)
                                                        (into [] (map #(category-path-from-names % new-path)))))))))]
    (->> cats
         (into [] (map #(category-path-from-names % nil)))
         ;; (walk/postwalk #(cond-> % (map? %) (assoc :db/id (db/tempid :db.part/user))))
         )))

(defn mock-stores []
  [
   ;; ikcha
   {:db/id          (db/tempid :db.part/user)
    :store/profile  {:store.profile/name  "ikcha"
                     :store.profile/photo (photo "mocked/isla_500x500.24111301_nvjpi6zo")
                     :store.profile/cover (photo "mocked/isbl_3360x840.20468865_f7kumdbt")}
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
                      :store.item/photos   [(item-photo "mocked/il_570xN.883668651_pp7m")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                      :store.item/section  (db/tempid :db.part/user -1002)
                      :store.item/uuid     #uuid "58a4b30e-3c8b-49c4-ab08-796c05b4275b"
                      :store.item/skus     [(sku "S")
                                            (sku "M")
                                            (sku "L")]}
                     {:store.item/name     "Emerald silver choker"
                      :store.item/price    219.00M
                      :store.item/photos   [(item-photo "mocked/il_570xN.1122315115_m1kt")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                      :store.item/uuid     #uuid "58a4b2b8-4489-4661-9580-c0fe2d132966"
                      :store.item/skus     [(sku)]
                      :store.item/section  (db/tempid :db.part/user -1001)}
                     {:store.item/name     "Ear Floral Cuff in Sterling Silver"
                      :store.item/section  (db/tempid :db.part/user -1000)
                      :store.item/price    68.00M
                      :store.item/photos   [(item-photo "mocked/il_570xN.883522367_34xx")]
                      :store.item/category [:category/path (category-path "jewelry" "women")]
                      :store.item/uuid     #uuid "58a4b270-fd5d-4cd9-a5ec-ee6c683c679b"
                      :store.item/skus     [(sku "M")]}
                     {:store.item/name     "Sun Stone geometrical Sterling Silver Ring"
                      :store.item/section  (db/tempid :db.part/user -1002)
                      :store.item/price    211.00M
                      :store.item/photos   [(item-photo "mocked/il_570xN.883902058_swjc")]
                      :store.item/category [:category/path (category-path "jewelry" "women" "rings")]}]
    :store/owners   {:store.owner/user {:db/id        (db/tempid :db.part/user)
                                        :user/email   test-user-email
                                        :user/profile {:user.profile/photo (photo "static/men")
                                                       :user.profile/name  "Diana"}
                                        :user/stripe  {:stripe/id "cus_A9paOisnJJQ0wS"}}
                     :store.owner/role :store.owner.role/admin}}
   ;; MagicLinen
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "MagicLinen"
                    :store.profile/cover (photo "mocked/isbl_3360x840.22956500_1bj341c6")
                    :store.profile/photo (photo "mocked/isla_500x500.17338368_6u0a6c4s")}
    :store/items   [{:store.item/name     "Linen duvet cover - Woodrose"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "mocked/il_570xN.1142044641_1j6c")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Linen pillowcases with ribbons"
                     :store.item/price    52.00M
                     :store.item/photos   [(item-photo "mocked/il_570xN.1003284712_ip5e")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Stone washed linen duvet cover"
                     :store.item/price    134.00M
                     :store.item/photos   [(item-photo "mocked/il_570xN.915745904_opjr")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Linen fitted sheet - Aquamarine"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "mocked/il_570xN.1098073811_5ca0")]
                     :store.item/category [:category/path "home"]
                     :store.item/skus     [(sku)]}]}

   ;; thislovesthat
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "thislovesthat"
                    :store.profile/cover (photo "mocked/175704-27dcee8b2fd94212b2cc7dcbe43bb80c")
                    :store.profile/photo (photo "mocked/175704-27dcee8b2fd94212b2cc7dcbe43bb80c")}
    :store/items   [{:store.item/name     "Glitter & Navy Blue Envelope Clutch"
                     :store.item/photos   [(item-photo "mocked/175704-f4b3f5a3acdd4997a3a4ea18186cca19")]
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "accessories" "men")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Mint Green & Gold Scallop Canvas Clutch"
                     :store.item/photos   [(item-photo "mocked/175704-78f7ed01cfc44fa690640e04ee83a81e")]
                     :store.item/price    52.00M
                     :store.item/category [:category/path (category-path "accessories" "men")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Modern Geometric Wood Bead Necklace"
                     :store.item/price    134.00M
                     :store.item/photos   [(item-photo "mocked/175704-bae48bd385d64dc0bb6ebad3190cc317")]
                     :store.item/category [:category/path (category-path "jewelry" "men" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Modern Wood Teardrop Stud Earrings"
                     :store.item/price    34.00M
                     :store.item/photos   [(item-photo "mocked/175704-ba70e3b49b0f4a9084ce14f569d1cf60")]
                     :store.item/category [:category/path (category-path "jewelry" "men" "earrings")]
                     :store.item/skus     [(sku)]}]}

   ;; Nafsika
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "Nafsika"

                    :store.profile/photo (photo "mocked/isla_500x500.22177516_ath1ugrh")}
    :store/items   [{:store.item/name     "Silver Twig Ring Milky"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.1094898766_ewls"
                                                                            "mocked/il_570xN.1094898750_jnvm"])
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Bunny Charm Necklace"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.1116392641_6zg2"])
                     :store.item/price    52.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Red Moss Planter Fall Cube Necklace"
                     :store.item/photos   [(item-photo "mocked/il_570xN.988546292_nvbz")]
                     :store.item/price    134.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Elvish Twig Ring"
                     :store.item/photos   [(item-photo "mocked/il_570xN.987968604_8ix5")]
                     :store.item/price    34.00M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}]}

   ;; FlowerRainbowNJ
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name    "FlowerRainbowNJ"
                    :store.profile/tagline "Keep calm and wear pretty jewelry."
                    :store.profile/cover   (photo "mocked/449892-2b1249e4cb424d5a937a0f67fb22ccc0")
                    :store.profile/photo   (photo "mocked/449892-2b1249e4cb424d5a937a0f67fb22ccc0")}
    :store/items   [{:store.item/name     "Nose Stud"
                     :store.item/photos   [(item-photo "mocked/449892-c7eed40ca74a4ed7abc555640c0936ad")]
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "jewelry" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tragus Earring"
                     :store.item/photos   [(item-photo "mocked/449892-7340ea71653e4b53a9057de4f64c1018")]
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "jewelry" "women" "earrings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Nose Ring"
                     :store.item/photos   [(item-photo "mocked/449892-4603b04cdd4e4a41b281a4aff4a39fe0")]
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Nose Ring"
                     :store.item/photos   [(item-photo "mocked/449892-18406d9dfa7e449e8d36627c088c92c1")]
                     :store.item/price    6.74M
                     :store.item/category [:category/path (category-path "jewelry" "women" "rings")]
                     :store.item/skus     [(sku)]}]}

   ;; BangiShop
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "BangiShop"
                    :store.profile/cover (photo "mocked/isbl_3360x840.24031443_roffucs6")
                    :store.profile/photo (photo "mocked/isla_500x500.18128391_dro0qzqd")}
    :store/stripe  {:stripe/id "acct_19jze1BbOp8CGZPS"}
    :store/items   [{:store.item/name     "Leather Shoes (silver)"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.1040522475_mbon"
                                                                            "mocked/il_570xN.993989824_3pdl"])
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "shoes" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Shoes (yellow)"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.988317879_5pik"
                                                                            "mocked/il_570xN.988317889_kzc9"])
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "shoes" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Boots"
                     :store.item/photos   [(item-photo "mocked/il_570xN.1104988862_cb12")]
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "shoes" "women" "boots")]
                     :store.item/skus     [(sku)]}]}

   ;; MIRIMIRIFASHION
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name    "MIRIMIRIFASHION"
                    :store.profile/tagline "Handmade exclusive fashion designer shop."
                    ;:store/cover #db/id[:db.part/user -51]
                    :store.profile/photo   (photo "mocked/isla_500x500.17344782_h4dngp5g")}
    :store/items   [{:store.item/name     "Hoodie Dress"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.1088263217_thkk"
                                                                            "mocked/il_570xN.1041709156_noxy"
                                                                            "mocked/il_570xN.1041709214_ae4i"])
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "clothing" "women" "dresses")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Maxi skirt"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.272372530"
                                                                            "mocked/il_570xN.272372548"])
                     :store.item/price    4.49M
                     :store.item/category [:category/path (category-path "clothing" "women" "skirts")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Leather Boots"
                     :store.item/photos   (map-indexed #(item-photo %2 %1) ["mocked/il_570xN.1087733031_du1y"
                                                                            "mocked/il_570xN.1087733249_hz9c"])
                     :store.item/price    6.37M
                     :store.item/category [:category/path (category-path "shoes" "women" "boots")]
                     :store.item/skus     [(sku)]}]}

   ;; RecycledBeautifully
   {:db/id         (db/tempid :db.part/user)
    :store/profile {:store.profile/name  "RecycledBeautifully"
                    :store.profile/cover (photo "mocked/isbl_3360x840.18460378_4d4b1gyn")
                    :store.profile/photo (photo "mocked/isla_500x500.23870003_5l3vsjlx")}
    :store/items   [{:store.item/name     "Tree of Life wire"
                     :store.item/photos   [(item-photo "mocked/il_570xN.728670429_e1dd")]
                     :store.item/price    24.74M
                     :store.item/category [:category/path (category-path "jewelry" "women" "necklaces")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tree of Life copper"
                     :store.item/photos   [(item-photo "mocked/il_570xN.1094904882_t58t")]
                     :store.item/price    42.49M
                     :store.item/category [:category/path (category-path "jewelry" "women")]
                     :store.item/skus     [(sku)]}
                    {:store.item/name     "Tree of Life wire"
                     :store.item/photos   [(item-photo "mocked/il_570xN.1074937810_dh62")]
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
   :user/profile {:user.profile/photo (photo "static/women-clothing")
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