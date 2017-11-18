(ns eponai.server.datomic.mocked-data
  (:require
    [eponai.common.database :as db]
    [eponai.common.api.products :as products]
    [eponai.common.format :as f]
    [clojure.string :as str]
    [taoensso.timbre :refer [debug]]
    [medley.core :as medley]
    [clojure.walk :as walk]
    [clojure.java.io :as io]
    [clojure.data.json :as json]))

(defn missing-personal-id-account []
  {:stripe/id   "acct_19k3ozC0YaFL9qxh"
   :stripe/publ "pk_test_zkG9ip9pZ984hIVoNN1dApoi"})

(defn no-details-account []
  {:stripe/id   "acct_1A9udfFhS10xugIf"
   :stripe/publ "pk_test_c5VS6ZK5mHacWIylwV9HpNwh"})

(defn stripe-account []
  (missing-personal-id-account))

(def test-user-email "me@email.com")
(def test-user-2-email "you@email.com")

(defn photo [id]
  {:db/id    (db/tempid :db.part/user)
   :photo/id id})
(defn sku [& [v]]
  (cond-> {:store.item.sku/inventory {:store.item.sku.inventory/type  :store.item.inventory.type/bucket
                                      :store.item.sku.inventory/value :store.item.inventory.value/in-stock}}
          (some? v)
          (assoc :store.item.sku/variation v)))

(defn item-photo [id & [i]]
  {:db/id                  (db/tempid :db.part/user)
   :store.item.photo/photo (photo id)
   :store.item.photo/index (or i 0)})

(defn item-photos [& ids]
  (map-indexed (fn [i p] (item-photo p i)) ids))

(defn item-skus [& skus]
  (if (empty? skus)
    [(sku)]
    (map sku skus)))

(defn adult-category [label]
  [#:category {:name  "unisex-adult"
               :label (str "Unisex " label)}
   #:category {:name  "men"
               :label (str "Men's " label)}
   #:category {:name  "women"
               :label (str "Women's " label)}])

(defn hash-map-by [f coll]
  (into {} (map (juxt f identity)) coll))

(def cats
  [#:category {:name     "clothing"
               :label    "Clothing"
               :children (into [
                                ;; #:category {:name  "unisex-kids" :label "Kids Clothing"}
                                ]
                               (adult-category "Clothing"))}
   #:category {:name     "jewelry"
               :label    "Jewelry"
               :children (adult-category "Jewelry")}
   #:category {:name     "home"
               :label    "Home"
               :children [#:category {:name  "bath-and-body"
                                      :label "Bath & Body"}
                          #:category {:name  "decor"
                                      :label "Décor"}
                          #:category {:name  "furniture"
                                      :label "Furniture"}
                          #:category {:name  "accessories"
                                      :label "Accessories"}]}
   #:category {:name  "art"
               :label "Art"}
   #:category {:name     "accessories"
               :label    "Accessories"
               :children (into
                           [
                            ;;#:category {:name  "unisex-kids" :label "Kids Accessories"}
                            ]
                           (adult-category "Accessories"))}])

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

