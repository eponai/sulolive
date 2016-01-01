(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
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
                 [sablono "0.5.3"]
                 [hiccup "1.0.5"]
                 [org.omcljs/om "1.0.0-alpha28"]
                 ;; Sablono needs the react-dom-server to compile.
                 [cljsjs/react-dom-server "0.14.3-0"]
                 [devcards "0.2.0-8"]
                 [cljs-ajax "0.5.0"]
                 [datascript "0.13.3"]
                 [cljsjs/pikaday "1.3.2-0"]
                 [compojure "1.4.0"]
                 [environ "1.0.1"]
                 [bidi "1.21.1"]
                 [ring/ring-core "1.4.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring-transit "0.1.4"]
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [org.clojure/test.check "0.8.1"]
                 [clj-http "2.0.0"]
                 [cljs-http "0.1.39"]
                 [com.datomic/datomic-pro "0.9.5302"
                  :exclusions [joda-time]]
                 [clj-time "0.11.0"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [garden "1.3.0"]
                 [com.cemerick/friend "0.2.1"]
                 [com.draines/postal "1.11.3"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-1"]
            [lein-ring "0.9.7"]
            [lein-test-out "0.3.1"]
            [lein-environ "1.0.1"]]
  :min-lein-version "2.0.0"

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :target-path "target/%s"
  :source-paths ["src" "env/clj" "test"]
  :ring {:handler eponai.server.core/app
         :init    eponai.server.core/init}
  :main eponai.server.core
  :repl-options {:init-ns eponai.repl
                 :init (eponai.repl/init)}
  :profiles {:uberjar {:aot   :all
                       :prep-tasks ["compile" ["cljsbuild" "once" "release"]]}}
  :uberjar-name "budget-0.1.0-SNAPSHOT-standalone.jar"
  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username ~(System/getenv "DATOMIC_EMAIL")
                                   :password ~(System/getenv "DATOMIC_KEY")}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :cljsbuild
  {:builds        [{:id           "dev"
                    :source-paths ["src" "test"]
                    :figwheel     {:on-jsload "eponai.client.tests/run"}
                    :compiler     {:main          "eponai.client.core"
                                   :asset-path    "js/out"
                                   :output-to     "resources/public/dev/js/out/budget.js"
                                   :output-dir    "resources/public/dev/js/out/"
                                   :optimizations :none
                                   :source-map    true}}
                   {:id           "devcards"
                    :source-paths ["src" "test"]
                    :figwheel     {:devcards true}          ;; <- note this
                    :compiler     {:main                 "eponai.devcards.devcards_main"
                                   :asset-path           "js/out"
                                   :output-to            "resources/public/devcards/js/out/budget.js"
                                   :output-dir           "resources/public/devcards/js/out"
                                   :source-map-timestamp true}}
                   {:id           "test"
                    :source-paths ["src" "test"]
                    :compiler     {:output-to     "resources/public/test/js/out/budget.js"
                                   :output-dir    "resources/public/test/js/out"
                                   ;; asset-path set like this to use with karma
                                   :asset-path    "base/resources/public/test/js/out"
                                   :main          "circle.karma"
                                   :optimizations :none
                             ;;    :pretty-print  true
                               ;;  :psuedo-names  true
                                  }}
                   {:id           "release"
                    :source-paths ["src"]
                    :compiler     {:main          "eponai.client.core"
                                   :asset-path    "js/out"
                                   :output-to     "resources/public/release/js/out/budget.js"
                                   :output-dir    "resources/public/release/js/out/"
                                   :optimizations :advanced
                                   ;;   :pseudo-names true
                                   ;;   :pretty-print true
                                   }}]
   :test-commands {"frontend-unit-tests"
                   ["node_modules/karma/bin/karma" "start" "karma.conf.js" "--single-run"]}}
  :npm {:dependencies [[source-map-support "0.3.2"]
                       [karma "0.13.9"]
                       [karma-junit-reporter "0.3.8"]
                       ;; We can test against other launchers later if we want.
                       ;; I.e. phantomjs, firefox and more?
                       [karma-chrome-launcher "0.1.8"]
                       ;; Using CircleCI's branch of karma-cljs.test to get
                       ;; re-use their circle.karma.cljs namespace as an
                       ;; entrypoint.
                       [karma-cljs.test "git://github.com/circleci/karma-cljs.test#077e0ac53af3506f4d11d8bd157bf9de89761a9e"]
                       [karma-closure "0.1.1"]]})

