(ns pivot-slack.slack
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
   ))

(defn slack-user-info [oauth-token user]
  (-> (client/get "https://slack.com/api/users.info" {:query-params {:token oauth-token
                                                                     :user user}})
      :body
      json/read-str
      clojure.walk/keywordize-keys
      ))

(defn slack-permalink [oauth-token channel-id message-ts]
  (-> (client/get "https://slack.com/api/chat.getPermalink" {:query-params {:token oauth-token
                                                                            :channel channel-id
                                                                            :message_ts message-ts}})
      :body
      json/read-str
      clojure.walk/keywordize-keys))
