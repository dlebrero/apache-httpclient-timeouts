(defproject apache-httpclient-timeouts "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.10.0"]
                 [cheshire "5.8.1"]
                 [http.async.client "1.3.1"]
                 [duct/core "0.7.0"]
                 [duct/module.logging "0.4.0"]
                 [duct/module.web "0.7.0" :exclusions [org.slf4j/slf4j-nop]]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]]
  :plugins [[duct/lein-duct "0.12.1"]]
  :main ^:skip-aot apache-httpclient-timeouts.main
  :resource-paths ["resources" "target/resources"]
  :prep-tasks     ["javac" "compile" ["run" ":duct/compiler"]]
  :middleware     [lein-duct.plugin/middleware]
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:prep-tasks ^:replace ["javac" "compile"]
          :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :profiles/dev {}
   :project/dev {:source-paths ["dev/src"]
                 :resource-paths ["dev/resources"]
                 :repl-options {:init (do
                                        (println "Starting BackEnd ...")
                                        ;(dev)
                                        ;(go)
                                        )
                                :host "0.0.0.0"
                                :port 47480}
                 :dependencies [[integrant/repl "0.3.1"]
                                [eftest "0.5.7"]
                                [kerodon "0.9.0"]]}})
