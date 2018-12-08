(ns pivot-slack.add-comment
  (:require [pivot-slack.tokens :refer [oauth-token pivotal-token]]
            [pivot-slack.slack :refer [slack-permalink]]
            [pivot-slack.pivotal :as pivotal]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            ))

(defmulti add-comment-handler
  (fn [payload] (:type payload)))

(defmethod add-comment-handler "message_action"
  [payload]
  (let [{trigger-id :trigger_id
         callback-id :callback_id
         {channel-id :id} :channel
         {text :text ts :ts} :message} payload
        token oauth-token

        ;; retrieve the permalink
        permalink (->> (slack-permalink oauth-token channel-id ts) :permalink)
        description (str text "\n\n" "Created from Slack:\n" permalink)

        ;; fetch project(s)
        projects (pivotal/projects pivotal-token)
        elements {:title "Add as story comment"
                  :callback_id callback-id
                  :submit_label "Comment"
                  :state "What is thie for?"
                  :elements [{:type "select"
                              :label "Project"
                              :name :project
                              :placeholder "Select a project"
                              :options  (->> (for [x projects] {:value (get x "id")
                                                                :label (get x "name")})
                                             (into []))}
                             {:type "select"
                              :label "Story"
                              :name :story
                              :data_source "external"}

                             ]
                  }
        ]

    (client/post "https://slack.com/api/dialog.open" {:content-type "application/json"
                                                      :headers {:authorization (str "Bearer " token)}
                                                      :body (json/write-str {:dialog elements
                                                                             :trigger_id trigger-id})
                                                      })
    ))

(defmethod add-comment-handler "dialog_submission"
  [payload]
  )

(defmethod add-comment-handler "create-story-handler"
  [payload]
  )
