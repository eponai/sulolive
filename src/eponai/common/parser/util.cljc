(ns eponai.common.parser.util)

(defn post-process-parse
  "Calls post-parse-fn for each [k v] in the result of a (parser env query-expr).
  The post-parse-fn should be a 3 arity function taking [env key value].
  The return of the post-parse-fn will be assoced to the key k. The key can also be removed
  from the parsed result by returning :dissoc from the post-parse-fn."
  ([post-parse-fn] (post-process-parse post-parse-fn []))
  ([post-parse-fn keys-to-process-first]
   (fn
     ([env parsed-result]
      (let [post-parse-subset (fn [result subset]
                                (reduce-kv (fn [m k v]
                                             (let [ret (post-parse-fn env k v)]
                                               (cond
                                                 (or (nil? ret) (identical? ret v)) m
                                                 (= ret :dissoc) (dissoc m k)
                                                 :else (assoc m k ret))))
                                           result
                                           subset))]
        (reduce post-parse-subset
                parsed-result
                [(select-keys parsed-result keys-to-process-first)
                 (apply dissoc parsed-result keys-to-process-first)]))))))
