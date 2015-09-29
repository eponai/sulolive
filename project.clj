(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122" 
                  :classifier "aot"
                  :exclusion [org.clojure/data.json]]
                 [org.clojure/data.json "0.2.6" :classifier "aot"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [sablono "0.3.6"]
                 [org.omcljs/om "0.9.0"]
                 [org.clojure/test.check "0.8.2"]
                 [compojure "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.3.9"]
            [lein-ring "0.7.3"]]

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :main ^:skip-aot flipmunks.budget.core
  :target-path "target/%s"
  :source-paths ["src/server" "test"]
  :ring {:handler flipmunks.budget.core/app}
  :profiles {:uberjar {:aot :all}} 

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/client"]
                :figwheel true
                :compiler {:main "flipmunks.budget.core"
                           :asset-path "out"
                           :output-to "resources/public/dev/out/budget.js"
                           :output-dir "resources/public/dev/out/"
                           :optimizations :none
                           :source-map true}}
               {:id "release"
                :source-paths ["src/client"]
                :compiler {:main "flipmunks.budget.core"
                           :asset-path "out"
                           :output-to "resources/public/release/out/budget.js"
                           :output-dir "resources/public/release/out/"
                           :optimizations :advanced
                           }}]}
  :npm {:dependencies [[source-map-support "0.3.2"]]})
