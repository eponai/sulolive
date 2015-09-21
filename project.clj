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
                 [sablono "0.3.6"]
                 [org.omcljs/om "0.9.0"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.3.9"]]

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :main ^:skip-aot flipmunks.budget.core
  :target-path "target/%s"
  :source-paths ["src/server"]
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
                           :source-map true}}]}
  :npm {:dependencies [[source-map-support "0.3.2"]]})
