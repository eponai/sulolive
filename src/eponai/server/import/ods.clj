(ns eponai.server.import.ods
  "Namespace for importing open office expenses.
  The name 'ods' comes from the open office file format (.ods).

  How to:
  1. Export .ods format to .html in
     Open Office -> File -> Export...
     Choose format .html
  2. Call this -main function with a path
     to the file, or slurp the file and call
     (doit <html-string>) via repl.
  3. Result with be jourmoney transaction entities.
  4. You may have to add additional data when transacting
     the entities to a database.
     Such as :transaction/uuid and :transaction/project."
  (:require [clojure.data.xml :as xml]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clj-time.format :as f]
            [clj-time.coerce :as time-coerce]
            [eponai.common.format :as eponai.format]
            [eponai.server.import.ods.tags :as ods.tags]
            [eponai.common.format.date :as date])
  (:import [java.util.zip GZIPInputStream]
           [java.io InputStreamReader]))

(defn tag? [k element]
  (= k (:tag element)))

(defn data-cell?
  "This cell contains a single data point?"
  [{:keys [content] :as cell}]
  (and (tag? :td cell)
       (every? #(or (string? %) (map? %)) content)))

;; The export contains empty strings which are represented as (char 160). It's weird.
(defn empty-string? [s]
  {:pre [(or (nil? s) (string? s))]}
  (or (nil? s)
      (= s (str (char 160)))))

(defn cell->content [cell]
  (assert (data-cell? cell)
          (str "Was not data-cell. Was: " cell))
  {:pre [(data-cell? cell)]}
  (letfn [(unwrap-content [x]
            (cond
              (string? x)
              (when-not (empty-string? x) x)
              (and (map? x) (contains? x :content))
              (reduce str nil (filter some? (map unwrap-content (:content x))))))]
    (unwrap-content cell)))

(def date-pattern #"\d{2}/\d{2}/\d{2}")

(defn date? [s]
  {:pre  [(or (nil? s) (string? s))]
   :post [(or (nil? %) (string? s))]}
  (when s
    (re-matches date-pattern s)))

(defn- page-rows->maps [header rows]
  (letfn [(filter-nil-vals [m]
            {:pre [(map? m)]}
            (reduce-kv (fn [m k v] (cond-> m (some? v) (assoc k v)))
                       {} m))]
    (loop [date nil rows rows ret []]
      (if (seq rows)
        (let [row (first rows)
              row-map (zipmap header (map cell->content (:content row)))
              d (or (date? (get row-map "Date")) date)]
          (recur d
                 (rest rows)
                 (cond-> ret
                         (not (empty-string? (get row-map "Product")))
                         (conj (filter-nil-vals (assoc row-map "Date" d))))))
        ret))))

(defn transaction-page->maps [{:keys [content] :as page}]
  {:pre [(tag? :table page)]}
  (let [header (map cell->content (:content (first content)))
        maps (page-rows->maps header (rest content))]
    {:currencies   (set (filter some? (drop 2 header)))
     :transactions maps}))

(defn transaction-page->parsed-page [page]
  (transaction-page->maps page))

(defn transaction-pages->parsed-pages [pages]
  (map transaction-page->parsed-page pages))

(defn trim-left [page]
  {:pre [(tag? :table page)]}
  (update page :content
          (fn [rows]
            (->> rows (map (fn [row]
                             (assert (or (tag? :tr row) (tag? :colgroup row))
                                     (str "row was not :colgroup. Was: " row
                                          ". on page: " page))
                             (update row :content #(drop 3 %))))))))

(defn trim-top [page]
  (assert (->> (:content page)
               (first)
               (every? #(empty? (:content %))))
          (str "First row of page was not empty. Page: " page))
  (update page :content #(drop 2 %)))

(defn page->transaction-rows
  "Trims the top and the left side of the page, leaving only the transaction data."
  [page]
  {:pre [(tag? :table page)]}
  (-> page trim-top trim-left))

(defn html->transaction-pages
  "Takes the root of the parsed html and returns a list with the transaction data per page"
  [root]
  {:pre  [(tag? :html root)]
   :post [(sequential? %)]}
  (let [[header body] (:content root)]
    (assert (tag? :body body))
    (map page->transaction-rows (:content body))))

(defn remove-style-and-attrs [element]
  (if-not (map? element)
    element
    (if (contains? element :content)
      (update (select-keys element [:tag :content])
              :content #(map remove-style-and-attrs %))
      (do (prn "What is this map?: " element)
          element))))

(defn parse [expenses-html]
  (xml/parse (cond
               (string? expenses-html) (java.io.StringReader. expenses-html)
               (instance? java.io.Reader expenses-html) expenses-html)))

;; JOUR MONEY

(defn read-bigdec [s trim-start & [trim-end]]
  {:pre  [(string? s)]
   :post [(decimal? %)]}
  (let [neg? (s/starts-with? s "-")
        s (cond-> s neg? (subs 1))
        digits (->> (s/trim (subs s trim-start (- (count s) (or trim-end 0))))
                    (remove #(= % \,))
                    (apply str))]
    (binding [*read-eval* false]
      (read-string (str (cond->> digits neg? (str "-"))
                        "M")))))

(def html-date (f/formatter "MM/dd/yy"))

;; TODO: Change this defmulti to a simple map taking :trim-start or :trim-end

;; Euro with amount: € 30.00
;; Kuna with amount: -500.00 kn
;; Leke with amount: -5,000.00 LAK
;; USD with amount: $0.00
;; Bath with amount: -฿10,000.00
;; THB with amount: ฿100.00
;; SEK with amount: 403.00 kr
;; VND with amount: 260,000.00 ₫
;; MYR with amount: RM22.00
;;JPY with amount: ￥1,840
(def parse-price-m
  {"Euro" {:trim-start 1 :code "EUR"}
   "Kuna" {:trim-end 3 :code "HRK"}
   "Leke" {:trim-end 4 :code "ALL"}
   "USD"  {:trim-start 1}
   "THB"  {:trim-start 1}
   "Bath" {:trim-start 1 :code "THB"}
   "SEK"  {:trim-end 3}
   "VND"  {:trim-end 2}
   "MYR"  {:trim-start 2}
   "JPY"  {:trim-start 1}
   "KRW"  {:trim-start 1}})

(defn parse-price [currency amount]
  (if-let [{:keys [trim-start trim-end code]
            :or   {code       currency
                   trim-start 0
                   trim-end   0}}
           (get parse-price-m currency)]
    {:amount (read-bigdec amount trim-start trim-end)
     :code   code}
    (throw (ex-info (str "TODO: Implement currency: " currency
                         " with amount: " amount)
                    {:currency currency :amount amount}))))

(defn parse-amount [currencies transaction]
  (let [curr-only (select-keys transaction (disj currencies "USD"))
        _ (assert (or (and (empty? curr-only) (contains? transaction "USD"))
                      (= 1 (count curr-only)))
                  (str "Had more than one non-USD currency"
                       " or no known currencies."
                       " Transaction: " transaction))
        [raw-curr raw-amount] (if (empty? curr-only)
                                ["USD" (get transaction "USD")]
                                (first curr-only))
        {:keys [code amount]} (parse-price raw-curr raw-amount)]
    {:transaction/amount   amount
     :transaction/currency {:currency/code code}}))

(defn left-side-of-expenses []
  {:post [(vector? %)]}
  (letfn [(tx
            ([date title amount] (tx date title amount "USD"))
            ([date title amount curr]
             {:pre [(string? date) (string? title) (string? curr)
                    (or (decimal? amount)
                        (integer? amount)
                        (and (string? amount) (not= "USD" curr)))]}
             (as-> {"Date" date "Product" title}
                   transaction
                   (if (= "USD" curr)
                     (assoc transaction "USD" (str "$" amount))
                     (assoc transaction "USD" "$0.00"
                                        curr amount)))))]
    [
     ;; September
     (tx "09/09/15" "Flight SEK to Dubrovnik" 500)
     (tx "09/13/15" "Appartment Durres" 187)
     (tx "09/09/15" "Hotel Croatia" 100)
     (tx "09/25/15" "Hotel Chiang Mai" 112.14M)
     (tx "09/12/15" "Ferry Dubrovnik to Bari" 262.50M)
     (tx "09/13/15" "Ferry Bari to Durres" 180.80M)
     (tx "09/25/15" "Flight Albania to BKK" 1234)
     (tx "09/25/15" "Flight BKK to Chaing Mai" 116.13M)
     (tx "09/25/15" "Hotel Chiang Mai 1d" 14.73M)
     (tx "09/17/15" "Domain" 2.53M)
     (tx "09/01/15" "T-Mobile" 118)

     ;; October
     (let [first "10/01/15"]
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
       (tx first "Netflix" 7.99M))

     ;; November
     (let [first "11/01/15"]
       (tx first "Domain" 30.32M)
       (tx first "T-Mobile" 118)
       (tx first "SL-kort" "1,570.00 kr" "SEK")
       (tx first "Fitness24seven" "569.00 kr" "SEK")
       (tx first "Go pro selfie stick" 90)
       (tx "11/15/15" "SL-kort" "1,200.00 kr" "SEK")
       (tx first "Hygiene" "495.00 kr" "SEK")
       (tx first "Netflix" 7.99M)
       (tx first "Github" 25))

     ;; December
     (let [first "12/01/15"
           thailand "12/06/15"
           cm "12/22/15"]
       (tx first "Thai visum" 36)
       (tx first "Fitness24seven" 11.88M)
       (tx thailand "Flight ARN to BKK" 900)
       (tx thailand "Kohub apartment" 765.88M)
       (tx thailand "Taxi Krabi to Kohub" 51)
       (tx thailand "Bicycles" 67.20M)
       (tx "12/12/15" "Boat trip kohub" 72)
       (tx first "T-Mobile" 118)
       (tx thailand "Swimsuits" 47.88)
       (tx cm "Flight KBV to Chiang Mai" 122.85M)
       (tx cm "Chiang Mai Housing" 135)
       (tx first "Netflix" 7.99M)
       (tx cm "Taxi Kohub to Krabi" 51)
       (tx cm "Taxi Chiang Mai" 6)
       (tx cm "Internet Chiang Mai"))

     ;; January
     (let [first "01/01/16"
           vietnam "01/26/16"]
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
       (tx first "Github" 25))

     ;; February
     (let [first "02/01/16"
           malaysia "02/08/16"]
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
       (tx malaysia "Pants Diana" "RM140.00" "MYR"))

     ;; March
     (let [first "03/01/16"]
       (tx first "Netflix" 7.99M)
       (tx first "T-Mobile" 118)
       (tx first "Github" 25)
       (tx first "Airbnb Malaysia" 877.29M)
       (tx first "Face cream Diana" "RM65.00" "MYR")
       (tx first "Shoes+Hats etc" "RM287.00" "MYR"))

     ;; April
     (let [first "04/01/16"
           osaka "04/05/16"]
       (tx first "Netflix" 7.99M)
       (tx first "T-Mobile" 118)
       (tx first "Github" 25)
       (tx first "Apartment Malaysia" 168.71M)
       (tx osaka "Flight KL to Osaka" 417.94M)
       (tx osaka "Apartment Osaka" 1033)
       (tx osaka "Jacket" "￥3,225" "JPY"))

     ;; May
     (let [first "05/01/16"
           seoul "05/05/16"
           nyc "05/27/16"]
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
       (tx seoul "Diana stuff" "₩56,500" "KRW"))
     ]))

(defn page->jourmoney-entities [{:keys [currencies transactions] :as page}]
  {:pre [(map? page)]}
  (letfn [(html-t->jourmoney->t [t]
            (prn "t: " t)
            (let [date (f/parse html-date (get t "Date"))
                  entity (-> (parse-amount currencies t)
                             (assoc :transaction/date (date/date-map date)
                                    :transaction/title (s/trim (get t "Product"))))]

              (assoc entity :transaction/tags (ods.tags/generate-tags entity)
                            :transaction/type :transaction.type/expense
                            :transaction/created-at (time-coerce/to-long date))))]
    (let [left-side (left-side-of-expenses)]
      (into [] (comp (map html-t->jourmoney->t)
                    (filter (fn [{:keys [:transaction/amount]}]
                              (pos? amount)))
                    (map #(update % :transaction/amount str)))
           transactions))))

;; END JOUR MONEY

(defn import-parsed
  "Test helper. Since parsing takes most of the time and we don't
  want need to test parsing, we can skip the parsing step and only
  run the code we've written."
  [parsed]
  (->> parsed
       remove-style-and-attrs
       html->transaction-pages
       transaction-pages->parsed-pages
       (sequence (mapcat page->jourmoney-entities))))

(defn import-expenses [expenses-html]
  {:pre  [(or (string? expenses-html)
              (instance? java.io.Reader expenses-html))]
   :post [(seq %)]}
  (import-parsed (parse expenses-html)))

(defn raw-expenses []
  {:post [(instance? java.io.Reader %)]}
  (InputStreamReader.
    (GZIPInputStream.
     (io/input-stream
       (io/resource "private/test/import/ods/expenses.html.gz")))))

(defn parsed-transactions []
  (read-string
    (slurp
      (GZIPInputStream.
        (io/input-stream
          (io/resource "private/test/import/ods/parsed-expenses.edn.gz"))))))

(defn -main
  "Takes a path to an .html file with expenses, exported from Open office.
  Returns a list with jourmoney transaction entities."
  [& [path :as args]]
  (when (or (nil? path)
            (not (s/ends-with? path ".html")))
    (throw (ex-info "Must specify path to expenses with .html extension" {:path path}))
    (import-expenses (slurp path))))
