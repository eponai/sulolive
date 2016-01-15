(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [bidi "1.21.1"]
                 [clj-http "2.0.0"]
                 [clj-time "0.11.0"]
                 [compojure "1.4.0"]
                 [com.cemerick/friend "0.2.1"
                  :exclusions [org.clojure/core.cache]]
                 [com.cemerick/url "0.1.1"]
                 [com.datomic/datomic-pro "0.9.5302"
                  :exclusions [joda-time]]
                 [com.draines/postal "1.11.3"]
                 [com.taoensso/timbre "4.2.0"]
                 [environ "1.0.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.371"]
                 [org.clojure/core.memoize "0.5.8"]         ; needed to work around lein+core.async dependency issue.
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.namespace "0.2.11"]
                 ; ring helpers
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring-transit "0.1.4"]
                 [prone "1.0.0"]

                 ;CLJS
                 [com.cognitect/transit-cljs "0.8.225"]
                 [org.clojure/clojurescript "1.7.228"
                  ;;  :classifier "aot"
                  :exclusion [org.clojure/data.json]
                  ]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [cljs-http "0.1.39"]
                 [org.clojure/tools.reader "1.0.0-alpha2"]
                 [garden "1.3.0"]
                 [org.omcljs/om "1.0.0-alpha28"]
                 [datascript "0.13.3"]
                 [sablono "0.5.3"]
                 ; Sablono needs the react packages to compile.
                 [cljsjs/react-dom-server "0.14.3-0"]
                 [cljs-ajax "0.5.0"]
                 [cljsjs/pikaday "1.3.2-0"]
                 [cljsjs/stripe "2.0-0"]

                 ;; Testing
                 [lein-doo "0.1.6"]
                 [devcards "0.2.0-8"]
                 [org.clojure/test.check "0.9.0"]]
  :exclusions [[org.clojure/test.check]]

  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-doo "0.1.6"]
            [lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-3"]
            [lein-ring "0.9.7"]
            [lein-test-out "0.3.1"]
            [lein-environ "1.0.1"]] 
  
  :min-lein-version "2.0.0"
  :clean-targets ^{:protect false} ["resources/public/dev/js/out"
                                    "resources/public/devcards/js/out"
                                    "resources/public/release/js/out"
                                    "resources/public/test/js/out"
                                    "target/uberjar"
                                    "target/uberjar+uberjar"]

  ;;;;;;;;;;;;;
  ;; AWS+Docker deploy:
  ;;;;;;;;;;;;;
  :uberjar-name "budget-0.1.0-SNAPSHOT-standalone.jar"
  :profiles {:uberjar {:aot                :all
                       :prep-tasks         ["compile" ["cljsbuild" "once" "release"]]}}

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :target-path "target/%s"
  :source-paths ["src"]
  :test-paths ["test" "env/clj"]
  :ring {:handler eponai.server.core/app
         :init    eponai.server.core/init}
  :main eponai.server.core
  :repl-options {:init-ns eponai.repl
                 :init (eponai.repl/init)}
  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username ~(System/getenv "DATOMIC_EMAIL")
                                   :password ~(System/getenv "DATOMIC_KEY")}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :doo {:paths {:phantom "./node_modules/phantomjs/bin/phantomjs"}}
  :cljsbuild
  {:builds        [{:id           "dev"
                    :source-paths ["src/"
                                   "test/"]
                    :figwheel     {:on-jsload "eponai.client.figwheel/run"}
                    :compiler     {:main          "eponai.client.core"
                                   :asset-path    "/dev/js/out"
                                   :output-to     "resources/public/dev/js/out/budget.js"
                                   :output-dir    "resources/public/dev/js/out/"
                                   :optimizations :none
                                   :source-map    true}}
                   {:id           "devcards"
                    :source-paths ["src/"
                                   "test/"]
                    :figwheel     {:devcards true}          ;; <- note this
                    :compiler     {:main                 "eponai.devcards.devcards_main"
                                   :asset-path           "/devcards/js/out"
                                   :output-to            "resources/public/devcards/js/out/budget.js"
                                   :output-dir           "resources/public/devcards/js/out"
                                   :source-map-timestamp true}}
                   {:id           "test"
                    :source-paths ["src/"
                                   "test/"]
                    :compiler     {:output-to     "resources/public/test/js/out/budget.js"
                                   :output-dir    "resources/public/test/js/out"
                                   :main          "eponai.client.tests"
                                   :optimizations :none
                                  }}
                   {:id           "release"
                    :source-paths ["src/"]
                    :compiler     {:main          "eponai.client.core"
                                   :asset-path    "/release/js/out"
                                   :output-to     "resources/public/release/js/out/budget.js"
                                   :output-dir    "resources/public/release/js/out/"
                                   :optimizations :advanced
                                   ;;   :parallel-build true
                                   ;;   :pseudo-names true
                                   ;;   :pretty-print true
                                   }}]
   :test-commands {"frontend-unit-tests"
                   ["node_modules/karma/bin/karma" "start" "karma.conf.js" "--single-run"]}}
  :npm {:dependencies [[source-map-support "0.3.2"]
                       [react "0.14.3"]
                       [pikaday "1.3.2"]
                       [karma "0.13.9"]
                       [karma-junit-reporter "0.3.8"]
                       [phantomjs "1.9.19"]
                       ;; We can test against other launchers later if we want.
                       ;; I.e. phantomjs, firefox and more?
                       [karma-chrome-launcher "0.1.8"]
                       ;; Using CircleCI's branch of karma-cljs.test to get
                       ;; re-use their circle.karma.cljs namespace as an
                       ;; entrypoint.
                       [karma-cljs.test "git://github.com/circleci/karma-cljs.test#077e0ac53af3506f4d11d8bd157bf9de89761a9e"]
                       [karma-closure "0.1.1"]]})

