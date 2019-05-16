(ns orb.lambda
  (:require
   [clojure.string :as str]
   [clojure.data.json :as json]
   [saw.core :as saw])
  (:import
   [java.util Base64]
   [com.amazonaws.services.lambda
    AWSLambdaClientBuilder]
   [com.amazonaws.services.lambda.model
    InvokeRequest
    LogType
    AddPermissionRequest
    RemovePermissionRequest
    ListFunctionsRequest
    InvocationType]))

(def client (atom nil))

(defn make-client [region]
  (-> (AWSLambdaClientBuilder/standard)
      (.withCredentials (saw/creds))
      (.withRegion region)
      .build))

(defn b64-decode [s]
  (.decode (Base64/getDecoder) s))

(defn b64-encode [s]
  (.encode (Base64/getEncoder) s))

(defn as-result [x]
  {:response (->> (.getPayload x)
                  (.array)
                  (map char)
                  (apply str)
                  (json/read-str))
   :log      (as-> (->> (.getLogResult x)
                        (b64-decode)
                        (map char)
                        (apply str)) r
               (str/split r #"\n"))
   :error    (.getFunctionError x)
   :version  (.getExecutedVersion x)})

(defn as-log-type [type]
  (condp = type
    :tail  (LogType/valueOf "Tail")
    nil    (LogType/valueOf "None")))

(defn as-invocation-type [type]
  (condp = type
    :request-response (InvocationType/valueOf "RequestResponse")
    :event            (InvocationType/valueOf "Event")
    :dry-run          (InvocationType/valueOf "DryRun")))

(defn invoke [type fn-name payload]
  (->> (doto (InvokeRequest.)
         (.withInvocationType (as-invocation-type type))
         (.withFunctionName fn-name)
         (.withLogType (as-log-type :tail))
         (.withPayload (json/write-str payload)))
       (.invoke @client)
       (as-result)))

(defn add-permission [fn-name rule-name rule-arn]
  (->> (doto (AddPermissionRequest.)
         (.withFunctionName fn-name)
         (.withStatementId rule-name)
         (.withPrincipal  "events.amazonaws.com")
         (.withAction "lambda:InvokeFunction")
         (.withSourceArn rule-arn))
       (.addPermission @client)))

(defn remove-permission [fn-name rule-name]
  (->> (doto (RemovePermissionRequest.)
         (.withFunctionName fn-name)
         (.withStatementId rule-name))
       (.removePermission @client)))

(defn as-function [f]
  {:handler (.getHandler f)
   :timeout (.getTimeout f)
   :memory  (.getMemorySize f)
   :name    (.getFunctionName f)
   :runtime (.getRuntime f)})

(defn list-all []
  (->> (ListFunctionsRequest.)
       (.listFunctions @client)
       (.getFunctions)
       (map as-function)))

(defn init! [region]
  (reset! client (make-client region)))
