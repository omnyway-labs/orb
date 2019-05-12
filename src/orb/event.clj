(ns orb.event
  (:require
   [saw.core :as saw]
   [orb.event-pattern :as ep]
   [clojure.data.json :as json])
  (:import
   [com.amazonaws.regions Regions]
   [com.amazonaws.services.cloudwatchevents
    AmazonCloudWatchEventsClientBuilder]
   [com.amazonaws.services.cloudwatchevents.model
    PutTargetsRequest
    RemoveTargetsRequest
    Target
    TestEventPatternRequest
    PutRuleRequest
    DeleteRuleRequest
    RemoveTargetsRequest
    ListRulesRequest
    ListTargetsByRuleRequest
    PutEventsRequest
    PutEventsRequestEntry
    TestEventPatternRequest]))

(defonce client (atom nil))

(defn make-client [region]
  (-> (AmazonCloudWatchEventsClientBuilder/standard)
      (.withCredentials (saw/creds))
      (.withRegion region)
      .build))

(defn as-target [t]
  {:id    (.getId t)
   :arn   (.getArn t)
   :input (-> (.getInput t)
              (json/read-str :key-fn keyword))})

(defn find-target [rule-name]
  (->> (doto (ListTargetsByRuleRequest.)
         (.withRule rule-name))
       (.listTargetsByRule @client)
       (.getTargets)
       (first)
       (as-target)))

(defn- as-rule [r]
  {:name     (.getName r)
   :state    (.getState r)
   :rule     (.getScheduleExpression r)})

(defn list-rules []
  (->> (ListRulesRequest.)
       (.listRules @client)
       (.getRules)
       (map as-rule)))

(defn put-schedule-rule [schedule-expression name]
  (->> (doto (PutRuleRequest.)
         (.withState "ENABLED")
         (.withScheduleExpression schedule-expression)
         (.withName name))
       (.putRule @client)
       (.getRuleArn)))

(defn put-event-rule [event-pattern name]
  (let [exp (ep/make event-pattern)]
    (->> (doto (PutRuleRequest.)
           (.withState "ENABLED")
           (.withEventPattern exp)
           (.withName name))
         (.putRule @client)
         (.getRuleArn))))

(defn make-target [rule-name orb-arn input]
  (let [input-str (json/write-str input)]
    (doto (Target.)
      (.withId rule-name)
      (.withInput input-str)
      (.withArn orb-arn))))

(defn add-rule [rule-name rule]
  (if (and (map? rule) (:event-id rule))
    (put-event-rule rule rule-name)
    (put-schedule-rule rule rule-name)))

(defn add-target [rule-name orb-arn input]
  (->> (doto (PutTargetsRequest.)
         (.withTargets [(make-target rule-name orb-arn input)])
         (.withRule rule-name))
       (.putTargets @client)))

(defn delete-target [rule-name target-id]
  (->> (doto (RemoveTargetsRequest.)
         (.withRule rule-name)
         (.withIds  [target-id]))
       (.removeTargets @client)))

(defn delete-rule [rule-name]
  (->> (find-target rule-name)
       :id
       (delete-target rule-name))
  (->> (doto (DeleteRuleRequest.)
           (.withName rule-name))
       (.deleteRule @client)))

(defn init! [region]
  (reset! client (make-client region)))
