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

;; nginx is working
(client/get "http://local.nginx/")

(time
  (do
    (->
      (client/get "http://local.nginx/")
      :headers)

    ;(client/get "http://192.168.0.1/")
    ))

;; setup ToxiProxy route to nginx
(client/post "http://local.toxiproxy:8474/populate"
  {:form-params [{:enabled true
                  :upstream "local.nginx:80"
                  :listen "local.toxiproxy:22220"
                  :name "proxied.nginx"}]
   :content-type :json})

;(client/get "http://local.toxiproxy:8474/proxies")

(comment
  ;; Connection timeout
  ;; First no specific connection timeout:
  (time
    (try
      (client/get "http://10.255.255.1:22220/")
      (catch Exception e)))
  ;; Same url, but now with a connection timeout
  (time
    (try
      (client/get "http://10.255.255.1:22220/"
        {:connection-timeout 2000})
      (catch Exception e
        (log/info (.getClass e) ":" (.getMessage e))))))

(comment
  ;; Socket timeout
  ;;
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:latency 2000000
                                :jitter 0}
                   :toxicity 1.0
                   :stream "upstream"
                   :type "latency"
                   :name "first"}
     :content-type :json})
  ;; First no specific socket timeout
  (time
    (try
      (client/get "http://local.toxiproxy:22220/")
      (catch Exception e)))

  ;; same url, but now with a socket timeout
  (time
    (try
      (client/get "http://local.toxiproxy:22220/"
        {:socket-timeout 2000})
      (catch Exception e
        (log/info (.getClass e) ":" (.getMessage e)))))


  (client/delete "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics/first")
  ;; Toxic that returns ~ 2 bytes per second
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:delay 800000
                                :size_variation 1
                                :average_size 2}
                   :toxicity 1.0
                   :stream "downstream"
                   :type "slicer"
                   :name "first"}
     :content-type :json})

  ;; same url, same timeout, but response takes forever
  (time
    (try
      (client/get "http://local.toxiproxy:22220/"
        {:socket-timeout 2000})
      (catch Exception e
        (log/info (.getClass e) ":" (.getMessage e))))))


(comment
  (def cp (new-connection-manager {:timeout 100 :threads 2 :default-per-route 2}))

  (shutdown-manager cp)
  (time
    (-> (client/get "http://local.nginx/" {:connection-manager cp}) :headers)))
