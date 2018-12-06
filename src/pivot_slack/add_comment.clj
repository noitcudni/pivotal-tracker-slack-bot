(ns pivot-slack.add-comment)

(defmulti add-comment-handler
  (fn [payload] (:type payload)))

(defmethod add-comment-handler "message_action"
  [payload]
  (prn "TODO: nothing here yet")
  )

(defmethod add-comment-handler "dialog_submission"
  [payload]
  )

(defmethod add-comment-handler "create-story-handler"
  [payload]
  )