(defn create-mock-stores []
  [
   ;; Simply Swedish ------------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name          "Simply Swedish"
                        :store.profile/photo         (photo "dynamic/real/nbatx9gxdmi4fxffqg0w")
                        :store.profile/cover         (photo "dynamic/real/bybk5ta1yst20bd2lpmg")
                        :store.profile/tagline       "Handmade reindeer jewelry from my indigenous Swedish heritage"
                        :store.profile/description   (f/str->bytes "<p>Simply Swedish is hand made reindeer jewelry made by artist Anna Lengstrand. \"I'm a Swede that found my ways, very spontaneously, to Whistler in 2010. Today I live on our own horse ranch in Pemberton, being inspired by the power of Mt Currie and nature every single day. I design Swedish reindeer and silver jewelry that are traditional Sami (Nordic indigenous) handiwork. My grandfather is Sami from the old reindeer herding Sami family Pokka, and the handcraft has been showed to me since a very young age from older family members. My Simply Swedish jewelry is made by reindeer leather and spun sterling silver-pewter thread woven on a band of reindeer leather, into patterns that bring to mind ancient Celts and Norse Warriors. The buttons are carved from shed reindeer antlers. The reindeer shed their antlers naturally in May every year. The leather was originally stitched together with reindeer ligaments, but I'm using a modern thread alternative. The craft is infused with the no trace, zero waste ethos of the Sami. It's their statement piece of jewelry. Pewter jewelry has been made by the reindeer herding Sami people of northern Scandinavia since at least the 1600s, and I'm working with both traditional braids and patterns as well as new versions that I design. I also always infuse my jewelry with good wishes and intentions. I believe in energies and vibrations... I would say that indigenous Sami handcraft meets timeless contemporary design in my jewelry, and I'm very honoured to be able to work with this ancient handcraft that means so much to me</p>")
                        :store.profile/return-policy (f/str->bytes "<p>Received the wrong size? Send the item back to me within 7 days from date of receiving. I’ll make a new bracelet for no extra charge. If the loop or button comes off within 45 days. Send it back to me and I’ll repair this for no extra cost. You must present a receipt or order confirmation. Customers will be responsible for all shipping &amp; re-shipping charges. Please e-mail if you're not happy with your piece of jewelry</p><p><br></p><p>Please be aware that a new Simply Swedish bracelet can feel a little stiff, but it quickly softens as you wear it. Same thing with the loop; it is made a little snug as the leather softens and the loop gets softer and slightly bigger after a few days of you wearing it. You can also rub a tiny bit of oil, like coconut oil, on it to make it soften faster. The reason why I make the loop snug is because I do not want you to loose your bracelet, so you can enjoy it for many years to come :)&nbsp;</p>")}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p>Please allow 2-3 weeks for shipment. Estimated shipping costs for a bracelet: within Canada $5, US $10, everywhere else $20</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/status      {:status/type :status.type/open}
    :store/username    "simplyswedish"
    :store/items       [{:store.item/name        "sami"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(27, 27, 27);\">The classic one. Simple is beautiful. SAMI is for you who prefer the minimalistic classic look. Sami is a reminder to show great respect to heritage, family, community and your land. Remember who you are and where you come from. Honour your roots! We are stronger together</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Width 0.8 cm</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">For sizing: measure your wrist snug above the bone, and add 1-1.5cm depending on how loose you want the bracelet to be</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">FOR MORE COLOUR OPTIONS AND SIZES PLEASE VISIT:</span></p><p>www.simplyswedish.ca</p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Every bracelet is carefully made by hand as you order it. Every piece of jewelry is custom made after your wrist and personal style. All bracelets are made with love and passion from highest quality vegetable tanned reindeer leather, pewter with 4% silver, sterling silver tag, and reindeer antler button. All materials are imported from craftsmen in Lapland Sweden for true authenticity</span></p>")
                         :store.item/category    [:category/path (category-path "jewelry" "unisex-adult")]
                         :store.item/photos      (item-photos "dynamic/real/rytbrb12fou1vrlpjntu"
                                                              "dynamic/real/lfeoyqjufz8jemu3qzpg"
                                                              "dynamic/real/mbacaywrqrbzmo3preul"
                                                              "dynamic/real/v3fmwgjsokhabvgtsl0x"
                                                              "dynamic/real/epy5wjc3kfhomg3a8nhp")
                         :store.item/price       85.00M
                         :store.item/skus        (item-skus "BLACK 16cm"
                                                            "BLACK 17cm"
                                                            "ANTIQUE BROWN 16cm"
                                                            "ANTIQUE BROWN 17cm"
                                                            "LIGHT GREY 16cm"
                                                            "LIGHT GREY 17cm"
                                                            "OLIVE GREEN 16cm"
                                                            "OLIVE GREEN 17cm")}
                        {:store.item/name        "torne"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(27, 27, 27);\">TORNE is named after the Tornedalen (Meänmaa in Finnish). This valley lies in the border of between Sweden and Finland, and it's here our family has our roots. Both my grandparents are born and raised here, and parts of our family are still doing reindeer farming in this area. We also have a cabin here where we spend as much time as we possible can. Torne is a reminder of purpouse. Remember what you are meant to be doing. Remember your passion. And never let it down</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Width 1.8cm</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">For sizing: measure your wrist snug above the bone, and add 1-1.5cm depending on how loose you want the bracelet to be</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">FOR MORE COLOUR OPTIONS AND SIZES PLEASE VISIT:</span></p><p>www.simplyswedish.ca</p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Every bracelet is carefully made by hand as you order it. Every piece of jewelry is custom made after your wrist and personal style. All bracelets are made with love and passion from highest quality vegetable tanned reindeer leather, pewter with 4% silver, sterling silver tag, and reindeer antler button. All materials are imported from craftsmen in Lapland Sweden for true authenticity</span></p>")
                         :store.item/category    [:category/path (category-path "jewelry" "unisex-adult")]
                         :store.item/photos      (item-photos "dynamic/real/rzjo7dxo4rulk4orv99l"
                                                              "dynamic/real/xnp2x5cdfszdgmb99l4c"
                                                              "dynamic/real/nqboe8y8qhd6ytfzv4oz")
                         :store.item/price       120.00M
                         :store.item/skus        (item-skus "LIGHT GREY 15.5cm"
                                                            "LIGHT GREY 16cm"
                                                            "LIGHT GREY 16.5cm"
                                                            "LIGHT GREY 17cm"
                                                            "LIGHT GREY 17.5cm")
                         }
                        {:store.item/name        "märta"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(27, 27, 27);\">When bohemian is combined with luxury, MÄRTA is born. Leather combined with a lot of silver gives you this luxurious reindeer bracelet. Märta is named after my dear grandmother. My grandmother Märta loves to dress up whenever she gets the chance. \"You'll never be wrong if you look a little extra nice\". Never hide. Honour yourself and your body. You’re worth it, every day</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Width 1.8 cm</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">For sizing: measure your wrist snug above the bone, and add 1-1.5cm depending on how loose you want the bracelet to be</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">FOR MORE COLOUR OPTIONS AND SIZES PLEASE VISIT:</span></p><p>www.simplyswedish.ca</p><p><span style=\"color: rgb(27, 27, 27);\">\uFEFF</span></p><p><span style=\"color: rgb(27, 27, 27);\">Every bracelet is carefully made by hand as you order it. Every piece of jewelry is custom made after your wrist and personal style. All bracelets are made with love and passion from highest quality vegetable tanned reindeer leather, pewter with 4% silver, sterling silver tag, and reindeer antler button. All materials are imported from craftsmen in Lapland Sweden for true authenticity</span></p>")
                         :store.item/category    [:category/path (category-path "jewelry" "unisex-adult")]
                         :store.item/photos      (item-photos "dynamic/real/dun8at6jmdwiy1kpdmr5"
                                                              "dynamic/real/g6ro5qxg1wsw8vhfgdtm")
                         :store.item/price       125.00M
                         :store.item/skus        (item-skus "BLACK 15.5cm"
                                                            "BLACK 16cm"
                                                            "BLACK 16.5cm"
                                                            "BLACK 17cm")}
                        {:store.item/name        "kalix"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(27, 27, 27);\">The perfect piece of jewelry for both her and him. KALIX is named after the river (Kalixälven) that passes by my family's cottage up in Lapland. Just a short drive from where grandfather was born and raised in the forest. Kalix symbolizes balance and harmony. Slow down and listen to your inner voice. What do you need in this moment to feel balanced and calm? Find that</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Width 2.3 cm</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">For sizing: measure your wrist snug above the bone, and add 1-1.5cm depending on how loose you want the bracelet to be</span></p><p><br></p><p><span style=\"color: rgb(27, 27, 27);\">Every bracelet is carefully made by hand as you order it. Every piece of jewelry is custom made after your wrist and personal style. All bracelets are made with love and passion from highest quality vegetable tanned reindeer leather, pewter with 4% silver, sterling silver tag, and reindeer antler button. All materials are imported from craftsmen in Lapland Sweden for true authenticity</span></p><p><br></p>")
                         :store.item/category    [:category/path (category-path "jewelry" "unisex-adult")]
                         :store.item/photos      (item-photos "dynamic/real/d6kuhregdmpxtesweikk"
                                                              "dynamic/real/ovywcnqkvjudkg9oevmr"
                                                              "dynamic/real/jbdqgcdy6hja6do8wuul")
                         :store.item/price       140.00M
                         :store.item/skus        (item-skus "ANTIQUE BROWN 16cm"
                                                            "ANTIQUE BROWN 16.5cm"
                                                            "ANTIQUE BROWN 17cm"
                                                            "ANTIQUE BROWN 17.5cm")}]}
   ;; ----------------------------- END Simply Swedish -------------------------------

   ;; ----------------------------- BEGIN Mind the Minimal ----------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name          "Mind The Minimal"
                        :store.profile/photo         (photo "dynamic/real/mqzjficxpk36656g5q0e")
                        :store.profile/cover         (photo "dynamic/real/kwopc8197cfghr5ttozp")
                        :store.profile/tagline       "Handcrafted Concrete Minimal Aesthetic\n\n"
                        :store.profile/description   (f/str->bytes "<p>It&nbsp;started as a simple vision.</p><p>&nbsp;</p><p>Mind The Minimal focuses on concrete and clean aesthetics.&nbsp;We combined these two elements together to create a unique distinctive&nbsp;</p><p>design to portray the effortless side of beauty.</p><p>&nbsp;</p><p>All of our designs are handcrafted through the process of pouring, sanding and painting.</p><p><br></p><p>We are happy to share this experience with you, let’s mind the minimal together.&nbsp;</p><p>&nbsp;</p>")
                        :store.profile/return-policy (f/str->bytes "<p>For any questions or concerns, please contact us.</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/status      {:status/type :status.type/open}
    :store/username    "mindtheminimal"
    :store/items       [{:store.item/name        "Rose Gold Teardrop Concrete Planter"
                         :store.item/description (f/str->bytes "<pre spellcheck=\"false\">By Mindtheminimal\n\nDESIGN\n\nThis beautiful artistic geometric planter is the perfect piece to edge up the decor.&nbsp;It's simple yet unique and brings a industrial minimalist vibe to the home.\n\nPerfect piece along with air plants + succulents and also a great piece on its own.\n\nDIMENSION\n\n4\" x 3\" x 3\" - Length x Width x Height (sitting on the side)\n3\" x 3\" x 4\" - Length x Width x Height (sitting with point up)\n&nbsp;\n\nPlant not included\n&nbsp;\nCare Instructions: http://www.mindtheminimal.com/careinstructions\n\nEach piece is made individually with the beautiful element of concrete.\nSlight colour or texture irregularities are due to the nature of the element.\n</pre><p><br></p>")
                         :store.item/category    [:category/path (category-path "home" "decor")]
                         :store.item/photos      (item-photos "dynamic/real/bjxgizzwiahyp9p3b4ah"
                                                              "dynamic/real/kxzdt63tor7znotnu9z0")
                         :store.item/price       35.00M
                         :store.item/uuid        #uuid "58a4b30e-3c8b-49c4-ab08-796c05b4275b"
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Gold Feathered Concrete Geometric Ring Cone"
                         :store.item/description (f/str->bytes "<pre spellcheck=\"false\">By Mind The Minimal\n\nDESIGN\n\nLight touch of gold elegance to compliment the modern black decor. \n\nPerfect piece for home decor and ring holder.\n\nDIMENSION\n\n2\" x 2\" x 3\" (Length x Width x Height)\n\nCare Instructions: http://www.mindtheminimal.com/careinstructions\n\nEach piece is made individually with the beautiful element of concrete + handpainted.Slight colour or texture irregularities are due to the nature of the element.\n</pre>")
                         :store.item/category    [:category/path (category-path "home" "decor")]
                         :store.item/photos      (item-photos "dynamic/real/you6hkz475qbcz1y8yb5")
                         :store.item/price       15.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "GOLD ICOSA CONCRETE PLANTER"
                         :store.item/description (f/str->bytes "<pre spellcheck=\"false\">By Mindtheminimal\n\nDESIGN\n\nThis beautiful artistic geometric planter is the perfect piece to edge up the decor.\nIt's simple yet unique and brings a industrial minimalist vibe to the home.\n\nPerfect piece along with air plants + succulents or&nbsp;stationery/jewelry&nbsp;holder.\n\nDIMENSION\n\n2.75\" x 2.75\" x 1.50\" - Length x Width x Height\n\n\nPlant not included\n&nbsp;\nCare Instructions: http://www.mindtheminimal.com/careinstructions\n\nEach piece is made individually with the beautiful element of concrete.\nSlight colour or texture irregularities are due to the nature of the element.\n</pre>")
                         :store.item/category    [:category/path (category-path "home" "decor")]
                         :store.item/photos      (item-photos "dynamic/real/l7clcnvh97civuweqxpt")
                         :store.item/price       28.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "CYLINDER Concrete Planter"
                         :store.item/description (f/str->bytes "<pre spellcheck=\"false\">By Mindtheminimal\n\nDESIGN\n\nThis beautiful artistic geometric planter is the perfect piece to edge up the decor.\nIt's simple yet unique and brings a industrial minimalist vibe to the home.\n\nPerfect piece along with air plants + succulents or&nbsp;stationery/jewelry&nbsp;holder.\n\nDIMENSION\n\n2.75\" x 2.75\" x 2\" - Length x Width x Height\n\nCOLOURS\n&nbsp;\nGold, Rose Gold,&nbsp;White, Blue, Pink\n\nPlant not included\n\nCare Instructions:&nbsp;http://www.mindtheminimal.com/careinstructions\n\nEach piece is made individually with the beautiful element of concrete.\nSlight colour or texture irregularities are due to the nature of the element.\n</pre>")
                         :store.item/category    [:category/path (category-path "home" "decor")]
                         :store.item/photos      (item-photos "dynamic/real/rtahj1lwssj5e06uxgme"
                                                              "dynamic/real/w0rf11npzvqrl3rap98k")
                         :store.item/price       28.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "GOLD CUBE Concrete Planter"
                         :store.item/description (f/str->bytes "<pre spellcheck=\"false\">By Mindtheminimal\n\nDESIGN\n\nThis beautiful artistic geometric planter is the perfect piece to edge up the decor.\nIt's simple yet unique and brings a industrial minimalist vibe to the home.\n\nPerfect piece along with air plants + succulents or&nbsp;stationery holder.\n\nDIMENSION\n\n3\" x 3\" x 3\" - Length x Width x Height\n\nPlant not included\n\nhttp://www.mindtheminimal.com/careinstructions\n\nEach piece is made individually with the beautiful element of concrete.\nSlight colour or texture irregularities are due to the nature of the element.\n</pre>")
                         :store.item/category    [:category/path (category-path "home" "decor")]
                         :store.item/photos      (item-photos "dynamic/real/myjfnwbxzsckzim2pgdg")
                         :store.item/price       35.00M
                         :store.item/skus        (item-skus)}]}
   ;; ------------------------- END Mind the minimal -------------------------------------------

   ;; --------------------------- BEGIN Metamorphose ------------------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name          "Metamorphose"
                        :store.profile/cover         (photo "dynamic/real/lbapedb5f57g6j63isjl")
                        :store.profile/photo         (photo "dynamic/real/wxekibunw1kask8mhjkj")
                        :store.profile/tagline       "Ready-to-wear clothing for women designed and handmade in Montreal, Qc, CA"
                        :store.profile/description   (f/str->bytes "<p><strong><em>Signed METAMORPHOSE</em></strong></p><p><em>Metamorphose</em>&nbsp;is a line of ready-to-wear clothing that stands out for its unique style, its feminine cuts and its attention to details and impeccable finish. Made entirely in the&nbsp;Montreal workshop&nbsp;by a team just as concerned about quality,&nbsp;<em>Metamorphose</em>&nbsp;signature is a meaning of authenticity, femininity, comfort &amp; durability.</p><p><br></p><p><strong><em>The Workshop – Boutique</em></strong></p><p>Quebec, including Montreal, has a rich heritage from fashion and clothing industry. The&nbsp;<em>Metamorphose</em>&nbsp;ready-to-wear clothing line is created in one of its remains of the Montreal manufacturing era:&nbsp;<strong>Grover factory</strong>. Cultural space and artistic synonymous, this ancient textile factory has a large number of painter artists, designers, jewelers, architects, graphic designers and theater companies.</p><p><br></p><p>This is where all the magic appears, that each piece is created and crafted. Each production step is thoroughly done by a careful team within the workshop for a better quality control. Aware of the long term environmental consequences, the company keeps the scraps and sends everything to non-profit organizations that will recycle the fabric.</p><p><br></p><p><em>Metamorphose</em>&nbsp;is a safe bet to anyone who’s looking for quality, comfort &amp; femininity. Each&nbsp;new&nbsp;collection&nbsp;is awaited with excitement by loyal and enthusiastic customers.</p><p><br></p><p><strong>OS X META</strong></p><p><strong>EXCLUSIVE COLLECTION&nbsp;</strong></p><p><br></p><p>Born of a common passion, the&nbsp;<strong>OS X META</strong>&nbsp;collection is the meeting of a painter (<strong>Os cane</strong>) inspired by the fusion between nature and the human body and a fashion designer (<strong>Metamorphose</strong>) passionate about visual art. Each one, in her own way, combines texture and colour to create utterly delicate and feminine organic works of art. The two artists wanted to unite their talents and interests in order to create exclusive fabrics based on existing paintings and thus recreate these works in order to dress the body with organic femininity, a form of \"Art to wear\".</p>")
                        :store.profile/return-policy (f/str->bytes "<p><strong>EXCHANGE AND RETURN POLICY</strong></p><p>We guarantee consumer satisfaction. If you are not satisfied with your purchase, an exchange for an item of the same value is allowed. If, at the time of the exchange, you do not find an item of the same value or we are out of stock in a size you want to exchange, a coupon code will be issued for future purchases on our online store .</p><p><br></p><p><strong>NO REFUNDS IS POSSIBLE EXCEPT EXCEPTIONS *</strong></p><p>* Exceptions from a manufacturing defect or an error on our part</p><p>* The product falls out of stock during order processing</p><p>* Other reason deemed valid by our customer service</p><p><span class=\"ql-cursor\">\uFEFF</span></p><p><strong>The item will be returned at the expense of the customer. We accept exchanges for a period of 7 days after purchase. BEFORE RETURNING A PACKAGE, THE CUSTOMER'S RESPONSIBILITY TO EMAIL WARNING THAT IT WILL RETURN THE PACKAGE&nbsp;</strong>to karine@creationsmetamorphose.com. The item must be returned in its original condition. Any damaged product will not be exchanged.</p><p><br></p>")}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p><strong>GENERAL CONDITIONS OF SALE AND DELIVERY</strong></p><p>Except in the case of a special agreement, orders are processed within a maximum of 5 working days of receipt of your payment. If the deadline is extended, we will contact you to notify you.</p><p><br></p><p><span class=\"ql-cursor\">\uFEFF</span>* Delivery costs are determined according to the destination and not according to the weight of the package (please contact us before placing your order to know the fixed cost of your destination).</p><p><br></p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Montreal, QC"}
    :store/username    "metamorphose"
    :store/status      {:status/type :status.type/open}
    :store/items       [{:store.item/name        "MORNY sweater"
                         :store.item/description (f/str->bytes "<p><strong style=\"color: rgb(61, 60, 60);\">MORNY sweater</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Raglan sleeves sweater, V-cut of flowered fabric at the front (ornamentation of small flowers at the front cut).</span></p><p><span style=\"color: rgb(61, 60, 60);\">\uFEFF</span></p><p><strong style=\"color: rgb(61, 60, 60);\">FABRIC &amp; CARE</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Stretch soft fabric 50% rayon 30% polyester 10% nylon 5% spandex 5% cotton</span></p><p><span style=\"color: rgb(61, 60, 60);\">Hand wash in cold water with mild soap, dry flat.</span></p><p><span style=\"color: rgb(61, 60, 60);\">Designed and manufactured in Montreal, QC, CA</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">Please contact us if you have questions regarding our size chart.</span></p><p><span style=\"color: rgb(61, 60, 60);\">Our model is wearing the size \"Small\" and mesure 5'2 height.</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">Thank you to keep in mind that colors vary from one computer to another.</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "women")]
                         :store.item/photos      (item-photos "dynamic/real/rk9x9kfw7uyliv2rldka"
                                                              "dynamic/real/pdnzq7gtkkqfosyxmnfr")
                         :store.item/price       95.00M
                         :store.item/skus        (item-skus "X-Small"
                                                            "Small"
                                                            "Medium"
                                                            "Large"
                                                            "X-Large")}
                        {:store.item/name        "AKO sweater"
                         :store.item/description (f/str->bytes "<p><strong style=\"color: rgb(61, 60, 60);\">AKO sweater</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Ample semi-transparent printed sheer fabric sweater,&nbsp;round neckline&nbsp;and black sheer sleeves.</span></p><p><strong style=\"color: rgb(61, 60, 60);\">Note:</strong><span style=\"color: rgb(61, 60, 60);\">&nbsp;The model of this sweater is made ample, the style is therefore \"oversize\". </span></p><p><br></p><p><strong style=\"color: rgb(61, 60, 60);\">FABRIC &amp; CARE</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Sheer semi-transparent fabric 98% polyester 2% spandex</span></p><p><span style=\"color: rgb(61, 60, 60);\">Hand wash in cold water with mild soap, dry flat.</span></p><p><span style=\"color: rgb(61, 60, 60);\">Designed and manufactured in Montreal, QC, CA</span></p><p><br></p><p>Please contact us if you have questions regarding our size chart.</p><p><span style=\"color: rgb(61, 60, 60);\">Our model is wearing the size \"Small\" and mesure 5'2 height.</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">JEWELRY</span><em style=\"color: rgb(61, 60, 60);\">:&nbsp;</em><a href=\"http://www.louvemontreal.com/\" target=\"_blank\" style=\"color: rgb(61, 60, 60);\"><em>LOUVE MONTRÉAL</em></a></p>")
                         :store.item/category    [:category/path (category-path "clothing" "women")]
                         :store.item/photos      (item-photos "dynamic/real/elxrvna8bgjzqclxpbpk"
                                                              "dynamic/real/fsyxjrdpk0iv7xzh20zj")
                         :store.item/price       95.00M
                         :store.item/skus        (item-skus "Small"
                                                            "Medium"
                                                            "Large")}
                        {:store.item/name        "ZAYA sweater"
                         :store.item/description (f/str->bytes "<p><strong style=\"color: rgb(61, 60, 60);\">ZAYA sweater</strong></p><p><strong style=\"color: rgb(61, 60, 60);\">\uFEFF</strong><span style=\"color: rgb(61, 60, 60);\">Ample semi-transparent sheer fabric sweater, claudine collar with floral fabric and black sheer sleeves.</span></p><p><strong style=\"color: rgb(61, 60, 60);\">Note:</strong><span style=\"color: rgb(61, 60, 60);\">&nbsp;The model of this sweater is made ample, the style is therefore \"oversize\". </span></p><p><br></p><p><strong style=\"color: rgb(61, 60, 60);\">FABRIC &amp; CARE</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Sheer semi-transparent fabric 98% polyester 2% spandex</span></p><p><span style=\"color: rgb(61, 60, 60);\">Hand wash in cold water with mild soap, dry flat.</span></p><p><span style=\"color: rgb(61, 60, 60);\">Designed and manufactured in Montreal, QC, CA</span></p><p><br></p><p>Please contact us if you have questions regarding our size chart.</p><p><span style=\"color: rgb(61, 60, 60);\">Our model is wearing the size \"Small\" and mesure 5'2 height.</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">Thank you to keep in mind that colors vary from one computer to another.</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">JEWELRY</span><em style=\"color: rgb(61, 60, 60);\">:&nbsp;</em><a href=\"http://www.louvemontreal.com/\" target=\"_blank\" style=\"color: rgb(61, 60, 60);\"><em>LOUVE MONTRÉAL</em></a></p>")
                         :store.item/category    [:category/path (category-path "clothing" "women")]
                         :store.item/photos      (item-photos "dynamic/real/l67a6gmgmvuxbkw4nuwc"
                                                              "dynamic/real/tzjf2ldalx1ukmvaofqd")
                         :store.item/price       95.00M
                         :store.item/skus        (item-skus "Small"
                                                            "Medium"
                                                            "Large")}
                        {:store.item/name        "Kimono «SOUPIR» exclusive collection OS X META"
                         :store.item/description (f/str->bytes "<p><strong style=\"color: rgb(61, 60, 60);\">Kimono blouse «SOUPIR» exclusive collection OS X META</strong></p><p><span style=\"color: rgb(61, 60, 60);\">Blouse in printed sheer, sleeves kimono length 3/4 with cords at the front.</span></p><p><br></p><p><strong style=\"color: rgb(61, 60, 60);\"><em>ABOUT:&nbsp;</em></strong><em style=\"color: rgb(61, 60, 60);\">Fruit of more than a year of research and work, all items in our&nbsp;</em><a href=\"http://www.creationsmetamorphose.com/en/category/os-x-meta-collection\" target=\"_blank\" style=\"color: rgb(61, 60, 60);\"><strong><em>OS X META</em></strong></a><em style=\"color: rgb(61, 60, 60);\">&nbsp;collection were created in collaboration with the artist painter&nbsp;</em><a href=\"https://www.facebook.com/r.lessard.artistepeintre/?fref=ts\" target=\"_blank\" style=\"color: rgb(61, 60, 60);\"><strong><em>OS CANE</em></strong></a><em style=\"color: rgb(61, 60, 60);\">. Unique and exclusive, the fabrics used for the design of the collection are reproductions of existing canvas printed on the fabric with the sublimation process.</em></p><p><br></p><p><strong style=\"color: rgb(61, 60, 60);\">FABRIC &amp; CARE</strong></p><p><span style=\"color: rgb(61, 60, 60);\">100% polyester&nbsp;</span></p><p><span style=\"color: rgb(61, 60, 60);\">Hand wash in cold water with mild soap, dry flat.</span></p><p><span style=\"color: rgb(61, 60, 60);\">Designed and manufactured in Montreal, QC, CA</span></p><p><br></p><p>Please contact us if you have questions regarding our size chart.</p><p><span style=\"color: rgb(61, 60, 60);\">Our model is wearing the size \"Small\" and mesure 5'5 height.</span></p><p><br></p><p><span style=\"color: rgb(61, 60, 60);\">Thank you to keep in mind that colors vary from one computer to another.</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "women")]
                         :store.item/photos      (item-photos "dynamic/real/gihnjk8tmnllbahn9mab"
                                                              "dynamic/real/hgogzm5bcan1cyjs0bbf")
                         :store.item/price       135.00M
                         :store.item/skus        (item-skus "Small"
                                                            "Medium"
                                                            "Large")}]}
   ;; ----------------------------- END Metamorphose -------------------------------------------------


   ;; ----------------------------- BEGIN Zed Handmade -------------------------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name          "zed handmade"
                        :store.profile/cover         (photo "dynamic/real/hpo5cea9xjx7cvrqz6kl")
                        :store.profile/photo         (photo "dynamic/real/agjvjt2leplfqc3oc44e")
                        :store.profile/tagline       "wonderful woolen wares handmade in south surrey, bc"
                        :store.profile/description   (f/str->bytes "<p><br></p><p>My name is Diane and I live in South Surrey, BC. I established zed handmade as a small independent studio in 2011 and have been part of the Vancouver \"shop local\" community ever since.</p><p>All zed pieces are designed and hand-knit by me (with knitting needles, not a machine!) using 100% Peruvian highland wool and Peruvian alpaca yarn.</p><p>\u200BThe designs, colour&nbsp;palette and attention to detail reflect my love of texture, organic colours and natural fibres.</p><p><br></p><p><br></p>")
                        :store.profile/return-policy (f/str->bytes "<p>If you're unhappy with your purchase, I want to help you fix that.&nbsp;Refunds will be considered within 4 days of receiving your zed item.&nbsp;Please&nbsp;contact me&nbsp;before sending anything back.&nbsp;Please understand that you will not be reimbursed for any shipping costs associated with the purchase.&nbsp;All returns must be received in the same condition in which they were shipped.&nbsp;Refunds will be issued when items are received by zed handmade.&nbsp;</p><p>Thank you for supporting an independent studio!</p><p><br></p><p><br></p>")}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p>Shipping rate to Canada and the continental USA is $15.00 <em>per order</em>. Free shipping for orders over $300</p><p>All taxes, brokerage fees and/or duties for shipments to the USA are the responsibility of the buyer.&nbsp;You will be billed for these fees before shipping is completed.</p><p>Expedited shipping:&nbsp;Please&nbsp;contact me&nbsp;for a quote</p><p>Outside of North America:&nbsp;Not available at this time.&nbsp;Sorry.</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Surrey, BC"}
    :store/username    "zedhandmade"
    :store/status      {:status/type :status.type/open}
    :store/items       [{:store.item/name        "MENSCH wool scarf sterling"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(129, 129, 129);\">The word \"mensch\" refers to someone to admire and emulate, often used to denote a \"real gentleman\".&nbsp;The MENSCH scarf&nbsp;might be \"admirable\" in it's original, highly textural stitch design, and may make you look like a \"gentleman\", but we think it should be shared with the lady in your life too!&nbsp;It is definitely a unisex design and we think it looks pretty amazing on women.&nbsp;</span></p><p><span style=\"color: rgb(129, 129, 129);\">Measuring 6 feet long, there is plenty of scarf to wrap round and round!&nbsp;Hand knit with 100% Peruvian highland wool, this sterling MENSCH is a versatile neutral color.</span></p>")
                         :store.item/category    [:category/path (category-path "accessories" "men")]
                         :store.item/photos      (item-photos "dynamic/real/gwdnnhrqc1an8etnnfuo"
                                                              "dynamic/real/necmuyxuslxwbnb3j5kk")
                         :store.item/price       145.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "EMME wool cowl midnight"
                         :store.item/description (f/str->bytes "<p>EMME is a palindrome in name and design.&nbsp;Ingenious in its&nbsp;&nbsp;construction, it is impossible&nbsp;to know where it begins and ends;&nbsp;identical forwards and backwards, inside and out.</p><p>Hand knit with 100% Peruvian highland wool, EMME snugs into your neck to keep you warm,&nbsp;yet makes a statement that will have you wearing it long after&nbsp;the cold weather has passed.&nbsp;</p><p>Store folded flat, do not hang.&nbsp;Hand wash with Eucalan fine fabric wash, blot with a fluffy towel, reshape and lay flat to dry. May also be dry cleaned.&nbsp;</p>")
                         :store.item/category    [:category/path (category-path "accessories" "women")]
                         :store.item/photos      (item-photos "dynamic/real/if8sqwfyaffyar6rwn7l"
                                                              "dynamic/real/kj6ekifarofvc7v4i936")
                         :store.item/price       125.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "SOPHIE alpaca long cowl cinnamon"
                         :store.item/description (f/str->bytes "<p>SOPHIE's simple, classic hand knit design showcases the beauty of this 100% Peruvian alpaca yarn.&nbsp;When you hold SOPHIE&nbsp;in your hands, you will be amazed at the feeling of soft, cushiony luxury.&nbsp;</p><p>Measuring approximately 10\" wide, SOPHIE can be worn long or wrapped twice around your neck and will be one of the warmest cowls you own.&nbsp;</p><p>Store folded flat, do not hang.&nbsp;Hand wash with Eucalan fine fabric wash, blot with a fluffy towel, reshape and lay flat to dry.&nbsp;May also be dry cleaned.</p>")
                         :store.item/category    [:category/path (category-path "accessories" "women")]
                         :store.item/photos      (item-photos "dynamic/real/ml8rof52f3pvp6xipwv4"
                                                              "dynamic/real/gbe8l557ztlstjzf1pbf")
                         :store.item/price       165.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "LANE wool scarf poinsettia"
                         :store.item/description (f/str->bytes "<p>LANE is a extra long bulky scarf with a design reminiscent of tire tracks on a snowy road.&nbsp;There are so many ways to wear this piece, whether it is wrapped tightly around your neck on a cold damp day or draped open for a big splash of color.&nbsp;</p><p>Hand knit with 100% Peruvian highland wool, LANE is knit with or without a fringe, and may be custom ordered in either configuration.&nbsp;Fringed LANE measures 9 inches wide;&nbsp;unfringed LANE is 11 inches wide.</p><p>Store folded flat, do not hang.&nbsp;Hand wash with Eucalan fine fabric wash, blot with a fluffy towel, reshape and lay flat to dry. May also be dry cleaned.&nbsp;</p>")
                         :store.item/category    [:category/path (category-path "accessories" "women")]
                         :store.item/photos      (item-photos "dynamic/real/swwcsshjwxkldrpgxcs6"
                                                              "dynamic/real/woegzczztlfoms4rirp2")
                         :store.item/price       125.00M
                         :store.item/skus        (item-skus)}]}

   ;; ---------------------------------- END zed handmade -----------------------------------

   ;; ---------------------------------- BEGIN Start with the basis -----------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name        "Start With the Basis"
                        :store.profile/cover       (photo "dynamic/real/u1dofrq8fudcodwgw3mw")
                        :store.profile/photo       (photo "dynamic/real/mdcddiglzxe9jhs6hrth")
                        :store.profile/description (f/str->bytes "<p>Start With the Basis is a blend of quality, function, and bold simplicity. Rooted in inspiration from the well-dressed of the past, SWB is a new adaptation and a means for men around the world to dress up their casual environment.</p><p><br></p><p>Taking cues from artisan craftsmanship and utilitarianism, the brand transforms inspiration with an updated outlook and creates with ethical production values.</p><p><br></p><p>Based in Vancouver, an international city with a mecca of multiculturalism, SWB is the result of its rugged yet temperate environment. Vancouver is a leader for tech-driven design with an ever-growing business and creative community.</p><p>Start With The Basis is at the forefront of this combination.</p><p>&nbsp;</p><p><u>THE MEANING</u></p><p>The phrase encompasses the beginning. In a very stimulated world, sometimes an initial idea&nbsp;gets lost or misinterpreted. Start With the Basis is set to help realign the idea: what is the reason for the creation?</p><p><br></p><p><span class=\"ql-cursor\">\uFEFF</span>Take a piece of paper and fold it into something beautiful; a basic way to understand&nbsp;the process of creation. This idea can pertain&nbsp;to anything pursuing a creative end.&nbsp;Pull apart the origami and the folds remain. This is the logo: a reminder to never forget the importance of the foundation.</p><p><br></p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/username    "startwiththebasis"
    :store/status      {:status/type :status.type/open}
    :store/sections    [{:db/id               (db/tempid :db.part/user -1000)
                         :store.section/label "Bottoms"}
                        {:db/id               (db/tempid :db.part/user -1001)
                         :store.section/label "Tops"}
                        {:db/id               (db/tempid :db.part/user -1002)
                         :store.section/label "Outer Wear"}]
    :store/items       [{:store.item/name        "Akiko Jeans"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(61, 66, 70);\">Slim straight regular rise stretch selvedge jeans in Chevy. Made from raw 10.75oz Italian spandex cotton blend selvedge denim. 5 pocket with zip feature selvedge detail side seam coin pocket. Button fly with fully integrated belt loops and black top stitching throughout. Reverse hem with Evan Snaps™ to create tapered jogger look. All sizes made to 34\" inseam.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">Ready for harsh boot conditions or sunny sneakers, the stretch and snaps in the jeans provide comfort and versatility for denim that can be worn everyday, no matter what the situation.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">*Please not that for any customers wishing to alter the inseam length, it must be an original hem to preserve the Evan Snaps.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">\uFEFFMade in Canada.</span></p><p><span style=\"color: rgb(61, 66, 70);\">&nbsp;</span></p><p><span style=\"color: rgb(61, 66, 70);\">Model is wearing a size 32.</span></p><p><br></p><p><u style=\"color: rgb(61, 66, 70);\">Model Measurements</u></p><p><span style=\"color: rgb(61, 66, 70);\">Height: 6' 2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Neck: 16 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Chest: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Waist: 32\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Hips: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Sleeve: 34 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Shoulder: 17\"</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "men")]
                         :store.item/section     (db/tempid :db.part/user -1000)
                         :store.item/photos      (item-photos "dynamic/real/noen0jfb7imsif9tdsmr"
                                                              "dynamic/real/ouubfdf8tvzxy5ptetcg"
                                                              "dynamic/real/yvhtocnttdzrnc7fh0ws"
                                                              "dynamic/real/n15il3vvbr00tdjkvxn7"
                                                              "dynamic/real/pxxe1epr3jsfqhyozmoy")
                         :store.item/price       249.00M
                         :store.item/skus        (item-skus "30" "32" "34" "36")}
                        {:store.item/name        "SSBD Bomber Jacket"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(61, 66, 70);\">Fully lined bomber style with Freestyle 3-ply&nbsp;water resistant and breathable exterior in Midnight. Rib knit collar, cuffs and sides&nbsp;with slightly elongated body. Two-way YKK closure at front. YKK zipper pockets at waist.&nbsp;YKK zipper passport pocket on left sleeve. Zippered interior lining breast pocket. Right side seam zippered invisible pocket. Dual function snap&nbsp;option zipper guard. Tonal stitching and contrast silver&nbsp;hardware throughout.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">The boldest style in Collection I. A contemporary take on your standard bomber jacket, that can still withstand the elements.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">Made in Canada.</span></p><p><span style=\"color: rgb(61, 66, 70);\">&nbsp;</span></p><p><span style=\"color: rgb(61, 66, 70);\">Model is wearing a size L.</span></p><p><br></p><p><u style=\"color: rgb(61, 66, 70);\">Model Measurements</u></p><p><span style=\"color: rgb(61, 66, 70);\">Height: 6' 2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Neck: 16 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Chest: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Waist: 32\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Hips: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Sleeve: 34 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Shoulder: 17\"</span></p>")
                         :store.item/section     (db/tempid :db.part/user -1002)
                         :store.item/photos      (item-photos "dynamic/real/vva1yxd7jp20atzwm59i"
                                                              "dynamic/real/iuirntfxbsqqt1pbpukm"
                                                              "dynamic/real/r0t9vnjeiojwgt6tt4dq"
                                                              "dynamic/real/varxlujivhjkbyxidrnf")
                         :store.item/price       399.00M
                         :store.item/category    [:category/path (category-path "clothing" "men")]
                         :store.item/skus        (item-skus "S" "M" "L" "XL")}
                        {:store.item/name        "Kamekubi Shirt - Gunmetal"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(61, 66, 70);\">Long&nbsp;sleeve polyester cotton blend rib knit shirt&nbsp;in Gunmetal. Cross neck rib knit&nbsp;collar with tonal stitching. Opposite grain elbow patches and sloped&nbsp;cuffs. Straight front hem and curved back hem. Woven tag on back.&nbsp;</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">A versatile top with opposing grain lines that move with your body. Directional stretch in the elbow for better arm movement and a more rigid cuff for when sleeves are pulled up.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">Made in Canada.</span></p><p><span style=\"color: rgb(61, 66, 70);\">&nbsp;</span></p><p><span style=\"color: rgb(61, 66, 70);\">Model is wearing a size M.</span></p><p><br></p><p><u style=\"color: rgb(61, 66, 70);\">Model Measurements</u></p><p><span style=\"color: rgb(61, 66, 70);\">Height: 6' 2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Neck: 16 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Chest: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Waist: 32\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Hips: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Sleeve: 34 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Shoulder: 17\"</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "men")]
                         :store.item/section     (db/tempid :db.part/user -1001)
                         :store.item/photos      (item-photos "dynamic/real/axbkjuszo6jhp6sytfkl"
                                                              "dynamic/real/cruebqgnjdsg4algau1w"
                                                              "dynamic/real/vpdfpqklleaiu7gr7azh"
                                                              "dynamic/real/fxfu1zclvyc4czup4ti4")
                         :store.item/price       119.00M
                         :store.item/skus        (item-skus "S" "M" "L" "XL")}
                        {:store.item/name        "Kamekubi Shirt - Fog"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(61, 66, 70);\">Long&nbsp;sleeve polyester cotton blend rib knit shirt&nbsp;in Fog. Cross neck rib knit&nbsp;collar with tonal stitching. Opposite grain elbow patches and sloped&nbsp;cuffs. Straight front hem and curved back hem. Woven tag on back.&nbsp;</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">\uFEFFA versatile top with opposing grain lines that move with your body. Directional stretch in the elbow for better arm movement and a more rigid cuff for when sleeves are pulled up.</span></p><p><span style=\"color: rgb(61, 66, 70);\">Made in Canada.</span></p><p><span style=\"color: rgb(61, 66, 70);\">&nbsp;</span></p><p><span style=\"color: rgb(61, 66, 70);\">Model is wearing a size M.</span></p><p><br></p><p><u style=\"color: rgb(61, 66, 70);\">Model Measurements</u></p><p><span style=\"color: rgb(61, 66, 70);\">Height: 6' 2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Neck: 16 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Chest: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Waist: 32\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Hips: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Sleeve: 34 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Shoulder: 17\"</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "men")]
                         :store.item/section     (db/tempid :db.part/user -1001)
                         :store.item/photos      (item-photos "dynamic/real/oitby0s3myomdahia2m3"
                                                              "dynamic/real/db0dxjz3zhjfrfkoc6i7"
                                                              "dynamic/real/r4eu0hhwfgjx4ogtvxek"
                                                              "dynamic/real/ofndku4rebmdtupay01q")
                         :store.item/price       119.00M
                         :store.item/skus        (item-skus "S" "M" "L" "XL")}
                        {:store.item/name        "SS Bomber Jacket"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(61, 66, 70);\">Fully lined bomber style with Aquastop&nbsp;water resistant and breathable exterior in Raven. Rib knit collar, cuffs and hem. Two-way YKK closure at front. Dual function flap snap/zip pockets at waist. Dual function flap snap/zip passport pocket on left sleeve. Zippered interior lining breast pocket. Right side seam zippered invisible pocket. Tonal stitching, black metal and gunmetal hardware throughout.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">Clean, crispy and versatile. This is the everyday jacket that can be worn with anything and be able to react to everything.</span></p><p><br></p><p><span style=\"color: rgb(61, 66, 70);\">Made in Canada.</span></p><p><span style=\"color: rgb(61, 66, 70);\">&nbsp;</span></p><p><span style=\"color: rgb(61, 66, 70);\">Model is wearing a size L</span></p><p><br></p><p><u style=\"color: rgb(61, 66, 70);\">Model Measurements</u></p><p><span style=\"color: rgb(61, 66, 70);\">Height: 6' 2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Neck: 16 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Chest: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Waist: 32\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Hips: 38\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Sleeve: 34 1/2\"</span></p><p><span style=\"color: rgb(61, 66, 70);\">Shoulder: 17\"</span></p>")
                         :store.item/category    [:category/path (category-path "clothing" "men")]
                         :store.item/section     (db/tempid :db.part/user -1002)
                         :store.item/photos      (item-photos "dynamic/real/rtzwt0dufllafu3aa1ys"
                                                              "dynamic/real/uvtk1lyohe3mc0brm2fs"
                                                              "dynamic/real/nyolomusg8giocumwwuv"
                                                              "dynamic/real/kfkdo1l0pplasg6zmlnn")
                         :store.item/price       399.00M
                         :store.item/skus        (item-skus "S" "M" "L" "XL")}]}

   ;; ---------------------------- END Start with the basis ---------------------------------

   ;; ----------------------------- BEGIN Midnight Paloma ------------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name        "Midnight Paloma"
                        :store.profile/cover       (photo "dynamic/real/yntnqnhubb9rarhvirqt")
                        :store.profile/photo       (photo "dynamic/real/pp4p9st84khfshtla7bs")
                        :store.profile/tagline     "Hi! We're a Canadian skincare company dedicated to clean beauty & an ingredient called charcoal. "
                        :store.profile/description (f/str->bytes "<p>As fellow product junkies we've always been aware of what we put into our bodies and how lifestyles can effect our health in the long run. We love the concept of detoxification and are especially obsessed with the idea of how charcoal can naturally provide that for us. This is what we are dedicated to. 100% natural, gluten free &amp; peta approved we've sourced and curated our<strong>&nbsp;</strong>small batch Canadian made line that is charcoal with a touch of luxury. Pairing this killer natural ingredient with some amazing others, we hope you love Midnight Paloma as much as we do.&nbsp;</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/username    "midnightpaloma"
    :store/status      {:status/type :status.type/open}
    :store/sections    [{:db/id               (db/tempid :db.part/user -1100)
                         :store.section/label "BODY"}
                        {:db/id               (db/tempid :db.part/user -1101)
                         :store.section/label "FACE"}]
    :store/items       [{:store.item/name        "Charcoal + Grapefruit Fizzy Bath Soak"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(35, 31, 32);\">Our natural blend of grapefruit, rose + charcoal will hydrate and invigorate after a long day. Use in the bath or as a foot soak. (And no, it won’t stain your tub).</span></p><p><span style=\"color: rgb(35, 31, 32);\">Ingredients: Magnesium sulfate (epsom salt), whole milk powder, sodium chloride (sea salt), Avena sativa (rolled oats), carbones (charcoal) and/et essential blend/huile essentielles.</span></p><p><br></p><p><em style=\"color: rgb(35, 31, 32);\">16 oz 470 ml</em></p>")
                         :store.item/category    [:category/path (category-path "home" "bath-and-body")]
                         :store.item/section     (db/tempid :db.part/user -1100)
                         :store.item/photos      (item-photos "dynamic/real/a3zuihmnanaol3du1uzz")
                         :store.item/price       44.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Charcoal + Rose Detox Mask"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(35, 31, 32);\">Our natural charcoal mask is your skins best friend when it comes to keeping it detoxified. Charcoal absorbs impurities and acts like a magnet to draw out dirt from pores. Rolled oats sooth and rose petals hydrate.</span></p><p><span style=\"color: rgb(35, 31, 32);\">Ingredients: kaolinite (white clay), avena sativa (rolled oats), carbones (charcoal), rosa centifolia (rose petals) and/et essential blend/huile essentielles.</span></p><p><br></p><p><em style=\"color: rgb(35, 31, 32);\">2 oz 55 g</em></p><p><br></p><p><br></p>")
                         :store.item/category    [:category/path (category-path "home" "bath-and-body")]
                         :store.item/section     (db/tempid :db.part/user -1101)
                         :store.item/photos      (item-photos "dynamic/real/jlzkbavx9p9voqawmjjv")
                         :store.item/price       22.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Sweet Almond + Carrot Seed Face Serum"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(35, 31, 32);\">Alleviate redness and regenerate cell growth with our all natural serum. Sweet almond oil improves and retains glow and carrot seed oil improves skin tones and elasticity. Apply in the am + pm after cleansing or after your charcoal mask. Massage in gentle circular motions over face and neck until absorbed.</span></p><p><br></p><p><span style=\"color: rgb(35, 31, 32);\">Ingredients: Simmondsia chinensis (jojoba oil), calendae (calendula), Oryza Sativa (rice bran oil), rosa canina (rosehip seed), prunus dulcis (almond oil), daucus carota (carrot seed tissue), vitamin e and/et essential blend/huile essentielles.</span></p><p><br></p><p><em style=\"color: rgb(35, 31, 32);\"><span class=\"ql-cursor\">\uFEFF</span>0.3 oz 10 ml</em></p><p><br></p>")
                         :store.item/category    [:category/path (category-path "home" "bath-and-body")]
                         :store.item/section     (db/tempid :db.part/user -1101)
                         :store.item/photos      (item-photos "dynamic/real/nk4wy5rrzbhlz8dnqotq")
                         :store.item/price       30.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Charcoal + Blood Orange Body Wash"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(35, 31, 32);\">Our natural hand + body wash draws out toxins and impurities of the skin with the detoxifying properties of charcoal. Blood orange, pink grapefruit + lime stimulate and energize. (They smell pretty good too).</span></p><p><br></p><p><span style=\"color: rgb(35, 31, 32);\">Ingredients: Cocos nucifera (coconut soap), monoxide (h2o), (lavender hydrosol), hamamelis (witch hazel), glycerol (vegetable glycerine), Rosmarinus ocinalis (rosemary extract), Camellia sinensis (green tea extract), carbones (charcoal) and/et essential blend/huile essentielles.</span></p><p><br></p><p><em style=\"color: rgb(35, 31, 32);\">8 oz 235 ml</em></p>")
                         :store.item/category    [:category/path (category-path "home" "bath-and-body")]
                         :store.item/section     (db/tempid :db.part/user -1100)
                         :store.item/photos      (item-photos "dynamic/real/rb4odfyhqvkqm7nfdf6s")
                         :store.item/price       38.00M
                         :store.item/skus        (item-skus)}]}
   ;; ------------------------------------- END Midnight Paloma ----------------------------------

   ;; ------------------------------------- BEGIN Farbod ceramics ----------------------------------

   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name    "Farbod Ceramics"
                        :store.profile/cover   (photo "dynamic/real/bysn52pdqnwgdpvvagnj")
                        :store.profile/photo   (photo "dynamic/real/vbaju2lqjvin6rmqojak")
                        :store.profile/tagline "Artisanal ceramics, Handmade with love"}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p>All items will be shipped within 2-3 Business days using Canada post.  </p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/username    "farbodceramics"
    :store/status      {:status/type :status.type/open}
    :store/items       [{:store.item/name        "Geo-pipe"
                         :store.item/description (f/str->bytes "<p>Handmade ceramic pipe, decorated with bubble glaze.</p><p>comfortable handheld size, easy to clean and dishwasher safe.</p>")
                         :store.item/category    [:category/path (category-path "home" "accessories")]
                         :store.item/photos      (item-photos "dynamic/real/kts34pbpce6heeqbxyet")
                         :store.item/price       125.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Mr. Mug"
                         :store.item/description (f/str->bytes "<p>Handmade ceramic mug individually sculpted into faces.</p>")
                         :store.item/category    [:category/path (category-path "home" "accessories")]
                         :store.item/photos      (item-photos "dynamic/real/q8bmu2tjt3854iha3bfu")
                         :store.item/price       56.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Handmade terracotta Bowl"
                         :store.item/description (f/str->bytes "<p>Handmade bowls with bubble glaze,</p><p>Dishwasher/Microwave safe.</p>")
                         :store.item/category    [:category/path (category-path "home" "accessories")]
                         :store.item/photos      (item-photos "dynamic/real/jytgn4dy7o8ohvbzcmzr"
                                                              "dynamic/real/sfmdzek4tvdvg9y3ubzt"
                                                              "dynamic/real/wakkiljddcjvx6yvrpe3")
                         :store.item/price       42.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Ceramic Incense holder"
                         :store.item/description (f/str->bytes "<p>Wheel Thrown mugs with bubble glaze decoration </p>")
                         :store.item/category    [:category/path (category-path "home" "accessories")]
                         :store.item/photos      (item-photos "dynamic/real/tbfqvpyyxnrybmklmxap")
                         :store.item/price       38.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name     "Handmade Bubble Mug"
                         :store.item/category [:category/path (category-path "home" "accessories")]
                         :store.item/photos   (item-photos "dynamic/real/j9usdj5m12rjnv3g8e3k"
                                                           "dynamic/real/gwlaoot8hfpdf0f0fyjd"
                                                           "dynamic/real/vnpfhfe8se4i9xcwyoix")
                         :store.item/price    38.00M
                         :store.item/skus     (item-skus)}]}

   ;; ------------------------------ END Farbod Ceramics --------------------------------

   ;; ----------------------------- BEGIN Willow & Stump Furniture Design ---------------------
   {:db/id             (db/tempid :db.part/user)
    :store/profile     {:store.profile/name          "Willow & Stump Furniture Design"
                        :store.profile/tagline       "big ideas for small spaces"
                        :store.profile/cover         (photo "dynamic/real/etf6ioz4yhny0dgzabdr")
                        :store.profile/photo         (photo "dynamic/real/sbw0mydvxzqykqze4tzk")
                        :store.profile/description   (f/str->bytes "<p>Kaly Ryan and Bram Sawatzky share a passion for woodworking and innovative furniture design. Following graduation from the University of Alberta’s Industrial Design program in 2008, they were both drawn to the creative community in Vancouver.</p><p><br></p><p>Initially inspired by designing for their small living areas, Kaly &amp; Bram started out creating furniture that was multifunctional and space-saving. They quickly realized this was not a unique problem in Vancouver, and they co-founded Willow &amp; Stump Furniture Design in April 2014 to meet the needs of others living in small spaces in the lower mainland and beyond. The pair works with a combination of wood technology and new materials to create designs that are fresh, graceful and functional.</p>")
                        :store.profile/return-policy (f/str->bytes "<p>If you are not satisfied with your purchase, you may contact us within 7 days of receipt for return, store credit, or exchange. If the product you have received is defective or damaged, it must be reported within 7 days of receipt. Goods soiled or damaged by the customer are not eligible for return. All returns must have a return authorization. If you need to return or exchange your purchase, please contact us first for further instructions.</p><p><br></p><p><strong>Refunds (if applicable)</strong></p><p>Once your return is received and inspected, we will send you an email to notify you that we have received your returned item. We will also notify you of the approval or rejection of your refund. If you are approved, then your refund will be processed, and a credit will automatically be applied to your credit card or original method of payment, within a certain amount of days. Unfortunately, we will not refund any postage costs.</p><p><br></p><p><strong>Late or missing refunds (if applicable)</strong></p><p>If you haven’t received a refund yet, first check your bank account again. Then contact your credit card company, it may take some time before your refund is officially posted. Next contact your bank. There is often some processing time before a refund is posted.</p><p>If you’ve done all of this and you still have not received your refund yet, please contact us.</p><p><br></p><p><br></p><p><strong>Sale items (if applicable)</strong></p><p>Only regular priced items may be refunded, unfortunately sale items cannot be refunded.</p><p><br></p><p>&nbsp;</p><p><strong>Shipping</strong></p><p>To return your product, please contact us. You will be responsible for paying for your own shipping costs for returning your item. Shipping costs are non-refundable. Depending on where you live, the time it may take for your exchanged product to reach you may vary. If you are shipping an item with a value of over $100, you should consider using a trackable shipping service or purchasing shipping insurance. We can not guarantee that we will receive your returned item.</p>")}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p>We offer free shipping to the United States &amp; Canada on orders over $100 CAD. For international shipping please contact us.</p><p><br></p><p>Please see individual product descriptions for lead times prior to shipping. Products with no lead time will ship within 3-5 business days. Most items take 2-10 days to arrive after shipping. If you need items to arrive in a certain amount of time please contact us for further information.</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/status      {:status/type :status.type/open}
    :store/username    "willowandstump"
    :store/items       [{:store.item/name        "Traverse Ottoman"
                         :store.item/description (f/str->bytes "<p>The Traverse Convertible Ottoman is a versatile piece perfect for a small or dynamic living space. It functions both as a coffee table and an ottoman, and can transition between the two with ease, eliminating the need for extra pieces of furniture. In addition to its functionality as both a coffee table and a foot rest, the Traverse Ottoman can be used as seating for up to two people and the shelf underneath provides storage.</p><p><br></p><ul><li>Dimensions: 41.5” x 20.5” x 19” (Coffee table height: 16”)</li><li>Material: Solid ash, birch dowels, brass hardware, high quality foam, vegan suede, denim, webbing</li><li>Patterns Available: Crane Deco-Mint, Fish-Peach, Triangle Geometrics</li><li>Cushion cover is removable, spot clean only</li><li>Ships flat, small amount of assembly required</li><li>Made to Order in Vancouver - Lead time 3-4 weeks before shipping</li></ul>")
                         :store.item/category    [:category/path (category-path "home" "furniture")]
                         :store.item/photos      (item-photos "dynamic/real/c7ix8qe8rjtxfd5f52vg"
                                                              "dynamic/real/rrjsoce1lzktu8hzjkoh"
                                                              "dynamic/real/azeg5wn2hbddijn6dbru"
                                                              "dynamic/real/loejrod4volejfgy5vm0"
                                                              "dynamic/real/gdewvjllkwotlws9lhgc")
                         :store.item/price       1395.00M
                         :store.item/skus        (item-skus "Crane Deco-Mint Cushion"
                                                            "Fish-Peach Cushion"
                                                            "Triangle Geometrics Cushion")}
                        {:store.item/name        "Traverse Floor Lamp"
                         :store.item/description (f/str->bytes "<p>The Traverse Floor Lamp is a versatile accent light that functions standing or on a table. The fabric diffuser is available in 4 different patterns.</p><p><br></p><ul><li>Dimensions: 36” x 6” Diameter</li><li>Materials: ash, silk faille, webbing, acrylic, warm white 12V LED strip</li><li>Patterns Available: Soho, Dotted Spheres, Crane-Deco Peach, Pearl Deco</li><li>Fabric is removable, can be updated in the future</li><li>Includes plug-in power supply</li><li>ETL Listed</li><li>Spot clean only</li><li>Made in Vancouver - Current lead time before shipping 1-2 weeks</li></ul>")
                         :store.item/category    [:category/path (category-path "home" "furniture")]
                         :store.item/photos      (item-photos "dynamic/real/w936pyfzuror4wvpx0ut"
                                                              "dynamic/real/uflqxwjeovkcivyzhj2h"
                                                              "dynamic/real/lqh3b3dfnutzemnagqm3"
                                                              "dynamic/real/kpk6qyk7ns55ejmood3q"
                                                              "dynamic/real/xbvx82dwfxmalbrautqm")
                         :store.item/price       795.00M
                         :store.item/skus        (item-skus "Dotted Spheres"
                                                            "Crane-Deco Peach"
                                                            "Soho"
                                                            "Pearl Deco")}
                        {:store.item/name        "Traverse Shelf"
                         :store.item/description (f/str->bytes "<p>The Traverse Shelf is a simple wall unit that provides storage without taking up floor space. Hanging from a single wall hook, the decorative yet sturdy cloth sides of the Traverse Shelf provide a sleek and elegant shelving system to store everything from books to your favourite scotch.</p><p><br></p><ul><li>Dimensions: 23.5\" x 40\"</li><li>Materials: ash, birch dowel, metal hook, heavy cotton twill, webbing</li><li>Patterns: Animal Geo b/w, Animal Shapes</li><li>Mounts by screwing hook into a stud or a toggle bolt into drywall (included)</li><li>Holds up to 50lbs</li><li>Easy assembly, no hardware except for hook</li><li>Ability to change and update side panels</li><li>Great for people who move often - small footprint, only one hole in the wall</li><li>Made in Vancouver - Current lead time before shipping 1-2 weeks</li></ul>")
                         :store.item/category    [:category/path (category-path "home" "furniture")]
                         :store.item/photos      (item-photos "dynamic/real/cnchylnd1etzpr8lerpu"
                                                              "dynamic/real/ufx8twafnev8smhgwegy"
                                                              "dynamic/real/i2scp1o6puuvlaf5aaja"
                                                              "dynamic/real/fgzcnplxvnswsyf3iybp"
                                                              "dynamic/real/yp3lh7jbof3ajzciqbyg")
                         :store.item/price       415.00M
                         :store.item/skus        (item-skus "Animal Geo b/w"
                                                            "Animal Shapes")}]}

   ;; ----------------------------- END Willow and stump

   ;; ------------------------------- BEGIN Joanne Probyn Art --------------------------------------------
   {:db/id             (db/tempid :db.part/user)
    :store/created-at  8
    :store/profile     {:store.profile/name          "Joanne Probyn Art"
                        :store.profile/cover         (photo "dynamic/real/ywtxteygcbualhqtlxt5")
                        :store.profile/photo         (photo "dynamic/real/p2tmixmgxatz10inll4i")
                        :store.profile/description   (f/str->bytes "<p>Joanne Probyn is a Canadian artist who lives and works on Vancouver's Eastside. She is a passionate, prolific creator known for acrylic and mixed media paintings. Probyn explores themes including imperfect beauty, impermanence, flow, unity and journeys of all kinds. She draws inspiration from personal experience, music, yoga, nature and culture. Probyn's paintings, designs and art direction have been recognized with numerous awards individually and collaboratively. Her work has been exhibited and collected locally and internationally.&nbsp;</p>")
                        :store.profile/return-policy (f/str->bytes "<p>The return policy is easy...we guarantee you will be LOVE your new art!</p><p><br></p><p>ORIGINAL PAINTINGS AND GICLEE PRINTS</p><p>We are fully committed to quality products and your satisfaction is 100% guaranteed. If for any reason you are not completely satisfied with your purchase, return&nbsp;it within 48 hours of receipt to receive a full refund for the price of the product. Shipping fees are non-refundable. Frames must be received in the same condition.</p><p><br></p><p>FRAMING</p><p>Frames, when included, are in new or excellent condition (you will be notified of wear, if any). Custom framing is always non-refundable; a careful step-by-step process ensures your satisfaction at every stage.</p>")}
    :store/shipping    {:db/id           (db/tempid :db.part/user)
                        :shipping/policy (f/str->bytes "<p>We deliver worldwide. Canadian shipping and local (FREE) pick-up are available at check-out. US and international shipping available. Contact Joanne for an estimate or to set up an appointment for pick-up at www.probynart.com/contact.</p><p><br></p><p>SHIPPING TYPES:</p><p><br></p><p>Standard:</p><p>All check-out menu shipping options are for Standard–packaging with at least two layers of cardboard and bubble wrap/foam. Preferred suppliers include FedEx Ground, Canada Post and UPS.</p><p><br></p><p>Tube:</p><p>Prints may be sent by rolling a flat, unstretched canvas print and sending in a heavy-duty cardboard tube (same cost as standard).</p><p>Original paintings may be shipped by tube (following review and approval, and after removing from canvas from bars). Additional costs may apply.</p><p><br></p><p>Thin Custom Wood Box:</p><p>A custom-made, thin wood box is ideal for any work traveling long distances where a higher level of protection is required. Price is 2-3 times the costs of standard shipping. Additional charges apply.</p><p><br></p><p>Thick Custom Wood Crate:</p><p>A custom-made, heavy-duty, wooden crate is is ideal for any artwork traveling a long distances and where the highest level of protection is required. Price is 3-6 times the cost of standard shipping. Additional charges apply. Contact Joanne directly at www.probynart.com/contact.</p><p><br></p><p>SHIPPING REFUNDS</p><p><br></p><p>Shipping is non-refundable and return shipping is customer's responsibility. </p><p><br></p><p>DAMAGES</p><p><br></p><p>Shipping damages will be considered for repair. To process, immediately notify the shipping company and email Probyn Art a photograph of the packaging and art damage. Return artwork to Probyn Art in original packaging, if possible.</p><p><br></p><p>If a print is beyond repair, we will replace it with a new print immediately. As prints are created on-demand, allow 7-10 business days plus shipping. </p><p><br></p><p>If a painting is damaged during shipment, you will receive a full refund. We may work with the shipper to come to an agreement. You may choose another painting at 10% off or request a new, commissioned work at 15% off. Allow 6-8 weeks plus shipping for commissions. </p><p><br></p><p>Replacement shipping is discounted a minimum of 30%. </p><p><br></p><p>Damaged art must be returned for assessment. Replacements, refunds and discounts are only applicable when artwork has been returned and reviewed. Delivery times may change may change without notice.</p><p><br></p><p>Contact Joanne at www.probynart.com/contact.</p>")}
    :store/geolocation {:db/id             (db/tempid :db.part/user)
                        :geolocation/title "Vancouver, BC"}
    :store/username    "probynart"
    :store/status      {:status/type :status.type/open}
    :store/sections    [{:db/id               (db/tempid :db.part/user -1200)
                         :store.section/label "Original Art"}
                        {:db/id               (db/tempid :db.part/user -1201)
                         :store.section/label "Limited Edition Giclée Prints"}
                        {:db/id               (db/tempid :db.part/user -1202)
                         :store.section/label "Open Edition Giclée Prints"}]
    :store/items       [{:store.item/name        "Lush"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(34, 34, 34);\">Mixed Media on Canvas</span></p><p><br></p><p><span style=\"color: rgb(34, 34, 34);\">20″ x 20″</span></p><p><br></p><p>This is an original, one-of-a-kind painting. I update availability daily. This means (on rare occasions) listed work may have already been sold on another platform on the same day. To confirm availability, contact me directly at your earliest convenience. Commissions available by request.</p><p><br></p><p>$699 plus shipping (if applicable) and 5% tax. US buyers tax-free!</p><p><br></p><p>Visit www.probynart.com to view this and other works.</p><p><br></p><p><strong style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\"><em>Vancouver locals:</em></strong></p><p><em style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\">I encourage you to select&nbsp;Local Pickup at checkout to meet me to get your order. I am also more than happy to schedule a time for you to come by my studio to view this and other works of art. :-)</em></p>")
                         :store.item/category    [:category/path (category-path "art")]
                         :store.item/section     (db/tempid :db.part/user -1200)
                         :store.item/photos      (item-photos "dynamic/real/z1ji55f95jqpaetdlaze")
                         :store.item/price       699.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Together"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(34, 34, 34);\">Mixed Media</span></p><p><span style=\"color: rgb(34, 34, 34);\">\uFEFF12″ x 12″</span></p><p><br></p><p>This is an original, one-of-a-kind painting. I update availability daily. This means (on rare occasions) listed work may have already been sold on another platform on the same day. To confirm availability, contact me directly at your earliest convenience. Commissions available by request.</p><p><br></p><p>$439 plus shipping (if applicable) and 5% tax. US buyers tax-free!</p><p><br></p><p>Visit www.probynart.com to view this and other works.</p><p><br></p><p><strong style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\"><em>Vancouver locals:</em></strong></p><p><em style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\">I encourage you to select&nbsp;Local pickup at checkout to meet me to get your order. I am also more than happy to schedule a time for you to come by my studio to try on the pieces. :-)</em></p><p><br></p>")
                         :store.item/category    [:category/path (category-path "art")]
                         :store.item/section     (db/tempid :db.part/user -1200)
                         :store.item/photos      (item-photos "dynamic/real/xhhbbyjvyrm7womyieyu")
                         :store.item/price       439.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Coming Home"
                         :store.item/description (f/str->bytes "<p><span style=\"color: rgb(34, 34, 34);\">Mixed Media on Canvas</span></p><p><span style=\"color: rgb(34, 34, 34);\">10″ x 10″</span></p><p><br></p><p>This is an original, one-of-a-kind painting. I update availability daily. This means (on rare occasions) listed work may have already been sold on another platform on the same day. To confirm availability, contact me directly at your earliest convenience. Commissions available by request.</p><p><br></p><p>$349 plus shipping (if applicable) and 5% tax. US buyers tax-free!</p><p><br></p><p>Visit www.probynart.com to view this and other works.</p><p><br></p><p><strong style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\"><em>Vancouver locals:</em></strong></p><p><em style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\">I encourage you to select&nbsp;Local Pickup at checkout to meet me to get your order. I am also more than happy to schedule a time for you to come by my studio to view this and other works of art. :-)</em></p><p><br></p>")
                         :store.item/category    [:category/path (category-path "art")]
                         :store.item/section     (db/tempid :db.part/user -1200)
                         :store.item/photos      (item-photos "dynamic/real/ee6m3ars9idpaquo0gdt")
                         :store.item/price       349.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Orchid Blessings (Limited Edition)"
                         :store.item/description (f/str->bytes "<p>Announcing the public release of Probyn's first limited edition giclée print, Orchid Blessings. The original painting is sold!</p><p><br></p><p>Limited edition means there will be a maximum of 25, 16\" x 20\" canvas prints ever made–and that means forever! Your high quality canvas print includes an original artist signature and will always be enjoyed as a rare work of art. Limited edition prints look beautiful and offer an affordable way to collect (and invest in) art that typically increases in value–unlike standard prints.</p><p><br></p><p>• 20\" x 16\" (smaller available for same price)</p><p>• Archival, high quality canvas print</p><p>• Original artist signature</p><p>• 1.5 inch deep stretcher bars (no framing needed)</p><p>• Certified limited edition of 25 (plus artist proofs)</p><p><br></p><p>Allow 5-10 business days for high quality, print-on-demand production plus pick-up or shipping time.</p><p><br></p><p>Availability as of July 16, 2017:</p><p>20-25 prints remaining*</p><p><br></p><p>*Please note limited edition prices will increase with reduced availability. Buy now for the best price and investment entry point.</p><p><br></p><p>$259 plus shipping (if applicable) and 5% tax. US buyers tax-free!</p><p><br></p><p>Visit www.probynart.com to view this and other works.</p><p><br></p><p><strong style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\"><em>Vancouver locals:</em></strong></p><p><em style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\">I encourage you to select&nbsp;Local Pickup at checkout to meet me to get your order. I am also more than happy to schedule a time for you to come by my studio to view this and other works of art. :-)</em></p>")
                         :store.item/category    [:category/path (category-path "art")]
                         :store.item/section     (db/tempid :db.part/user -1201)
                         :store.item/photos      (item-photos "dynamic/real/hcuvmx2rdl1rzkg6tk2q")
                         :store.item/price       259.00M
                         :store.item/skus        (item-skus)}
                        {:store.item/name        "Zen Garden (Open Edition)"
                         :store.item/description (f/str->bytes "<p>Announcing the public release of Probyn's first open edition giclée print, Zen Garden! The original painting is sold.</p><p><br></p><p>• Four sizes to choose from</p><p>• Archival, high quality canvas print</p><p>• Original artist signature on side</p><p>• 1.5 inch deep stretcher bars (no framing needed)</p><p><br></p><p>Allow 5-10 business days for high quality, print-on-demand production plus shipping time.</p><p><br></p><p>12\" x 12\" $159</p><p>16\" x 16\" $199</p><p>20\" x 20\" $249</p><p>24\" x 24\" $299</p><p><br></p><p>Print cost (above) plus shipping (if applicable) and 5% tax. US buyers tax-free!</p><p><br></p><p>Visit www.probynart.com to view this and other works.</p><p><br></p><p><strong style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\"><em>Vancouver locals:</em></strong></p><p><em style=\"color: rgb(89, 88, 87); background-color: rgb(254, 254, 254);\">I encourage you to select&nbsp;Local Pickup at checkout to meet me to get your order. I am also more than happy to schedule a time for you to come by my studio to view this and other works of art, joanne@probynart.com. :-)</em></p>")
                         :store.item/category    [:category/path (category-path "art")]
                         :store.item/section     (db/tempid :db.part/user -1202)
                         :store.item/photos      (item-photos "dynamic/real/bmdlc27d9hvjavwetllj")
                         :store.item/price       159.00M
                         :store.item/skus        (item-skus)}]}])

