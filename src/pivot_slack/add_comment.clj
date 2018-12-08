(ns pivot-slack.add-comment
  (:require [pivot-slack.tokens :refer [oauth-token pivotal-token]]
            [pivot-slack.slack :refer [slack-permalink]]
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
        ]



    ))

(defmethod add-comment-handler "dialog_submission"
  [payload]
  )

(defmethod add-comment-handler "create-story-handler"
  [payload]
  )
