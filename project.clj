(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"
              ;;  :classifier "aot"
                :exclusion [org.clojure/data.json]
                 ]
                 [org.clojure/data.json "0.2.6"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [sablono "0.3.6"]
                 [org.omcljs/om "0.9.0-SNAPSHOT"]
                 [devcards "0.2.0-1"]
                 [cljs-ajax "0.5.0"]
                 [datascript "0.13.1"]
                 [org.clojure/test.check "0.8.1"]
                 [compojure "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [clj-http "2.0.0"]
                 [com.datomic/datomic-pro "0.9.5302"
                  :exclusions [joda-time]]
                 [clj-time "0.11.0"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]
            [lein-ring "0.7.3"]]

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :main ^:skip-aot flipmunks.budget.core
  :target-path "target/%s"
  :source-paths ["src/server" "test/server"]
  :ring {:handler flipmunks.budget.core/app}
  :profiles {:uberjar {:aot :all}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/client" "test/client"]
                :figwheel {:on-jsload "flipmunks.budget.client_tests/run"}
                :compiler {:main "flipmunks.budget.core"
                           :asset-path "js/out"
                           :output-to "resources/public/dev/js/out/budget.js"
                           :output-dir "resources/public/dev/js/out/"
                           :optimizations :none
                           :source-map true}}
               {:id "devcards"
                :source-paths ["src/client" "test/client"]
                :figwheel { :devcards true } ;; <- note this
                :compiler { :main    "flipmunks.budget.devcards_test"
                           :asset-path "js/out"
                           :output-to  "resources/public/devcards/js/out/budget.js"
                           :output-dir "resources/public/devcards/js/out"
                           :source-map-timestamp true }}
               {:id "release"
                :source-paths ["src/client"]
                :compiler {:main "flipmunks.budget.core"
                           :asset-path "js/out"
                           :output-to "resources/public/release/js/out/budget.js"
                           :output-dir "resources/public/release/js/out/"
                           :optimizations :advanced
                        ;; :pseudo-names true
                        ;; :pretty-print true
                           }}]}
  :npm {:dependencies [[source-map-support "0.3.2"]]})