(defn stores-with-item-created-at [stores]
  (map-indexed
    (fn [store-idx store]
      (update store :store/items
              (fn [items]
                (map-indexed (fn [item-idx item]
                               (assoc item :store.item/created-at (+ (* store-idx 100) item-idx)))
                             items))))
    stores))

(defn mock-stores []
  (-> (create-mock-stores)
      (stores-with-item-created-at)))

(defn countries []
  (let [country-data (json/read-str (slurp (io/resource "private/country-data.json")) :key-fn keyword)
        continents (:continents country-data)]
    (debug "Countries: " (:continents country-data))
    (map (fn [[code country]]
           {:db/id             (db/tempid :db.part/user)
            :country/code      (name code)
            :country/name      (:name country)
            :country/continent {:db/id          (db/tempid :db.part/user)
                                :continent/code (:continent country)
                                :continent/name (get continents (keyword (:continent country)))}})
         (:countries country-data))))

(defn sulo-localities []
  [{:sulo-locality/title "Vancouver, BC"
    :sulo-locality/path  "yvr"
    :sulo-locality/photo (photo "static/landing-vancouver-3")}

   {:sulo-locality/title "Montréal, QC"
    :sulo-locality/path  "yul"
    :sulo-locality/photo (photo "static/landing-montreal")}])

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
  (let [live-stores 0

        categories (mock-categories3)
        stores (->> (mock-stores)
                    (map-indexed #(assoc %2 :store/created-at %1)))
        chats (mock-chats stores)
        live-streams (mock-streams (take live-stores stores) :stream.state/live)
        streams (mock-streams (drop live-stores stores) :stream.state/offline)
        countries (countries)]
    (db/transact conn (concat categories (sulo-localities)))
    (debug "Categories added")
    (db/transact conn (concat stores live-streams streams chats countries))
    (debug "Stores with items, chats and streams added")))