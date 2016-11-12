(ns eponai.server.import.ods.tags
  "Put the tag generating in its own namespace
  so we can more easily change which tags get
  generated from our Open Office expense's titles
   and dates."
  (:require [clojure.string :as s]
            [clojure.set :as set]
            [clj-time.format :as f]
            [clj-time.core :as time]
            [clj-time.periodic :as time-period]
            [datascript.core :as d]
            [eponai.common.format :as eponai.format]
            [eponai.common.format.date :as date]))

(declare title->tags)
(declare locations)

(defn time-range
  "Return a lazy sequence of DateTime's from start to end, incremented
  by 'step' units of time."
  [start end step]
  (let [inf-range (time-period/periodic-seq start step)
        below-end? (fn [t] (time/within? (time/interval start end)
                                         t))]
    (take-while below-end? inf-range)))

(defn day-range [start end]
  (time-range start end (time/days 1)))

(defn location-entities-per-date []
  (mapcat (fn [{:keys [country city from to]}]
            (assert (and country city from to)
                    {:co country :ci city :from from :to to})
            (map (fn [d] {:country  country
                          :city     city
                          :date/ymd (:date/ymd (date/date-map d))})
                 (day-range from to))) locations))

(def locations-db (memoize
                    (fn []
                      (d/db-with (d/db (d/create-conn))
                                 (location-entities-per-date)))))

(defn date->tags [ymd-date]
  {:pre  [(string? ymd-date)]
   :post [(= 2 (count %))]}
  (let [db (locations-db)]
    (-> (d/q '{:find  [?e .]
               :in    [$ ?ymd-date]
               :where [[?e :date/ymd ?ymd-date]]}
             db
             ymd-date)
        (->> (d/entity db))
        (select-keys [:country :city])
        (vals)
        (->> (mapv (fn [s]
                     {:pre [(string? s)]}
                     (s/lower-case s)))))))

(def ^:private category->tags
  {:transportation #{:bicycle :ferry :taxi :uber :train :flight
                     :transportation}
   :recreational   #{:tickets :cinema :alcohol :beer :wine :shisha
                     :bowling :concert :cocktail :club}
   :accomodation   #{:hotel :airbnb :hostel :apartment :accomodation}
   :bills          #{:subscription :insurance :bills}
   :food           #{:groceries :food :lunch :dinner :breakfast
                     :reasturant :fika :sweets :snacks :refreshment
                     :coffee}
   :appearance     #{:clothes :makeup :beauty}
   :health         #{:pharmacy :shampoo :healthcare :fitness}})

(defn keyword! [x]
  (cond-> x (string? x) (keyword)))

