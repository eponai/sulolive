(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"
              ;;  :classifier "aot"
                :exclusion [org.clojure/data.json]
                 ]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.2.371"]
                 ;; core.memoize is needed to work around lein+core.async dependency issue.
                 [org.clojure/core.memoize "0.5.6"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [sablono "0.4.0"]
		 [hiccup "1.0.5"]
                 [org.omcljs/om "1.0.0-alpha14"]
                 [devcards "0.2.0-8"]
                 [cljs-ajax "0.5.0"]
                 [datascript "0.13.3"]
                 [cljsjs/pikaday "1.3.2-0"]
                 [compojure "1.4.0"]
                 [environ "1.0.1"]
		 [bidi "1.21.1"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring-transit "0.1.4"]
                 [clj-http "2.0.0"]
                 [com.datomic/datomic-pro "0.9.5302"
                  :exclusions [joda-time]]
                 [clj-time "0.11.0"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [garden "1.3.0-SNAPSHOT"]
                 [com.cemerick/friend "0.2.1"]
                 [com.draines/postal "1.11.3"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-1"]
            [lein-ring "0.9.7"]
            [lein-environ "1.0.1"]]
  :min-lein-version "2.0.0"

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :target-path "target/%s"
  :source-paths ["src" "mains" "test"]
  :ring {:handler eponai.server.datomic_dev/app
         :init eponai.server.core/init}
  :profiles {:uberjar {:aot :all}}
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username ~(System/getenv "DATOMIC_EMAIL")
                                   :password ~(System/getenv "DATOMIC_KEY")}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src" "test"]
                :figwheel {:on-jsload "eponai.client.client_tests/run"}
                :compiler {:main "eponai.client.core"
                           :asset-path "js/out"
                           :output-to "resources/public/dev/js/out/budget.js"
                           :output-dir "resources/public/dev/js/out/"
                           :optimizations :none
                           :source-map true}}
               {:id "devcards"
                :source-paths ["src" "test"]
                :figwheel { :devcards true } ;; <- note this
                :compiler { :main    "eponai.devcards.devcards_main"
                           :asset-path "js/out"
                           :output-to  "resources/public/devcards/js/out/budget.js"
                           :output-dir "resources/public/devcards/js/out"
                           :source-map-timestamp true }}
               {:id "release"
                :source-paths ["src"]
                :compiler {:main "eponai.client.core"
                           :asset-path "js/out"
                           :output-to "resources/public/release/js/out/budget.js"
                           :output-dir "resources/public/release/js/out/"
                           :optimizations :advanced
                        ;; :pseudo-names true
                        ;; :pretty-print true
                           }}]}
  :npm {:dependencies [[source-map-support "0.3.2"]]})
