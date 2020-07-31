(ns consul.agent-test
  (:require [clojure.test :refer :all]
            [consul.agent :as agent]
            [clojure.core.async :refer [timeout <!!]]))


(deftest test-1-start-stop-heartbeat
  (testing "start-stop-heartbeat"
    (binding [agent/*consul-url* "http://127.0.0.1:8500"]

      (agent/start-heartbeat {:id                                "my-service"
                              :address                           "127.0.0.1"
                              :port                              8080
                              :ttl                               "4s"
                              :deregister-critical-service-after "1m"
                              :interval-ms                       2000})

      (<!! (timeout 500))

      (let [{:keys [status body]} @(agent/service-details "my-service")]
        (println body)
        (is (= status 200)))

      (<!! (timeout 8500))

      (let [{:keys [status body]} @(agent/service-details "my-service")]
        (println body)
        (is (= status 200)))

      (agent/stop-heartbeat "my-service")

      (<!! (timeout 500))

      (let [{:keys [status body]} @(agent/service-details "my-service")]
        (println body)
        (is (= status 404))))))
