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
            [eponai.common.format :as eponai.format]))

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
            (map (fn [d] {:country country :city city
                          :date/ymd (f/unparse eponai.format/ymd-date-formatter d)})
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
  {:pre [(set? tags)]
   :post [(set? tags)]}
  (let [tag->tags {:beer      [:alcohol]
                   :wine      [:alcohol]
                   :coffee    [:fika]
                   :lunch     [:food]
                   :taxi      [:transport]
                   :uber      [:transport]
                   :party     [:alcohol]
                   :bangkok   [:thailand]
                   :chiangmai [:thailand]}]
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
                 :from (time/date-time 2015 9 24)
                 :to (time/date-time 2015 10 22)}
                {:country "Sweden" :city "Stockholm"
                 :from (time/date-time 2015 10 22)
                 :to (time/date-time 2015 12 7)}
                {:country "Thailand" :city "Koh Lanta"
                 :from (time/date-time 2015 12 7)
                 :to (time/date-time 2015 12 23)}
                {:country "Thailand" :city "Chiang Mai"
                 :from (time/date-time 2015 12 23)
                 :to (time/date-time 2016 1 16)}
                {:country "Vietnam" :city "Ho Chi Minh City"
                 :from (time/date-time 2016 1 16)
                 :to (time/date-time 2016 2 7)}
                {:country "Malaysia" :city "Kuala Lumpur"
                 :from (time/date-time 2016 2 7)
                 :to (time/date-time 2016 4 5)}
                {:country "Japan" :city "Osaka"
                 :from (time/date-time 2016 4 5)
                 :to (time/date-time 2016 5 5)}])

