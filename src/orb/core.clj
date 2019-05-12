(ns orb.core
  (:refer-clojure :exclude [send delete list])
  (:require
   [saw.core :as saw]
   [orb.event :as event]
   [orb.lambda :as lambda]))

(defn request [fn-name payload]
  (lambda/invoke :request-response fn-name payload))

(defn send [fn-name payload]
  (lambda/invoke :event fn-name payload))

(defn list []
  (event/list-rules))

(defn add [name & {:keys [rule lambda input]}]
  (let [rule-arn (event/add-rule name rule)]
    (event/add-target name lambda input)
    (lambda/add-permission lambda name rule-arn)
    rule-arn))

(defn delete [name]
  (-> (event/find-target name)
      :arn
      (lambda/remove-permission name))
  (event/delete-rule name)
  :ok)

(defn init! [auth]
  (let [session (saw/login auth)
        region  (or (:region auth) "us-east-1")]
    (event/init! region)
    (lambda/init! region)
    :ok))
