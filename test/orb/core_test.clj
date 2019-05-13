(ns orb.core-test
  (:require
   [clojure.test :refer :all]
   [orb.core :as orb]))

(deftest ^:integration create-list-test
  (orb/init! :qa-002)
  (is (= [{:name "test-rule"
           :state "ENABLED"
           :rule  "rate(5 minutes)"}]
         (do
           (orb/create! "test-rule"
                        :rule   "rate(5 minutes)"
                        :lambda (System/getenv "ORB_LAMBDA_ARN")
                        :input  {:cue "health.ping" :param {}})
           (orb/list-rules)
           (orb/delete! "test-rule")))))
