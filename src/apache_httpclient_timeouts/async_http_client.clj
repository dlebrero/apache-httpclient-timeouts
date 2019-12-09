(ns apache-httpclient-timeouts.async-http-client
  (:require
    [clojure.tools.logging :as log]
    [clj-http.client :as client])
  (:import org.asynchttpclient.Dsl
           (org.asynchttpclient AsyncHttpClient)))

(defn create-client [& {:keys [connection-timeout request-timeout read-timeout idle-in-pool-timeout connection-ttl max-conns-total max-conns-per-host]}]
  (Dsl/asyncHttpClient
    (cond-> (Dsl/config)
      connection-timeout (.setConnectTimeout connection-timeout)
      read-timeout (.setReadTimeout read-timeout)
      request-timeout (.setRequestTimeout request-timeout)
      idle-in-pool-timeout (.setPooledConnectionIdleTimeout idle-in-pool-timeout)
      connection-ttl (.setConnectionTtl connection-ttl)
      max-conns-total (.setMaxConnections max-conns-total)
      max-conns-per-host (.setMaxConnectionsPerHost max-conns-per-host)
      )))

(defn GET [^AsyncHttpClient async-http-client url]
  (->
    async-http-client
    (.prepareGet url)
    .execute
    deref))

;; nginx is working
(def default-async-client (Dsl/asyncHttpClient))

(GET default-async-client "http://local.nginx/")

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
      (GET default-async-client "http://10.255.255.1:22220/")
      (catch Exception e)))
  ;; Same url, but now with a connection timeout
  (time
    (try
      (with-open [with-conn-timeout (create-client :connection-timeout 2000)]
        (GET with-conn-timeout "http://10.255.255.1:22220/"))
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
      (GET default-async-client "http://local.toxiproxy:22220/")
      (catch Exception e)))

  ;; same url, but now with a socket timeout
  (time
    (try
      (with-open [with-conn-timeout (create-client :read-timeout 2000)]
        (GET with-conn-timeout "http://local.toxiproxy:22220/"))
      (catch Exception e
        (log/info (.getClass e) ":" (.getMessage e)))))


  (client/delete "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics/first")
  ;; Toxic that returns ~ 2 bytes per second
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:delay 800000
                                :size_variation 1
                                :average_size 2000}
                   :toxicity 1.0
                   :stream "downstream"
                   :type "slicer"
                   :name "first"}
     :content-type :json})

  ;; same url, same timeout, but response takes forever
  (time
    (try
      (with-open [with-read-timeout (create-client :read-timeout 2000)]
        (GET with-read-timeout "http://local.toxiproxy:22220/big.html"))
      (catch Exception e
        (log/info (.getClass e) ":" (.getMessage e))))))


(comment
  ;; Connection pool Time To Live
  ;; Create a new connection pool
  (with-open [with-ttl (create-client :connection-ttl 1000)]
    (dotimes [_ 10]
      (log/info "Send Http request")
      (GET with-ttl "http://local.toxiproxy:22220/")
      (Thread/sleep 500)))


  ;; A connection pool with 20 seconds TTL
  (with-open [with-ttl (create-client :connection-ttl 20000)]
    (dotimes [_ 10]
      (log/info "Send Http request")
      (GET with-ttl "http://local.toxiproxy:22220/")
      (Thread/sleep 500)))

  ;; Connection pool Idle Timeout
  ;; Connection idle for more than one second. Never reused.
  (with-open [with-ttl (create-client :idle-in-pool-timeout 1000)]
    (dotimes [_ 10]
      (log/info "Send Http request")
      (->
        (async-client/GET with-ttl "http://local.toxiproxy:22220/")
        async-client/await
        async-client/failed?)
      (Thread/sleep 2000)))


  ;; Connection idle for less than than one second. Always reused.
  (with-open [with-ttl (async-client/create-client :idle-in-pool-timeout 1000)]
    (dotimes [_ 10]
      (log/info "Send Http request")
      (->
        (async-client/GET with-ttl "http://local.toxiproxy:22220/")
        async-client/await
        async-client/failed?)
      (Thread/sleep 500)))

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
  (def max-pool (create-client
                  :max-conns-per-host 3
                  :max-conns-total 3))

  (dotimes [_ 4]
    (future
      (time
        (try
          (GET max-pool "http://local.toxiproxy:22220/")
          (catch Exception e
            (log/info (.getClass e) ":" (.getMessage e)))))))

  )

;;;
;;; Testing the request-timeout
;;;
(def never-more-than-1-second
  (create-client
    :request-timeout 1000
    :max-conns-per-host 3
    :max-conns-total 3
    :connection-timeout 300000000
    :read-timeout 300000000
    ))

(comment
  ;; Connection timeout
  (time
    (try
      (GET never-more-than-1-second "http://10.255.255.1:22220/")
      (catch Exception e)))

  ;; Read timeout
  (client/delete "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics/first")
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:latency 2000000
                                :jitter 0}
                   :toxicity 1.0
                   :stream "upstream"
                   :type "latency"
                   :name "first"}
     :content-type :json})

  (time
    (try
      (GET never-more-than-1-second "http://local.toxiproxy:22220/")
      (catch Exception e)))

  ;; Read timeout. Very slow server.
  (client/delete "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics/first")
  (client/post "http://local.toxiproxy:8474/proxies/proxied.nginx/toxics"
    {:form-params {:attributes {:delay 800000
                                :size_variation 1
                                :average_size 2000}
                   :toxicity 1.0
                   :stream "downstream"
                   :type "slicer"
                   :name "first"}
     :content-type :json})

  (time
    (try
      (GET never-more-than-1-second "http://local.toxiproxy:22220/big.html")
      (catch Exception e)))

  )