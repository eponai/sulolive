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
                   :bicycle [:transport]
                   :ferry     [:transport]
                   :taxi      [:transport]
                   :uber      [:transport]
                   :train     [:transport]
                   :flight    [:transport]}]
    (reduce (fn [tags t]
              (apply conj tags (get tag->tags t)))
            tags
            tags)))

(defn generate-tags
  "Given title, date, amount and currency, generate some tags."
  [{:keys [transaction/title transaction/date]}]
  (let [title-tags (get title->tags title)
        _ (when (not (some? title-tags))
            (throw (ex-info (str "No tags for title: " title)
                            {:title title :chars (seq title)})))
        date (:date/ymd date)
        date-tags (date->tags date)
        _ (when (not (some? date-tags))
            (throw (ex-info (str "No tags for date: " date)
                            {:date date})))]
    (->> (set/union (set title-tags) (set date-tags))
         tags->tags
         (into [] (comp (map #(cond-> % (keyword? %) name))
                        (distinct)
                        (map (fn [t] {:tag/name t})))))))

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
                 :to      (time/date-time 2016 6 27)}])

(def title->tags
  {"Cash"                     [:cash]
   "Transfer"                 []
   "Dinner"                   [:dinner]
   "Coffee"                   [:coffee]
   "Lunch"                    [:lunch]
   "Dinner Pizza"             [:dinner :pizza]
   "Lunch Port"               [:lunch]
   "Luggage"                  [:travel :essentials]
   "Fika"                     [:sweets]
   "Market"                   [:groceries]
   "Cash ($5 fee)"            [:cash :fee]
   "Cash (-$5 fee)"           [:cash :fee]
   "Beach chairs"             [:beach]
   "Corn"                     [:sweets]
   "Chips"                    [:sweets :chips]
   "Water"                    [:groceries]
   "Comb"                     [:essentials]
   "Fruit"                    [:groceries]
   "Ice tea"                  [:refreshment]
   "Hotell"                   [:accomodation :hotel]
   "Taxi"                     [:taxi]
   "Water+Sprite (ATH)"       [:refreshment]
   "M&Ms"                     [:snacks]
   "Lunch Abu Dhabi"          [:lunch]
   "Ramen BKK"                [:lunch]
   "Taxi Chiang Mai"          [:taxi]
   "Taxi BKK"                 [:taxi]
   "Food"                     [:food]
   "Night out"                [:party :beer]
   "7eleven"                  [:sweets]
   "sleven"                   [:sweets]
   "Ice cream"                [:sweets]
   "Lunch + Coffee"           [:lunch :coffee]
   "Lunch+coffee"             [:lunch :coffee]
   "Dinner+cola"              [:dinner]
   "Dinner+beer"              [:dinner :beer]
   "Dinner+drinks"            [:dinner :cocktail]
   "Songtaew"                 [:taxi]
   "Cat cafe"                 [:sweets :coffee]
   "Work cafe"                [:coffee]
   "Wine"                     [:wine]
   "Tea"                      [:tea]
   "Bowling"                  [:bowling]
   "Beers"                    [:beer]
   "Ritblock"                 [:essentials]
   "Bread"                    [:groceries]
   "Pool party"               [:cocktail :beer]
   "Taxi airport"             [:taxi]
   "Pizza"                    [:food]
   "Cola"                     [:refreshment :sweets]
   "Utekväll"                 [:party]
   "Kaffe"                    [:coffee]
   "Retro"                    [:beer]
   "Retro bar"                [:beer]
   "Storstad"                 [:beer]
   "Godis"                    [:sweets]
   "Eftersläpp"               [:party]
   "Vin"                      [:wine]
   "Blue bandana"             [:clothes]
   "outfit det går bra nu"    [:clothes]
   "Beefeater inn"            [:beer]
   "Chai latte"               [:coffee]
   "Lantchips"                [:sweets :chips]
   "Sabis"                    [:groceries]
   "Operation"                [:healthcare]
   "Iskaffe"                  [:coffee]
   "Bio"                      [:cinema]
   "Bio chips"                [:cinema :sweets :chips]
   "Bio-snacks"               [:cinema :sweets]
   "Plankstek+öl"             [:dinner]
   "Soutside"                 [:beer]
   "HTL öl"                   [:beer]
   "Soda stream"              [:groceries]
   "Hemköp"                   [:groceries]
   "Ica edsbro"               [:groceries]
   "Landet"                   []
   "Balsam"                   [:essentials]
   "Sushi"                    [:food]
   "Linn date"                [:beer]
   "Felicia öl"               [:beer]
   "Lunch HTL"                [:lunch]
   "Lunch Elin"               [:lunch]
   "Espresso house"           [:coffee]
   "Thai Lunch"               [:lunch]
   "Thai lunch"               [:lunch]
   "Kaffe osv"                [:coffee]
   "Lunch Buffe"              [:lunch]
   "Clas ohlzhon"             [:electronics]
   "Foam kaffe"               [:coffe]
   "Subway lunch"             [:lunch :fastfood]
   "Konsum"                   [:groceries]
   "Thai Middag"              [:dinner]
   "Burger king"              [:fastfood :burgerking]
   "Thai middag"              [:dinner]
   "Chips etc"                [:chips :sweets]
   "Klang kaffe"              [:coffee]
   "Subway dinner"            [:fastfood :dinner]
   "Sushi essingen"           [:food]
   "Lunch donken"             [:lunch :fastfood]
   "Lunch Pascha"             [:lunch]
   "Lunch thai"               [:lunch]
   "Foto visum"               [:essentials]
   "Lunch Kebab"              [:lunch :fastfood]
   "Onsdagspub"               [:beer :cocktail]
   "Cafe60"                   [:coffee]
   "Lunch Dox"                [:coffee :lunch]
   "HTL"                      [:coffee]
   "Lunch Sten Sture"         [:lunch]
   "Frukost Il caffe"         [:breakfast]
   "Donken"                   [:fastfood]
   "Flottis"                  [:fastfood]
   "Valhallagrillen"          [:fastfood]
   "Flux"                     [:apps]
   "Max"                      [:fastfood]
   "Vatten"                   [:refreshment]
   "Irish Embassy"            [:beer :food]
   "Bucket"                   [:alcohol :bucket]
   "Towels"                   [:essentials]
   "Drinks"                   [:cocktail]
   "Lunch Papaya"             [:lunch]
   "Chips+solkräm"            [:essentials :chips :sweets]
   "Snacks irish"             [:dinner]
   "Happy drinks"             [:cocktail]
   "Beer @ kohub"             [:beer]
   "Pangea bucket"            [:bucket :alcohol]
   "Irish dinner"             [:dinner]
   "Cafe"                     [:coffee]
   "Living room cafe"         [:coffee]
   "Chocolate"                [:sweets]
   "Beach bar"                [:beach :alcohol]
   "Subway"                   [:fastfood]
   "Shop"                     []
   "Lunch+Coffee"             [:lunch :coffee]
   "Coffee+Chocolate"         [:coffee :sweets]
   "Laundry"                  [:laundry]
   "East coffee"              [:coffee]
   "Beer"                     [:beer]
   "Pharmacy"                 [:essentials :healthcare]
   "Elastics"                 [:workout]
   "Salad concept"            [:dinner]
   "Dinner Cafe Luvv"         [:dinner]
   "Dinner Japan"             [:dinner]
   "Lunch Maya"               [:lunch]
   "Cainito Lunch+Coffee"     [:lunch :coffee]
   "Breakfast"                [:breakfast]
   "Karre"                    [:coffee]
   "Market Fruit"             [:groceries]
   "Betta's house"            [:coffee]
   "Cards+chips"              [:chips :sweets]
   "Coke"                     [:refreshment :sweets]
   "Dinner Echo"              [:dinner]
   "Wine+beer"                [:wine :beer]
   "Homemade"                 [:food]
   "Dinner homemade"          [:dinner]
   "Popcorn"                  [:sweets]
   "Cinema"                   [:cinema]
   "Lunch local"              [:lunch]
   "Thai tea"                 [:tea]
   "Lunch bk"                 [:fastfood :lunch]
   "Soda"                     [:refreshment]
   "Museum"                   [:tourism]
   "Cookies"                  [:sweets]
   "Coffee+Dinner"            [:coffee :dinner]
   "Chips cookies"            [:chips :sweets]
   "Everything I.d. Cafe"     [:lunch :dinner :coffee :sweets]
   "I.d. Cafe"                [:lunch :dinner :coffee :sweets]
   "Smoothie"                 [:refreshment :sweets]
   "Lunch+smoothie"           [:lunch :sweets]
   "Loft cafe"                [:cafe :coffee]
   "Twix"                     [:sweets]
   "Pho Airport"              [:lunch]
   "BK airport"               [:fastfood]
   "Uber"                     [:uber :taxi]
   "Bar"                      [:alcohol]
   "Groceries"                [:groceries]
   "Mamma betalar"            [:thanksmom]
   "El adapter"               [:essentials :electronics]
   "Cake"                     [:sweets]
   "Tickets Death cab"        [:concert]
   "Beers and dinner"         [:dinner :beer]
   "Beers KL live"            [:beer]
   "Mask"                     [:cosmetics]
   "Starbucks"                [:coffee]
   "Mocha"                    [:coffee]
   "Thai"                     [:food]
   "Milk"                     [:groceries]
   "MyNews"                   [:groceries]
   "Milk+Chips"               [:groceries :chips :sweets]
   "Milk+chips"               [:groceries :chips :sweets]
   "Lunch sushi"              [:lunch]
   "Hotel coffee"             [:coffee]
   "Lunch T.G.I"              [:lunch]
   "Shisha"                   [:shisha]
   "Ipren"                    [:healthcare :essentials]
   "Schwarma"                 [:food]
   "Dome lunch"               [:lunch]
   "Shisha meze"              [:shisha :dinner]
   "TGI"                      [:lunch]
   "Lunch Dome"               [:lunch]
   "Marinis on 57"            [:shisha :wine]
   "Airport breakfast"        [:breakfast]
   "Train tickets"            [:train]
   "KitKat"                   [:sweets]
   "Family mart"              [:groceries]
   "Bananas"                  [:groceries]
   "Ramen"                    [:food]
   "Batteries"                [:essentials]
   "Octupus balls"            [:food]
   "C.C lemon"                [:refreshment]
   "Manga"                    [:book]
   "Hot dog + coffee"         [:coffee :lunch]
   "BarShisha"                [:shisha :alcohol]
   "Bar15minutes"             [:bar :cocktails]
   "1dollar store"            [:clothes]
   "Train tickets (kyoto)"    [:train]
   "Refreshments"             [:refreshment]
   "Zoo"                      [:tourism]
   "Vending machine"          [:refreshment :sweets]
   "Tully's"                  [:coffee]
   "Kebab"                    [:fastfood]
   "Drink"                    [:refreshment]
   "Train Osaka"              [:train]
   "Train Seoul"              [:train]
   "Bank fee"                 [:fee]
   "AtoZ cafe"                [:coffee]
   "Sandwich coffe"           [:coffee :food]
   "Sandwiches"               [:food]
   "Glass"                    []
   "Candy"                    [:sweets]
   "Burgers"                  [:fastfood]
   "Bar+shisha"               [:shisha :beer]
   "Russin"                   [:sweets]
   "Flight ARN to Dubrovnik"  [:flight]
   "Appartment Durres"        [:apartment]
   "Hotel Croatia"            [:hotel]
   "Hotel Chiang Mai"         [:hotel]
   "Ferry Dubrovnik to Bari"  [:ferry]
   "Ferry Bari to Durres"     [:ferry]
   "Flight Albania to BKK"    [:flight]
   "Flight BKK to Chiang Mai" [:flight]
   "Hotel Chiang Mai 1d"      [:hotel]
   "Domain"                   [:business]
   "T-Mobile"                 [:subscription]
   "Netflix"                  [:subscription]
   "Github"                   [:subscription :business]
   "Internet Chiang Mai"      [:internet]
   "Pants Diana"              [:clothes]
   "Shoes+Hats etc"           [:clothes]
   "Jacket"                   [:clothes]
   "Diana stuff"              []
   "Flight BKK to ARN" [:flight]
   "Flight Chiang Mai to BKK" [:flight]
   "Nike Dri-fit Petter" [:clothes]
   "Pants for Diana" [:clothes]
   "Gift Lilian" [:gift]
   "Hälinlägg" [:healthcare :essentials]
   "SL-kort" [:train]
   "Fitness24seven" [:fitness]
   "Go pro selfie stick" [:electronics]
   "Hygiene" [:essentials]
   "Thai visum" [:essentials :visa]
   "Flight ARN to BKK" [:flight]
   "Kohub apartment" [:apartment]
   "Flight BKK to Krabi" [:flight]
   "Taxi Krabi to Kohub" [:taxi]
   "Bicycles" [:bicycle]
   "Boat trip kohub" [:tourism]
   "Swimsuits" [:clothes :beach]
   "Flight KBV to Chiang Mai" [:flight]
   "Chiang Mai Housing" [:apartment]
   "The Dome Residence" [:hotel]
   "Taxi Kohub to Krabi" [:taxi]
   "Flight DMK to SGN" [:flight]
   "Hotel Vietnam" [:hotel]
   "Yoga mat" [:fitness]
   "Flight CNX to DMK" [:flight]
   "Flight SGN to KUL" [:flight]
   "Jeans Diana" [:clothes]
   "Shirt Diana" [:clothes]
   "Chinos Petter" [:clothes]
   "Power adapter" [:electronics :essentials]
   "Taxi to KLCC" [:taxi]
   "Airbnb Malaysia" [:apartment]
   "Face cream Diana" [:cosmetics]
   "Apartment Malaysia" [:apartment]
   "Flight KL to Osaka" [:flight]
   "Apartment Osaka" [:apartment]
   "Flight Osaka to Seoul" [:flight]
   "Apartment Seoul" [:apartment]
   "Flight Seoul to NYC" [:flight]
   "Apartment NYC" [:apartment]
   "Ankle weights + socks" [:clothes :fitness]
   "Jewlery" [:clothes]
   "Shirt Petter" [:clothes]
   "Necklace" [:clothes]
   })