(def title->tags
  {"Cash"                  [:cash]
   "Transfer"              []
   "Dinner"                [:dinner]
   "Coffee"                [:coffee :fika]
   "Lunch"                 [:lunch]
   "Dinner Pizza"          [:dinner :pizza]
   "Lunch Port"            [:lunch]
   "Luggage"               [:travel :essentials]
   "Fika"                  [:fika]
   "Market"                [:groceries]
   "Cash ($5 fee)"         [:cash :fee]
   "Cash (-$5 fee)"        [:cash :fee]
   "Beach chairs"          [:beach]
   "Corn"                  [:snacks]
   "Chips"                 [:snacks]
   "Water"                 [:groceries :water]
   "Comb"                  [:essentials]
   "Fruit"                 [:groceries :fruit]
   "Ice tea"               [:drink :snacks]
   "Hotell"                [:accomodation :hotel]
   "Taxi"                  [:taxi :transportation]
   "Water+Sprite (ATH)"    [:drink :snacks :water :airport :athens]
   "M&Ms"                  [:snacks]
   "Lunch Abu Dhabi"       [:airport :lunch :abudhabi]
   "Ramen BKK"             [:airport :lunch :bangkok]
   "Taxi Chiang Mai"       [:taxi :transportation :chaingmai]
   "Taxi BKK"              [:taxi :transportation :bangkok]
   "Food"                  [:food]
   "Night out"             [:party]
   "7eleven"               [:snacks]
   "sleven"                [:snacks]
   "Ice cream"             [:snacks]
   "Lunch + Coffee"        [:lunch :coffee]
   "Lunch+coffee"          [:lunch :coffee]
   "Dinner+cola"           [:dinner]
   "Dinner+beer"           [:dinner :beer]
   "Dinner+drinks"         [:dinner :cocktail]
   "Songtaew"              [:taxi :songtaew]
   "Cat cafe"              [:cats :cafe :fika :coffee]
   "Work cafe"             [:cafe]
   "Wine"                  [:wine]
   "Tea"                   [:tea]
   "Bowling"               [:bowling]
   "Beers"                 [:beer]
   "Ritblock"              [:essentials]
   "Bread"                 [:bread :groceries]
   "Pool party"            [:party :pool :cocktail :beer]
   "Taxi airport"          [:taxi]
   "Pizza"                 [:pizza :food]
   "Cola"                  [:cola :drink :snacks]
   "Utekväll"              [:party]
   "Kaffe"                 [:coffee :fika]
   "Retro"                 [:party :stockholm :beer :retro]
   "Retro bar"             [:party :beer :retro]
   "Storstad"              [:party :stockholm :beer]
   "Godis"                 [:candy :snacks]
   "Eftersläpp"            [:party :stockholm]
   "Vin"                   [:wine]
   "Blue bandana"          [:stockholm :unknown]
   "outfit det går bra nu" [:clothes :splurge]
   "Beefeater inn"         [:beer :drinks :afterwork]
   "Chai latte"            [:coffee]
   "Lantchips"             [:snacks :chips]
   "Sabis"                 [:groceries]
   "Operation"             [:health :hospital]
   "Iskaffe"               [:coffee]
   "Bio"                   [:cinema]
   "Bio chips"             [:cinema :snacks :chips]
   "Bio-snacks"            [:cinema :snacks]
   "Plankstek+öl"          [:dinner :afterwork :beer]
   "Soutside"              [:party :beer]
   "HTL öl"                [:beer :afterwork :htl]
   "Soda stream"           [:sodastream]
   "Hemköp"                [:groceries]
   "Ica edsbro"            [:groceries]
   "Landet"                [:landet]
   "Balsam"                [:balsam :essentials]
   "Sushi"                 [:sushi]
   "Linn date"             [:beer :party]
   "Felicia öl"            [:beer :party]
   "Lunch HTL"             [:lunch :htl]
   "Lunch Elin"            [:lunch]
   "Espresso house"        [:coffee :fika]
   "Thai Lunch"            [:lunch :thaifood]
   "Thai lunch"            [:lunch :thaifood]
   "Kaffe osv"             [:coffee :fika]
   "Lunch Buffe"           [:lunch]
   "Clas ohlzhon"          [:electronics]
   "Foam kaffe"            [:coffe :foam]
   "Subway lunch"          [:fastfood :lunch :subway]
   "Konsum"                [:groceries :konsum]
   "Thai Middag"           [:thaifood :dinner]
   "Burger king"           [:fastfood :burgerking]
   "Thai middag"           [:thaifood :dinner]
   "Chips etc"             [:chips :snacks]
   "Klang kaffe"           [:coffee]
   "Subway dinner"         [:fastfood :dinner :subway]
   "Sushi essingen"        [:sushi]
   "Lunch donken"          [:lunch :fastfood :mcdonalds]
   "Lunch Pascha"          [:lunch]
   "Lunch thai"            [:thaifood :lunch]
   "Foto visum"            [:travel]
   "Lunch Kebab"           [:lunch :fastfood]
   "Onsdagspub"            [:party]
   "Cafe60"                [:cafe :coffee :fika]
   "Lunch Dox"             [:coffee :cafe :lunch]
   "HTL"                   [:coffee :fika :htl]
   "Lunch Sten Sture"      [:lunch]
   "Frukost Il caffe"      [:breakfast :ilcafe]
   "Donken"                [:fastfood :mcdonalds]
   "Flottis"               [:fastfood]
   "Valhallagrillen"       [:fastfood]
   "Flux"                  [:unknown]
   "Max"                   [:fastfood :max]
   "Vatten"                [:water :drink]
   "Irish Embassy"         [:party :beer :food]
   "Bucket"                [:alcohol :bucket]
   "Towels"                [:essentials]
   "Drinks"                [:cocktails]
   "Lunch Papaya"          [:lunch]
   "Chips+solkräm"         [:essentials :chips :snacks]
   "Snacks irish"          [:snacks :dinner]
   "Happy drinks"          [:drinks]
   "Beer @ kohub"          [:beer :afterwork]
   "Pangea bucket"         [:bucket :alcohol]
   "Irish dinner"          [:dinner]
   "Cafe"                  [:cafe :coffee :fika]
   "Living room cafe"      [:cafe :coffee :fika]
   "Chocolate"             [:chocolate :snacks]
   "Beach bar"             [:beach :alcohol]
   "Subway"                [:fastfood :subway]
   "Shop"                  [:shopping :unknown]
   "Lunch+Coffee"          [:lunch :coffee :fika]
   "Coffee+Chocolate"      [:chocolate :coffee :snacks :fika]
   "Laundry"               [:laundry]
   "East coffee"           [:eastcoffee :coffee :fika]
   "Beer"                  [:beer]
   "Pharmacy"              [:essentials :pharmacy]
   "Elastics"              [:unknown]
   "Salad concept"         [:dinner :salad]
   "Dinner Cafe Luvv"      [:dinner :thaifood]
   "Dinner Japan"          [:dinner]
   "Lunch Maya"            [:lunch :maya]
   "Cainito Lunch+Coffee"  [:lunch :thaifood :cainito :coffee]
   "Breakfast"             [:breakfast]
   "Karre"                 [:coffee]
   "Market Fruit"          [:market :fruit]
   "Betta's house"         [:coffee]
   "Cards+chips"           [:chips :unknown :snacks]
   "Coke"                  [:coke :drink :snacks]
   "Dinner Echo"           [:dinner :burger]
   "Wine+beer"             [:wine :beer]
   "Homemade"              [:thaifood :cainito :food]
   "Dinner homemade"       [:dinner :thaifood :cainito :food]
   "Popcorn"               [:snacks :popcorn]
   "Cinema"                [:cinema]
   "Lunch local"           [:lunch]
   "Thai tea"              [:tea]
   "Lunch bk"              [:fastfood :burgerking :lunch]
   "Soda"                  [:drink :soda :snacks]
   "Museum"                [:tourism :museum]
   "Cookies"               [:cookies]
   "Coffee+Dinner"         [:coffee :dinner]
   "Chips cookies"         [:chips :cookies :snacks]
   "Everything I.d. Cafe"  [:lunch :dinner :coffee :fika :idcafe]
   "I.d. Cafe"             [:lunch :dinner :coffee :fika :idcafe]
   "Smoothie"              [:smoothie :drink :refreshment]
   "Lunch+smoothie"        [:lunch :refreshment :smoothie]
   "Loft cafe"             [:cafe :coffee]
   "Twix"                  [:snacks :chocolate]
   "Pho Airport"           [:airport :pho :lunch]
   "BK airport"            [:airport :burgerking]
   "Uber"                  [:uber :transportation]
   "Bar"                   [:bar :alcohol]
   "Groceries"             [:groceries]
   "Mamma betalar"         []
   "El adapter"            [:essentials :electronics]
   "Cake"                  [:cake]
   "Tickets Death cab"     [:concert :deathcabforcutie :tickets]
   "Beers and dinner"      [:dinner :beer]
   "Beers KL live"         [:beer]
   "Mask"                  [:cosmetics]
   "Starbucks"             [:starbucks :coffee]
   "Mocha"                 [:coffee :mocha]
   "Thai"                  [:thaifood]
   "Milk"                  [:milk :groceries]
   "MyNews"                [:groceries]
   "Milk+Chips"            [:groceries :snacks :milk]
   "Milk+chips"            [:groceries :snacks :milk]
   "Lunch sushi"           [:lunch :sushi]
   "Hotel coffee"          [:coffee :cafe]
   "Lunch T.G.I"           [:tgifridays :lunch]
   "Shisha"                [:shisha :party]
   "Ipren"                 [:drugs :essentials]
   "Schwarma"              [:food]
   "Dome lunch"            [:lunch]
   "Shisha meze"           [:shisha :meze :dinner :food]
   "TGI"                   [:tgifridays :lunch]
   "Lunch Dome"            [:lunch]
   "Marinis on 57"         [:party :shisha :wine]
   "Airport breakfast"     [:airport :breakfast]
   "Train tickets"         [:tickets :train :transportation]
   "KitKat"                [:chocolate :snacks]
   "Family mart"           [:groceries]
   "Bananas"               [:fruit]
   "Ramen"                 [:food]
   "Batteries"             [:essentials]
   "Octupus balls"         [:food]
   "C.C lemon"             [:refreshment]
   "Manga"                 [:books :manga]
   "Hot dog + coffee"      [:coffee :hotdog]
   "BarShisha"             [:party :bar :shisha :alcohol]
   "Bar15minutes"          [:bar :cocktails]
   })