(ns apache-httpclient-timeouts.http-client
  (:require
    [clj-http.client :as client]
    [clojure.tools.logging :as log]
    [clj-http.conn-mgr :as conn-manager]))

;; nginx is working
(client/get "http://local.nginx/")

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
  ;; Connection pool Time To Live
  ;; Create a new connection pool
  (def cp (conn-manager/make-reusable-conn-manager
            {:timeout 1 ; in seconds. This is called TimeToLive in PoolingHttpClientConnectionManager
             }))

  (dotimes [_ 10]
    (log/info "Send Http request")
    (-> (client/get "http://local.nginx/" {:connection-manager cp}) :headers)
    (Thread/sleep 500))

  ;; A connection pool with 20 seconds TTL
  (def cp-2 (conn-manager/make-reusable-conn-manager
            {:timeout 20 ; in seconds.
             }))

  (dotimes [_ 10]
    (log/info "Send Http request")
    (-> (client/get "http://local.nginx/" {:connection-manager cp-2}) :headers)
    (Thread/sleep 500))

  )


(comment
  ;; Connection pool timeout

  (client/delete "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics/first")

  ;; Read requests will take 20 seconds
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:latency 20000
                                :jitter 0}
                   :toxicity 1.0
                   :stream "upstream"
                   :type "latency"
                   :name "first"}
     :content-type :json})

  ;; Created a connection pool with 3 max connections
  (def cp-3 (conn-manager/make-reusable-conn-manager
              {:timeout 100
               :threads 3           ;; Max connections in the pool.
               :default-per-route 3 ;; Max connections per route (~ max connection to a server)
               }))

  ;; Send 4 Http requests
  (dotimes [_ 4]
    (future
      (time
        (try
          (client/get "http://local.toxiproxy:22220/"
            {:connection-manager cp-3
             :connection-request-timeout 1000})
          (catch Exception e
            (log/info (.getClass e) ":" (.getMessage e)))))))

  )