(ns consul.agent
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [go-loop timeout <!]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]))


(def ^:dynamic *consul-url* "http://127.0.0.1:8500")
(def ^:private alive (atom #{}))


(defn- default-callback [{:keys [status] :as response}]
  (when (or
          (nil? status)
          (>= status 400))
    (log/warn "Error requesting consul agent: " response)))


(defn- http-get [path]
  (log/debug "GET" (str *consul-url* "/v1/agent" path))
  (http/request {:url (str *consul-url* "/v1/agent" path)}))


(defn- http-put [path body callback]
  (log/debug "PUT" (str *consul-url* "/v1/agent" path) "\n" (json/encode body {:pretty true}))
  (http/request {:method :put
                 :url    (str *consul-url* "/v1/agent" path)
                 :body   (if body (json/encode body) "")}
                (or callback default-callback)))


(defn service-list []
  (http-get "/services"))


(defn service-details [service-id]
  (http-get (str "/service/" service-id)))


(defn service-register [request & [callback]]
  (http-put "/service/register" request callback))


(defn service-deregister [service-id & [callback]]
  (http-put (str "/service/deregister/" service-id) nil callback))


(defn check-update [check-id status & [callback]]
  {:pre [(#{:passing :warning :critical} status)]}
  (http-put (str "/check/update/" check-id) {:status status} callback))


(defn check-update-with-register
  "check update with registration if not registered yet"
  [check-id check-status register-request]
  (let [consul-url *consul-url*]
    (check-update check-id check-status
                  (fn [{:keys [status body] :as response}]
                    (log/debug "-->" status body)
                    (if (and status
                             (>= status 400)
                             (string/includes? body (format "\"%s\"" check-id))
                             (string/includes? body "TTL"))
                      (binding [*consul-url* consul-url]
                        (service-register register-request
                                          (fn [{:keys [status] :as response}]
                                            (if (= status 200)
                                              (binding [*consul-url* consul-url]
                                                (check-update check-id check-status))
                                              (default-callback response)))))
                      (default-callback response))))))


(defn heartbeat
  "single heartbeat"
  [{:keys [id name address port ttl deregister-critical-service-after]}]
  (let [check-id (str id ":ttl-check")]
    (check-update-with-register check-id :passing
                                {:id      id
                                 :name    (or name id)
                                 :address address
                                 :port    port
                                 :check   {:CheckId                        check-id
                                           :TTL                            ttl
                                           :DeregisterCriticalServiceAfter deregister-critical-service-after}})))


(defn start-heartbeat [{:keys [id interval-ms] :as params}]
  (service-deregister id)
  (swap! alive #(conj % [*consul-url* id]))
  (go-loop []
    (when (@alive [*consul-url* id])
      (heartbeat params)
      (<! (timeout interval-ms))
      (recur))))


(defn stop-heartbeat [service-id]
  (swap! alive #(disj % [*consul-url* service-id]))
  (service-deregister service-id))
