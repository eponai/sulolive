(ns eponai.server.import.ods
  "Namespace for importing open office expenses.

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
            [eponai.server.import.ods.tags :as ods.tags])
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
   "JPY"  {:trim-start 1}})

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

(defn page->jourmoney-entities [{:keys [currencies transactions] :as page}]
  {:pre [(map? page)]}
  (letfn [(html-t->jourmoney->t [t]
            (let [date (f/parse html-date (get t "Date"))
                  entity (-> (parse-amount currencies t)
                             (assoc :transaction/date {:date/ymd (f/unparse eponai.format/ymd-date-formatter date)}
                                    :transaction/title (s/trim (get t "Product"))))]
              (assoc entity :transaction/tags (ods.tags/generate-tags entity)
                            :transaction/type :transaction.type/expense
                            :transaction/created-at (time-coerce/to-long date))))]
    (into [] (comp (map html-t->jourmoney->t)
                   (filter (fn [{:keys [:transaction/amount]}]
                             (pos? amount))))
          transactions)))

;; END JOUR MONEY

(defn doit-parsed
  "Test helper. Since parsing takes most of the time and we don't
  want need to test parsing, we can skip the parsing step and only
  run the code we've written."
  [parsed]
  (->> parsed
       remove-style-and-attrs
       html->transaction-pages
       transaction-pages->parsed-pages
       (sequence (mapcat page->jourmoney-entities))))

(defn doit [expenses-html]
  {:pre  [(or (string? expenses-html)
              (instance? java.io.Reader expenses-html))]
   :post [(seq %)]}
  (doit-parsed (parse expenses-html)))

(defn test-data []
  {:post [(instance? java.io.Reader %)]}
  (InputStreamReader.
    (GZIPInputStream.
     (io/input-stream
       (io/resource "private/test/import/ods/expenses.html.gz")))))

(defn test-data-parsed []
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
    (doit (slurp path))))
