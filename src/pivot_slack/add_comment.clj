(ns pivot-slack.add-comment
  (:require [pivot-slack.tokens :refer [oauth-token pivotal-token]]
            [pivot-slack.slack :refer [slack-permalink]]
            [pivot-slack.pivotal :as pivotal]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [ring.util.response :refer [response]]
            ))




(defn truncate* [cap s]
  (let [words (clojure.string/split s #"\s+")
        word-cnt-lst (->> words (map count))
        take-cnt (count (reduce (fn [r  curr]
                                  (if (> (+ (- (count r) 1) (apply + r)) cap)
                                    (reduced r)
                                    (conj r curr)
                                    )
                                  )
                                []
                                word-cnt-lst
                                ))
        truncated-s (->> (-> take-cnt
                             (take words))
                         (clojure.string/join " "))
        ]
    (if (< take-cnt (count word-cnt-lst))
      (str truncated-s "...")
      truncated-s
      )))

(def truncate
  (partial truncate* 40))

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
                  :state "What is this for?"
                  :elements [{:type "select"
                              :label "Project"
                              :name :project
                              :placeholder "Select a project"
                              :options  (->> (for [x projects] {:value (get x "id")
                                                                :label (get x "name")})
                                             (into []))}
                             #_{:type "select"
                              :label "Story type"
                              :name :story-type
                              :optional true
                              :options [{:label "feature" :value :feature}
                                        {:label "bug" :value :bug}
                                        {:label "chore" :value :chore}
                                        {:label "release" :value :release}]
                              }
                             #_{:type "select"
                              :label "State"
                              :name :state
                              :optional true
                              :options [{:label "accepted" :value :accepted}
                                        {:label "delivered" :value :accepted}
                                        {:label "finished" :value :accepted}
                                        {:label "started" :value :accepted}
                                        {:label "rejected" :value :accepted}
                                        {:label "planned" :value :accepted}
                                        {:label "unstarted" :value :accepted}
                                        {:label "unscheduled" :value :accepted}]
                              }
                             {:type "select"
                              :label "Story"
                              :name :story
                              :data_source "external"
                              :min_query_length 3}
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


(defn dynamic-story-menu-handler [payload]
  (let [_ (prn "payload: " payload) ;; xxx
        {value :value} payload]
    ;; TODO: get rid of project id hardcode
    (response {:options (->> (pivotal/stories pivotal-token 166031 :filter-str value)
                             (map (fn [x]
                                    (let [name (get x "name")
                                          story-id (get x "id")]
                                      {:label (truncate name)
                                       :value story-id}
                                      )))
                             (into [])
                             )})
    )

  )




;;;;;;;;;
;;; scatch buffer
;;;;;;;;;;;;;;;;;;;;
;; (truncate "Run the crawling script on barcelona from hostelbookers ")
