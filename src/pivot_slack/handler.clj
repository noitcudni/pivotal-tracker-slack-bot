(ns pivot-slack.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.logger :as logger]
            [clj-http.client :as client]
            clj-http.util
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

;; (defonce server (atom nil))
(def ^:dynamic *server* (atom nil))

(def oauth-token "xoxp-261542976081-262065086930-455830832484-2fab0362cf27bf20ef258b081f4e94a2")

(defmulti create-story-handler
  (fn [payload] (get payload "type")))

(defmethod create-story-handler "message_action"
  [payload]
  (let [trigger-id (get payload "trigger_id")
        callback-id (get payload "callback_id")
        token oauth-token

        elements {:title "Create a new story"
                  :callback_id callback-id
                  :submit_label "Submit"
                  :state "what is this for?"
                  :elements [{:type "text"
                              :label "Story name"
                              :name :story_name
                              }]}
        ]

    (prn "message_action") ;;xxx

    (client/post "https://slack.com/api/dialog.open" {:content-type "application/json"
                                                      :headers {:authorization (str "Bearer " token)}
                                                      :body (json/write-str {:dialog elements
                                                                             :trigger_id trigger-id})
                                                      })
    ))

(defmethod create-story-handler "dialog_submission"
  [payload]
  (let [trigger-id (get payload "trigger_id")
        callback-id (get payload "callback_id")
        channel-id (get-in payload ["channel" "id"])
        token oauth-token
        text "Your story has been created!!"
        _ (prn "dialog-submission") ;;xxx
        _ (prn "channel-id:"  channel-id)
        ]

    (prn (client/post "https://slack.com/api/chat.postMessage" {:content-type "application/json"
                                                                :charset "utf-8"
                                                                :headers {:authorization (str "Bearer " token)}
                                                                :body (json/write-str {:trigger_id trigger-id
                                                                                       :channel channel-id
                                                                                       :text text
                                                                                       })
                                                                }))
    ))

(defroutes app-routes
  (GET "/" []
       (prn "GET!!!")
       {:body "Hello World!"})

  (POST "/interactivity" [:as req]
        (let [payload (json/read-str (-> (:form-params req)
                                         (get "payload")))]
          (create-story-handler payload))

        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body ""})


  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      logger/wrap-with-logger
      (wrap-transit-response {:encoding :json :opts {}})
      ))

(defn stop-server []
  (when-not (nil? @*server*)
    (@*server* :timeout 100)
    (reset! *server* nil)))

(defn server
  []
  (reset! *server* (run-server #'app {:port 3000})))

#_{"message" {"type" "message", "user" "U7Q1X2JTC", "text" "I have slack on my new phone", "client_msg_id" "a163d8e5-1063-4747-ab0d-8777bfe6517d", "ts" "1540622932.000400"}, "token" "qE7oUaW1ATt44SXNRSKs0SUL", "trigger_id" "465366214769.261542976081.452e3e3ccf079523afa2c773d0480cc3", "message_ts" "1540622932.000400", "user" {"id" "U7Q1X2JTC", "name" "lihster"}, "action_ts" "1540666772.152771", "callback_id" "create-story", "type" "message_action", "response_url" "https://hooks.slack.com/app/T7PFYUQ2D/465874252451/t0YLm339UvFy1HNwqr9Y1Rie", "channel" {"id" "D7Q60RFNH", "name" "directmessage"}, "team" {"id" "T7PFYUQ2D", "domain" "jennyandlih"}}

#_{"token" "qE7oUaW1ATt44SXNRSKs0SUL", "user" {"id" "U7Q1X2JTC", "name" "lihster"}, "action_ts" "1540666744.343461", "callback_id" "create-story", "submission" {"story_name" "wtf"}, "type" "dialog_submission", "state" "what is this for?", "response_url" "https://hooks.slack.com/app/T7PFYUQ2D/467372446214/IQfXJoLJnKncFY5OOhDfqpTx", "channel" {"id" "D7Q60RFNH", "name" "directmessage"}, "team" {"id" "T7PFYUQ2D", "domain" "jennyandlih"}}

;; test
#_(let [d (json/read-str "{\"type\":\"message_action\",\"token\":\"qE7oUaW1ATt44SXNRSKs0SUL\",\"action_ts\":\"1539575724.397035\",\"team\":{\"id\":\"T7PFYUQ2D\",\"domain\":\"jennyandlih\"},\"user\":{\"id\":\"U7Q1X2JTC\",\"name\":\"lihster\"},\"channel\":{\"id\":\"D7Q60RFNH\",\"name\":\"directmessage\"},\"callback_id\":\"create-story\",\"trigger_id\":\"457425722838.261542976081.fd9dbe2ee44236ccf5313c5e8c0e5920\",\"message_ts\":\"1539268005.000100\",\"message\":{\"type\":\"message\",\"user\":\"U7Q1X2JTC\",\"text\":\"is there a way to send a delayed slack message?\",\"client_msg_id\":\"d4275b5f-e9e8-4575-8e7b-eb9394ae01c0\",\"ts\":\"1539268005.000100\"},\"response_url\":\"https:\\/\\/hooks.slack.com\\/app\\/T7PFYUQ2D\\/456054591058\\/ScwCRx4kQLK1eqKk8Et1Bun3\"}")]
  (get-in d ["token"])
  )


;;test
;; {"payload" "{\"type\":\"message_action\",\"token\":\"qE7oUaW1ATt44SXNRSKs0SUL\",\"action_ts\":\"1539575462.650914\",\"team\":{\"id\":\"T7PFYUQ2D\",\"domain\":\"jennyandlih\"},\"user\":{\"id\":\"U7Q1X2JTC\",\"name\":\"lihster\"},\"channel\":{\"id\":\"D7Q60RFNH\",\"name\":\"directmessage\"},\"callback_id\":\"create-story\",\"trigger_id\":\"457424529414.261542976081.0bb445bcc204c02a45fe46f37e35cc50\",\"message_ts\":\"1539268005.000100\",\"message\":{\"type\":\"message\",\"user\":\"U7Q1X2JTC\",\"text\":\"is there a way to send a delayed slack message?\",\"client_msg_id\":\"d4275b5f-e9e8-4575-8e7b-eb9394ae01c0\",\"ts\":\"1539268005.000100\"},\"response_url\":\"https:\\/\\/hooks.slack.com\\/app\\/T7PFYUQ2D\\/455880351924\\/DDw6uDLKsl7QDeUYuTSTdwzU\"}"}