(def tag-keyword? #(or (string? %) (keyword? %)))

(defn tags->category-keyword [tag-keywords]
  {:pre [(every? tag-keyword? tag-keywords)]}
  (some (fn [[category tag-set]]
          (when (some tag-set (map keyword! tag-keywords))
            category))
        (seq category->tags)))

(defn remove-category-tags [category-keyword tag-keywords]
  {:pre  [(or (nil? category-keyword) (keyword? category-keyword))
          (every? tag-keyword? tag-keywords)]
   :post [(every? tag-keyword? tag-keywords)]}
  (cond->> tag-keywords
           (some? category-keyword)
           (into #{} (remove #(= category-keyword (keyword! %))))))

(defn tags->tags [tags]
  {:pre  [(set? tags)]
   :post [(set? tags)]}
  (let [tag->tags {:beer      [:alcohol]
                   :wine      [:alcohol]
                   :cocktail  [:alcohol]
                   :coffee    [:fika]
                   :lunch     [:food]
                   :dinner    [:food]
                   :fastfood  [:food]
                   :hotel     [:accomodation]
                   :airbnb    [:accomodation]
                   :hostel    [:accomodation]
                   :apartment [:accomodation]
                   :bicycle   [:transport]
                   :ferry     [:transport]
                   :taxi      [:transport]
                   :uber      [:transport]
                   :train     [:transport]
                   :flight    [:transport]}]
    (reduce (fn [tags t]
              (apply conj tags (get tag->tags t)))
            tags
            tags)))

(defn generate-tag-keywords
  "Given title, date, amount and currency, generate some tags."
  [{:keys [transaction/title transaction/date]}]
  (let [title-tags (get title->tags title)
        _ (when (not (some? title-tags))
            (throw (ex-info (str "No tags for title: \"" title "\" - date: " date)
                            {:title title :chars (seq title)})))
        date (:date/ymd date)
        date-tags (date->tags date)
        _ (when (not (some? date-tags))
            (throw (ex-info (str "No tags for date: " date)
                            {:date date})))]
    (into #{}
          (map keyword!)
          (tags->tags (set/union (set title-tags) (set date-tags))))))

(def locations [{:country "Croatia" :city "Dubrovnik"
                 :from    (time/date-time 2015 9 9)
                 :to      (time/date-time 2015 9 12)}
                {:country "Italy" :city "Bari"
                 :from    (time/date-time 2015 9 12)
                 :to      (time/date-time 2015 9 13)}
                {:country "Albania" :city "Durres"
                 :from    (time/date-time 2015 9 13)
                 :to      (time/date-time 2015 9 24)}
                {:country "Thailand" :city "Chiang Mai"
                 :from    (time/date-time 2015 9 24)
                 :to      (time/date-time 2015 10 22)}
                {:country "Sweden" :city "Stockholm"
                 :from    (time/date-time 2015 10 22)
                 :to      (time/date-time 2015 12 7)}
                {:country "Thailand" :city "Koh Lanta"
                 :from    (time/date-time 2015 12 7)
                 :to      (time/date-time 2015 12 23)}
                {:country "Thailand" :city "Chiang Mai"
                 :from    (time/date-time 2015 12 23)
                 :to      (time/date-time 2016 1 16)}
                {:country "Vietnam" :city "Ho Chi Minh City"
                 :from    (time/date-time 2016 1 16)
                 :to      (time/date-time 2016 2 7)}
                {:country "Malaysia" :city "Kuala Lumpur"
                 :from    (time/date-time 2016 2 7)
                 :to      (time/date-time 2016 4 5)}
                {:country "Japan" :city "Osaka"
                 :from    (time/date-time 2016 4 5)
                 :to      (time/date-time 2016 5 5)}
                {:country "South Korea" :city "Seoul"
                 :from    (time/date-time 2016 5 5)
                 :to      (time/date-time 2016 5 27)}
                {:country "USA" :city "New York"
                 :from    (time/date-time 2016 5 27)
                 :to      (time/date-time 2016 6 27)}
                {:country "Sweden" :city "Stockholm"
                 :from    (time/date-time 2016 6 27)
                 :to      (time/date-time 2016 7 12)}
                {:country "Sweden" :city "Bohuslän"
                 :from    (time/date-time 2016 7 12)
                 :to      (time/date-time 2016 8 12)}
                {:country "Sweden" :city "Stockholm"
                 :from    (time/date-time 2016 8 12)
                 :to      (time/date-time 2016 8 14)}
                {:country "Sweden" :city "Skellefteå"
                 :from    (time/date-time 2016 8 14)
                 :to      (time/date-time 2016 8 20)}
                {:country "Sweden" :city "Stockholm"
                 :from    (time/date-time 2016 8 20)
                 :to      (time/date-time 2016 9 7)}
                {:country "Spain" :city "Seville"
                 :from    (time/date-time 2016 9 7)
                 :to      (time/date-time 2016 11 30)}])

(def title->tags
  {"Cash"                            [:cash]
   "Transfer"                        []
   "Dinner"                          [:dinner]
   "Coffee"                          [:coffee]
   "Lunch"                           [:lunch]
   "Dinner Pizza"                    [:dinner :pizza]
   "Lunch Port"                      [:lunch]
   "Luggage"                         [:travel :essentials]
   "Fika"                            [:sweets]
   "Market"                          [:groceries]
   "Cash ($5 fee)"                   [:cash :fee]
   "Cash (-$5 fee)"                  [:cash :fee]
   "Beach chairs"                    [:beach]
   "Corn"                            [:sweets]
   "Chips"                           [:sweets :chips]
   "Water"                           [:groceries]
   "Comb"                            [:essentials]
   "Fruit"                           [:groceries]
   "Ice tea"                         [:refreshment]
   "Hotell"                          [:accomodation :hotel]
   "Taxi"                            [:taxi]
   "Water+Sprite (ATH)"              [:refreshment]
   "M&Ms"                            [:snacks]
   "Lunch Abu Dhabi"                 [:lunch]
   "Ramen BKK"                       [:lunch]
   "Taxi Chiang Mai"                 [:taxi]
   "Taxi BKK"                        [:taxi]
   "Food"                            [:food]
   "Night out"                       [:party :beer]
   "7eleven"                         [:sweets]
   "sleven"                          [:sweets]
   "Ice cream"                       [:sweets]
   "Lunch + Coffee"                  [:lunch :coffee]
   "Lunch+coffee"                    [:lunch :coffee]
   "Dinner+cola"                     [:dinner]
   "Dinner+beer"                     [:dinner :beer]
   "Dinner+drinks"                   [:dinner :cocktail]
   "Songtaew"                        [:taxi]
   "Cat cafe"                        [:sweets :coffee]
   "Work cafe"                       [:coffee]
   "Wine"                            [:wine]
   "Tea"                             [:tea]
   "Bowling"                         [:bowling]
   "Beers"                           [:beer]
   "Ritblock"                        [:essentials]
   "Bread"                           [:groceries]
   "Pool party"                      [:cocktail :beer]
   "Taxi airport"                    [:taxi]
   "Pizza"                           [:food]
   "Cola"                            [:refreshment :sweets]
   "Utekväll"                        [:party]
   "Kaffe"                           [:coffee]
   "Retro"                           [:beer]
   "Retro bar"                       [:beer]
   "Storstad"                        [:beer]
   "Godis"                           [:sweets]
   "Eftersläpp"                      [:party]
   "Vin"                             [:wine]
   "Blue bandana"                    [:clothes]
   "outfit det går bra nu"           [:clothes]
   "Beefeater inn"                   [:beer]
   "Chai latte"                      [:coffee]
   "Lantchips"                       [:sweets :chips]
   "Sabis"                           [:groceries]
   "Operation"                       [:healthcare]
   "Iskaffe"                         [:coffee]
   "Bio"                             [:cinema]
   "Bio chips"                       [:cinema :sweets :chips]
   "Bio-snacks"                      [:cinema :sweets]
   "Plankstek+öl"                    [:dinner]
   "Soutside"                        [:beer]
   "HTL öl"                          [:beer]
   "Soda stream"                     [:groceries]
   "Hemköp"                          [:groceries]
   "Ica edsbro"                      [:groceries]
   "Landet"                          []
   "Balsam"                          [:essentials]
   "Sushi"                           [:food]
   "Linn date"                       [:beer]
   "Felicia öl"                      [:beer]
   "Lunch HTL"                       [:lunch]
   "Lunch Elin"                      [:lunch]
   "Espresso house"                  [:coffee]
   "Thai Lunch"                      [:lunch]
   "Thai lunch"                      [:lunch]
   "Kaffe osv"                       [:coffee]
   "Lunch Buffe"                     [:lunch]
   "Clas ohlzhon"                    [:electronics]
   "Foam kaffe"                      [:coffe]
   "Subway lunch"                    [:lunch :fastfood]
   "Konsum"                          [:groceries]
   "Thai Middag"                     [:dinner]
   "Burger king"                     [:fastfood :burgerking]
   "Thai middag"                     [:dinner]
   "Chips etc"                       [:chips :sweets]
   "Klang kaffe"                     [:coffee]
   "Subway dinner"                   [:fastfood :dinner]
   "Sushi essingen"                  [:food]
   "Lunch donken"                    [:lunch :fastfood]
   "Lunch Pascha"                    [:lunch]
   "Lunch thai"                      [:lunch]
   "Foto visum"                      [:essentials]
   "Lunch Kebab"                     [:lunch :fastfood]
   "Onsdagspub"                      [:beer :cocktail]
   "Cafe60"                          [:coffee]
   "Lunch Dox"                       [:coffee :lunch]
   "HTL"                             [:coffee]
   "Lunch Sten Sture"                [:lunch]
   "Frukost Il caffe"                [:breakfast]
   "Donken"                          [:fastfood]
   "Flottis"                         [:fastfood]
   "Valhallagrillen"                 [:fastfood]
   "Flux"                            [:apps]
   "Max"                             [:fastfood]
   "Vatten"                          [:refreshment]
   "Irish Embassy"                   [:beer :food]
   "Bucket"                          [:alcohol :bucket]
   "Towels"                          [:essentials]
   "Drinks"                          [:cocktail]
   "Lunch Papaya"                    [:lunch]
   "Chips+solkräm"                   [:essentials :chips :sweets]
   "Snacks irish"                    [:dinner]
   "Happy drinks"                    [:cocktail]
   "Beer @ kohub"                    [:beer]
   "Pangea bucket"                   [:bucket :alcohol]
   "Irish dinner"                    [:dinner]
   "Cafe"                            [:coffee]
   "Living room cafe"                [:coffee]
   "Chocolate"                       [:sweets]
   "Beach bar"                       [:beach :alcohol]
   "Subway"                          [:fastfood]
   "Shop"                            []
   "Lunch+Coffee"                    [:lunch :coffee]
   "Coffee+Chocolate"                [:coffee :sweets]
   "Laundry"                         [:laundry]
   "East coffee"                     [:coffee]
   "Beer"                            [:beer]
   "Pharmacy"                        [:essentials :healthcare]
   "Elastics"                        [:workout]
   "Salad concept"                   [:dinner]
   "Dinner Cafe Luvv"                [:dinner]
   "Dinner Japan"                    [:dinner]
   "Lunch Maya"                      [:lunch]
   "Cainito Lunch+Coffee"            [:lunch :coffee]
   "Breakfast"                       [:breakfast]
   "Karre"                           [:coffee]
   "Market Fruit"                    [:groceries]
   "Betta's house"                   [:coffee]
   "Cards+chips"                     [:chips :sweets]
   "Coke"                            [:refreshment :sweets]
   "Dinner Echo"                     [:dinner]
   "Wine+beer"                       [:wine :beer]
   "Homemade"                        [:food]
   "Dinner homemade"                 [:dinner]
   "Popcorn"                         [:sweets]
   "Cinema"                          [:cinema]
   "Lunch local"                     [:lunch]
   "Thai tea"                        [:tea]
   "Lunch bk"                        [:fastfood :lunch]
   "Soda"                            [:refreshment]
   "Museum"                          [:tourism]
   "Cookies"                         [:sweets]
   "Coffee+Dinner"                   [:coffee :dinner]
   "Chips cookies"                   [:chips :sweets]
   "Everything I.d. Cafe"            [:lunch :dinner :coffee :sweets]
   "I.d. Cafe"                       [:lunch :dinner :coffee :sweets]
   "Smoothie"                        [:refreshment :sweets]
   "Lunch+smoothie"                  [:lunch :sweets]
   "Loft cafe"                       [:cafe :coffee]
   "Twix"                            [:sweets]
   "Pho Airport"                     [:lunch]
   "BK airport"                      [:fastfood]
   "Uber"                            [:uber :taxi]
   "Bar"                             [:alcohol]
   "Groceries"                       [:groceries]
   "Mamma betalar"                   [:thanksmom]
   "El adapter"                      [:essentials :electronics]
   "Cake"                            [:sweets]
   "Tickets Death cab"               [:concert]
   "Beers and dinner"                [:dinner :beer]
   "Beers KL live"                   [:beer]
   "Mask"                            [:cosmetics]
   "Starbucks"                       [:coffee]
   "Mocha"                           [:coffee]
   "Thai"                            [:food]
   "Milk"                            [:groceries]
   "MyNews"                          [:groceries]
   "Milk+Chips"                      [:groceries :chips :sweets]
   "Milk+chips"                      [:groceries :chips :sweets]
   "Lunch sushi"                     [:lunch]
   "Hotel coffee"                    [:coffee]
   "Lunch T.G.I"                     [:lunch]
   "Shisha"                          [:shisha]
   "Ipren"                           [:healthcare :essentials]
   "Schwarma"                        [:food]
   "Dome lunch"                      [:lunch]
   "Shisha meze"                     [:shisha :dinner]
   "TGI"                             [:lunch]
   "Lunch Dome"                      [:lunch]
   "Marinis on 57"                   [:shisha :wine]
   "Airport breakfast"               [:breakfast]
   "Train tickets"                   [:train]
   "KitKat"                          [:sweets]
   "Family mart"                     [:groceries]
   "Bananas"                         [:groceries]
   "Ramen"                           [:food]
   "Batteries"                       [:essentials]
   "Octupus balls"                   [:food]
   "C.C lemon"                       [:refreshment]
   "Manga"                           [:book]
   "Hot dog + coffee"                [:coffee :lunch]
   "BarShisha"                       [:shisha :alcohol]
   "Bar15minutes"                    [:bar :cocktails]
   "1dollar store"                   [:clothes]
   "Train tickets (kyoto)"           [:train]
   "Refreshments"                    [:refreshment]
   "Zoo"                             [:tourism]
   "Vending machine"                 [:refreshment :sweets]
   "Tully's"                         [:coffee]
   "Kebab"                           [:fastfood]
   "Drink"                           [:refreshment]
   "Train Osaka"                     [:train]
   "Train Seoul"                     [:train]
   "Bank fee"                        [:fee]
   "AtoZ cafe"                       [:coffee]
   "Sandwich coffe"                  [:coffee :food]
   "Sandwiches"                      [:food]
   "Glass"                           []
   "Candy"                           [:sweets]
   "Burgers"                         [:fastfood]
   "Bar+shisha"                      [:shisha :beer]
   "Russin"                          [:sweets]
   "Flight ARN to Dubrovnik"         [:flight]
   "Appartment Durres"               [:apartment]
   "Hotel Croatia"                   [:hotel]
   "Hotel Chiang Mai"                [:hotel]
   "Ferry Dubrovnik to Bari"         [:ferry]
   "Ferry Bari to Durres"            [:ferry]
   "Flight Albania to BKK"           [:flight]
   "Flight BKK to Chiang Mai"        [:flight]
   "Hotel Chiang Mai 1d"             [:hotel]
   "Domain"                          [:business]
   "T-Mobile"                        [:subscription]
   "Netflix"                         [:subscription]
   "Github"                          [:subscription :business]
   "Internet Chiang Mai"             [:internet]
   "Pants Diana"                     [:clothes]
   "Shoes+Hats etc"                  [:clothes]
   "Jacket"                          [:clothes]
   "Diana stuff"                     []
   "Flight BKK to ARN"               [:flight]
   "Flight Chiang Mai to BKK"        [:flight]
   "Nike Dri-fit Petter"             [:clothes]
   "Pants for Diana"                 [:clothes]
   "Gift Lilian"                     [:gift]
   "Hälinlägg"                       [:healthcare :essentials]
   "SL-kort"                         [:train]
   "Fitness24seven"                  [:fitness]
   "Go pro selfie stick"             [:electronics]
   "Hygiene"                         [:essentials]
   "Thai visum"                      [:essentials :visa]
   "Flight ARN to BKK"               [:flight]
   "Kohub apartment"                 [:apartment]
   "Flight BKK to Krabi"             [:flight]
   "Taxi Krabi to Kohub"             [:taxi]
   "Bicycles"                        [:bicycle]
   "Boat trip kohub"                 [:tourism]
   "Swimsuits"                       [:clothes :beach]
   "Flight KBV to Chiang Mai"        [:flight]
   "Chiang Mai Housing"              [:apartment]
   "The Dome Residence"              [:hotel]
   "Taxi Kohub to Krabi"             [:taxi]
   "Flight DMK to SGN"               [:flight]
   "Hotel Vietnam"                   [:hotel]
   "Yoga mat"                        [:fitness]
   "Flight CNX to DMK"               [:flight]
   "Flight SGN to KUL"               [:flight]
   "Jeans Diana"                     [:clothes]
   "Shirt Diana"                     [:clothes]
   "Chinos Petter"                   [:clothes]
   "Power adapter"                   [:electronics :essentials]
   "Taxi to KLCC"                    [:taxi]
   "Airbnb Malaysia"                 [:apartment]
   "Face cream Diana"                [:cosmetics]
   "Apartment Malaysia"              [:apartment]
   "Flight KL to Osaka"              [:flight]
   "Apartment Osaka"                 [:apartment]
   "Flight Osaka to Seoul"           [:flight]
   "Apartment Seoul"                 [:apartment]
   "Flight Seoul to NYC"             [:flight]
   "Apartment NYC"                   [:apartment]
   "Ankle weights + socks"           [:clothes :fitness]
   "Jewlery"                         [:clothes]
   "Shirt Petter"                    [:clothes]
   "Necklace"                        [:clothes]
   "Grapes"                          [:groceries]
   "gs25"                            [:pharmacy :healthcare]
   "Mystik"                          [:club]
   "Mystik drinks"                   [:alcohol]
   "Coke+water"                      [:refreshment]
   "Snacks (cookies)"                [:snacks]
   "Grapes+nudlar"                   [:groceries]
   "Sandwich"                        [:food]
   "Train at airport"                [:transport :train]
   "Chocolate cake at airport"       [:snacks]
   "Lunch Beijing"                   [:food]
   "Water Beijing"                   [:refreshment]
   "Train"                           [:transport :train]
   "Falafel"                         [:food]
   "Water +Nutmix (Jamaica station)" [:refreshment :snacks]
   "Water+Nutmix"                    [:refreshment :snacks]
   "Hookah dinner"                   [:food :shisha]
   "Groceries+Walgreens"             [:groceries]
   "Snacks Chips"                    [:snacks]
   "Chewing gum"                     [:essentials]
   "Pringles"                        [:snacks]
   "Brows"                           [:beauty :personalcare]
   "Tostitos"                        [:snacks]
   "Pergola"                         [:food :alcohol]
   "Wines"                           [:alcohol :wine]
   "Systembolaget"                   [:alcohol]
   "Shot"                            [:alcohol]
   "Lunch Thai"                      [:food]
   "Bjäst"                           [:groceries]
   "Klorhexidin"                     [:healthcare]
   "Vegan drinks"                    [:refreshment]
   "Pashadelli"                      [:food]
   "Alcohol"                         [:alcohol]
   "Bensin"                          [:gas :transport]
   "Car food"                        [:food :snacks]
   "Fuel"                            [:transport :gas]
   "Clothes wedding"                 [:clothes :wedding]
   "Key"                             [:essentials]
   "Chocolate balls"                 [:snacks]
   "Bar w/ Claudia"                  [:alcohol]
   "Apotek"                          [:pharmacy :healthcare]
   "Clas ohlson"                     [:electronics]
   "Lunch Arlanda"                   [:food :airport]
   "Dinner Seville"                  [:food]
   "Coffe grinder"                   [:coffee]
   "Scathedral"                      [:culture :tourism]
   "Win"                             [:wine :alcohol]
   "Flight"                          [:flight :transport]
   "Apartment"                       [:accomodation :apartment]
   "Hotel"                           [:accomodation :hotel]
   "Insurance"                       [:insurance]
   "Clothes"                         [:clothes]
   "Govball Tix"                     [:tickets :festival]
   "Apt NYC"                         [:apartment :accomodation]
   "Flight NYC to ARN"               [:flight :transport]
   "ResetVegan"                      [:vegan :lifestyle]
   "Metro card"                      [:metro :transport :train]
   "Food processor"                  [:electronics]
   "Hair stuff"                      [:beauty :essentials]
   "Backpack"                        [:clothes]
   "Magic bullet"                    [:electronics]
   "Face stuff"                      [:beauty :essentials]
   "Bikinis and such"                [:clothes]
   "Metro"                           [:metro :train :transport]
   "Ipad cover"                      [:electronics]
   "Shirt petter"                    [:clothes]
   "Shampoos"                        [:essentials]
   "SJ train"                        [:transport :train]
   "Myrorna Wedding cloth"           [:wedding :clothes]
   "Claudia gift"                    [:gift]
   "Dentist"                         [:dentist :healthcare]
   "Wax"                             [:beauty :healthcare]
   "Claudia fest"                    [:alcohol]
   "Tandhygienist"                   [:dentist :healthcare]
   "SL refill"                       [:train :metro :transport]
   "Vegan Reset"                     [:vegan]
   "Wedding house"                   [:accomodation :wedding]
   "Tandläkare"                      [:dentist :healthcare]
   "Wedding"                         [:wedding]
   "Doctor"                          [:healthcare :doctor]
   "Income September 2015"           [:income]
   "Income October 2015"             [:income]
   "Income November 2015"            [:income]
   "Income December 2015"            [:income]
   "Income January 2016"             [:income]
   "Income February 2016"            [:income]
   "Income March 2016"               [:income]
   "Income April 2016"               [:income]
   "Income May 2016"                 [:income]
   "Income June 2016"                [:income]
   "Income July 2016"                [:income]
   "Income August 2016"              [:income]
   "Income September 2016"           [:income]
   "Income October 2016"             [:income]
   "Second Apartment Seville"        [:airbnb]
   "Coffemaker"                      [:coffee]
   "Beans"                           [:coffee]
   "Espresso"                        [:coffee]
   "Mas"                             [:groceries]
   "Ugglan"                          [:beer]
   "Ugglan inträde"                  [:cover]
   "Kungshallen"                     [:food]
   "Makeup"                          [:beauty]
   "Asian groceries"                 [:groceries]
   "Aquarium"                        [:tourism]
   "Pub crawl"                       [:alcohol]
   "McDoncals"                       [:food]
   "Mcdonalds"                       [:food]
   "Real Alcazar entrance"           [:cover :tourism]
   "Audio guide"                     [:tourism]
   "Asian Groceries"                 [:groceries]
   "Mas groceries"                   [:groceries]
   "Torch"                           [:coffee]
   })

(def ^:dynamic *default-currency* nil)

(defn left-side-of-expenses []
  {:post [(vector? %)]}
  (letfn [(tx
            ([date title amount]
             (tx date title amount (or *default-currency* "USD")))
            ([date title amount curr]
             (assert (and (string? date) (string? title) (string? curr))
                     (str "Wasn't all strings. Was: " date ", " title ", " curr))
             (assert (or (decimal? amount)
                         (integer? amount)
                         (and (string? amount) (not= "USD" curr)))
                     (str "Wasn't decimal, int or stuff. Was: " amount
                          "for curr: " curr ", title: " title " date: " date))
             (as-> {"Date" date "Product" title}
                   transaction
                   (if (= "USD" curr)
                     (assoc transaction "USD" (str "$" amount))
                     (assoc transaction "USD" "$0.00"
                                        curr amount)))))
          (income [date title amount]
            (assoc (tx date title amount)
              :transaction/type :transaction.type/income))]
    (let [september2015 (let [first "09/09/15"]
                          [(income first "Income September 2015" 2000)
                           (tx first "Flight ARN to Dubrovnik" 500)
                           (tx "09/13/15" "Appartment Durres" 187)
                           (tx first "Hotel Croatia" 100)
                           (tx "09/25/15" "Hotel Chiang Mai" 112.14M)
                           (tx "09/12/15" "Ferry Dubrovnik to Bari" 262.50M)
                           (tx "09/13/15" "Ferry Bari to Durres" 180.80M)
                           (tx "09/25/15" "Flight Albania to BKK" 1234)
                           (tx "09/25/15" "Flight BKK to Chiang Mai" 116.13M)
                           (tx "09/25/15" "Hotel Chiang Mai 1d" 14.73M)
                           (tx "09/17/15" "Domain" 2.53M)
                           (tx first "T-Mobile" 118)])

          october2015 (let [first "10/01/15"]
                        [(income first "Income October 2015" 2000)
                         (tx first "Hotel Chiang Mai" 367.75M)
                         (tx "10/22/15" "Flight BKK to ARN" 540)
                         (tx first "T-Mobile" 118)
                         (tx "10/22/15" "Flight Chiang Mai to BKK" 85)
                         (tx first "Nike Dri-fit Petter" 60.83M)
                         (tx first "Domain" 2.53M)
                         (tx first "Pants for Diana" 3.32M)
                         (tx first "Github" 25)
                         (tx "10/24/15" "Gift Lilian" 16.04M)
                         (tx "10/26/15" "Hälinlägg" 15.54M)
                         (tx first "Netflix" 7.99M)])
          november2015 (let [first "11/01/15"]
                         [(income first "Income November 2015" 2000)
                          (tx first "Domain" 30.32M)
                          (tx first "T-Mobile" 118)
                          (tx first "SL-kort" "1,570.00 kr" "SEK")
                          (tx first "Fitness24seven" "569.00 kr" "SEK")
                          (tx first "Go pro selfie stick" 90)
                          (tx "11/15/15" "SL-kort" "1,200.00 kr" "SEK")
                          (tx first "Hygiene" "495.00 kr" "SEK")
                          (tx first "Netflix" 7.99M)
                          (tx first "Github" 25)])
          december2015 (let [first "12/01/15"
                             thailand "12/06/15"
                             cm "12/22/15"]
                         [(income first "Income December 2015" 2000)
                          (tx first "Thai visum" 36)
                          (tx first "Fitness24seven" 11.88M)
                          (tx thailand "Flight ARN to BKK" 900)
                          (tx thailand "Kohub apartment" "฿27,699.00" "THB")
                          (tx thailand "Flight BKK to Krabi" "฿4,600.00" "THB")
                          (tx thailand "Taxi Krabi to Kohub" "฿1,700.00" "THB")
                          (tx thailand "Bicycles" "฿2,240.00" "THB")
                          (tx "12/12/15" "Boat trip kohub" "฿2,400.00" "THB")
                          (tx first "T-Mobile" 118)
                          (tx thailand "Swimsuits" "฿1,596.00" "THB")
                          (tx cm "Flight KBV to Chiang Mai" "฿4,095.00" "THB")
                          (tx cm "Chiang Mai Housing" "฿4,500.00" "THB")
                          (tx cm "The Dome Residence" 24)
                          (tx first "Netflix" 7.99M)
                          (tx cm "Taxi Kohub to Krabi" "฿1,700.00" "THB")
                          (tx cm "Taxi Chiang Mai" "฿200.00" "THB")
                          (tx cm "Internet Chiang Mai" "฿375.00" "THB")])
          january2016 (let [first "01/01/16"
                            vietnam "01/26/16"]
                        [(income first "Income January 2016" 2000)
                         (tx first "Thai visum" 36)
                         (tx first "Fitness24seven" "33.00 kr" "SEK")
                         (tx first "T-Mobile" 118)
                         (tx vietnam "Flight DMK to SGN" "฿4,260.00" "THB")
                         (tx first "Chiang Mai Housing" "฿10,500.00" "THB")
                         (tx first "Netflix" 7.99M)
                         (tx first "Internet Chiang Mai" "฿875.00" "THB")
                         (tx "01/22/16" "Chiang Mai Housing" "฿3,620.00" "THB")
                         (tx vietnam "Hotel Vietnam" 111)
                         (tx first "Towels" "฿550.00" "THB")
                         (tx first "Yoga mat" "฿690.00" "THB")
                         (tx vietnam "Flight CNX to DMK" "$4,361.00" "THB")
                         (tx first "Github" 25)])

          february2016 (let [first "02/01/16"
                             malaysia "02/08/16"]
                         [(income first "Income February 2016" 2000)
                          (tx first "Netflix" 7.99M)
                          (tx first "T-Mobile" 118)
                          (tx first "Github" 25)
                          (tx first "Hotel Vietnam" 92)
                          (tx malaysia "Flight SGN to KUL" 138)
                          (tx malaysia "Jeans Diana" "RM270.00" "MYR")
                          (tx malaysia "Shirt Diana" "RM429.00" "MYR")
                          (tx malaysia "Chinos Petter" "RM199.00" "MYR")
                          (tx malaysia "Power adapter" "RM64.00" "MYR")
                          (tx malaysia "Taxi to KLCC" "RM112.00" "MYR")
                          (tx malaysia "Pants Diana" "RM140.00" "MYR")])

          march2016 (let [first "03/01/16"]
                      [(income first "Income March 2016" 2000)
                       (tx first "Netflix" 7.99M)
                       (tx first "T-Mobile" 118)
                       (tx first "Github" 25)
                       (tx first "Airbnb Malaysia" 877.29M)
                       (tx first "Face cream Diana" "RM65.00" "MYR")
                       (tx first "Shoes+Hats etc" "RM287.00" "MYR")])

          april2016 (let [first "04/01/16"
                          osaka "04/05/16"]
                      [(income first "Income April 2016" 3000)
                       (tx first "Netflix" 7.99M)
                       (tx first "T-Mobile" 118)
                       (tx first "Github" 25)
                       (tx first "Apartment Malaysia" 168.71M)
                       (tx osaka "Flight KL to Osaka" 417.94M)
                       (tx osaka "Apartment Osaka" 1033)
                       (tx osaka "Jacket" "￥3,225" "JPY")])

          may2016 (let [first "05/01/16"
                        seoul "05/05/16"
                        nyc "05/27/16"]
                    [(income first "Income May 2016" 4500)
                     (tx first "Netflix" 7.99M)
                     (tx first "T-Mobile" 118)
                     (tx first "Github" 25)
                     (tx seoul "Flight Osaka to Seoul" 190)
                     (tx first "Apartment Osaka" 206.60M)
                     (tx seoul "Apartment Seoul" 863)
                     (tx nyc "Flight Seoul to NYC" 1220)
                     (tx nyc "Apartment NYC" 273.87M)
                     (tx first "Ankle weights + socks" "￥4,638" "JPY")
                     (tx seoul "Jewlery" "₩2,000" "KRW")
                     (tx seoul "Shirt Petter" "₩48,000" "KRW")
                     (tx seoul "Shirt Diana" "₩19,000" "KRW")
                     (tx seoul "Jewlery" "₩3,000" "KRW")
                     (tx seoul "Necklace" "₩8,900" "KRW")
                     (tx seoul "Diana stuff" "₩56,500" "KRW")
                     (tx seoul "Clothes" "$83.48" "KRW")])
          june2016 (let [first "06/01/16"
                         nyc "06/01/16"
                         swe "06/27/16"]
                     [(income first "Income June 2016" 5000)
                      (tx first "Netflix" 9.99M)
                      (tx first "T-Mobile" 118)
                      (tx first "Github" 25)
                      ;; Got refund 225 refund:
                      (tx nyc "Govball Tix" (- 675 225))
                      (tx nyc "Apt NYC" 1424.13M)
                      (tx swe "Flight NYC to ARN" 919)
                      (tx nyc "ResetVegan" 59)
                      (tx nyc "Metro card" 80)
                      (tx nyc "Food processor" 22)
                      (tx nyc "Hair stuff" 23)
                      (tx nyc "Backpack" 38)
                      (tx nyc "Magic bullet" 39)
                      (tx nyc "Face stuff" 60)
                      (tx nyc "Bikinis and such" 70)
                      (tx nyc "Metro card" 8)
                      (tx nyc "Metro" 60)
                      (tx nyc "Ipad cover" 50)
                      (tx nyc "Shirt petter" 16)
                      (tx nyc "Shampoos" 38)])
          july2016 (let [first "07/01/16"]
                     [(income first "Income July 2016" 1500)
                      (tx first "Netflix" 9.99M)
                      (tx first "T-Mobile" 118)
                      (tx first "Github" 25)
                      (tx first "SL-kort" 72.29M)
                      (tx first "Food processor" 59.52M)
                      (tx first "SJ train" 72.29M)
                      (tx first "Myrorna Wedding cloth" 30.12M)
                      (tx first "Claudia gift" 48.43M)
                      (tx first "Dentist" 119.88M)
                      (tx first "Wax" 48.07M)
                      (tx first "Claudia fest" 60.24M)
                      (tx first "Tandhygienist" 177.11M)
                      (tx first "SL refill" 24.10M)])
          august2016 (let [first "08/01/16"]
                       [(income first "Income August 2016" 1500)
                        (tx first "Netflix" 9.99M)
                        (tx first "T-Mobile" 118)
                        (tx first "Github" 25)
                        (tx first "Vegan Reset" 19)
                        (tx first "Wedding house" 107.53M)
                        (tx first "Tandläkare" 156.63M)
                        (tx first "Wedding" 96.39M)
                        (tx first "Doctor" 42.17M)])
          september2016 (let [first "09/01/16"
                              spain "09/07/16"]
                          [(income first "Income September 2016" 2500)
                           (tx first "Netflix" 9.99M)
                           (tx first "T-Mobile" 118)
                           (tx first "Github" 25)
                           (tx spain "Flight" 281.88M)
                           (tx spain "Apartment" 828)
                           (tx spain "Hotel" 73)
                           (tx first "Insurance" 147.06M)])
          october2016 (let [first "10/01/16"
                            sthlm-first "10/04/16"]
                        [(income first "Income October 2016" 2500)
                         (tx first "Netflix" 9.99M)
                         (tx first "T-Mobile" 118)
                         (tx first "Github" 25)
                         (tx first "Insurance" 147.06M)
                         (tx sthlm-first "Flight" 308M)
                         (tx first "Apartment" 216)
                         (tx "10/06/16" "Second Apartment Seville" 957)])]
      (vec (concat
             september2015
             october2015
             november2015
             december2015
             january2016
             february2016
             march2016
             april2016
             may2016
             june2016
             july2016
             august2016
             september2016
             october2016)))))