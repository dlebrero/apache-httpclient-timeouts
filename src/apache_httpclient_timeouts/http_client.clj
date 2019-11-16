(ns apache-httpclient-timeouts.http-client
  (:require
    [clj-http.client :as client]
    [clojure.tools.logging :as log]
    [clj-http.conn-mgr :as http.conn-mgr]))

(defn- default-connection-manager-opts
  ":timeout - Time that connections are left open before automatically closing ... in seconds"
  [opts]
  (merge {:timeout 10 :threads 2 :insecure? false :default-per-route 2}
    (select-keys opts [:timeout :threads :insecure? :default-per-route])))

(defn new-connection-manager
  ([] (new-connection-manager {}))
  ([opts]
   (http.conn-mgr/make-reusable-conn-manager (default-connection-manager-opts opts))))

(defn shutdown-manager [connection-manager]
  (http.conn-mgr/shutdown-manager connection-manager))

(defn req-opts
  "All these timeouts are in milliseconds
  :connection-timeout - time to establish the socket
  :connection-request-timeout - time to have a free socket
  :socket-timeout - time to get the response"
  [socket-timeout]
  {:connection-timeout 2000 :connection-request-timeout 50 :socket-timeout socket-timeout})

(time (client/get "http://nginx/"))
(time
  (try
    (client/get "http://toxiproxy:22220/"
      {:socket-timeout 10000
       :connection-timeout 2000})
    (catch Exception e (.printStackTrace e))))

(time
  (try
    (client/get "http://10.255.255.1:22220/"
      {:socket-timeout 10000
       ;:connection-timeout 20000000
       })
    (catch Exception e)))

(client/get "http://toxiproxy:8474/proxies")
(client/post "http://toxiproxy:8474/populate"
  {:form-params [{:enabled true
                  :upstream "nginx:80"
                  :listen "toxiproxy:22220"
                  :name "proxiednginx"}]
   :content-type :json})

(client/post "http://toxiproxy:8474/proxies/proxiednginx/toxics"
  {:form-params {:attributes {:latency 20000
                              :jitter 0}
                 :toxicity 1.0
                 :stream "upstream"
                 :type "latency"
                 :name "first"}
   :content-type :json})

(client/post "http://toxiproxy:8474/proxies/proxiednginx/toxics"
  {:form-params {:attributes {:delay 800000
                              :size_variation 1
                              :average_size 2}
                 :toxicity 1.0
                 :stream "downstream"
                 :type "slicer"
                 :name "first"}
   :content-type :json})

(client/delete "http://toxiproxy:8474/proxies/proxiednginx/toxics/first")