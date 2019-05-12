(ns orb.event-pattern)

(defn s3-bucket-event [{:keys [bucket-name]}]
  {:source       ["aws.s3"]
   :detailType ["AWS API Call via CloudTrail"]
   :detail     {:eventSource ["s3.amazonaws.com"]
                :eventName   ["PutObject"]
                :requestParameters
                {"bucketName" [bucket-name]}}})

(def events
  {:s3-bucket-change #'s3-bucket-event})

(defn make [{:keys [event-id] :as param}]
  (when-let [f (get events event-id)]
    (f param)))
