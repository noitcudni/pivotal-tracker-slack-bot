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
                              :options  (->> (for [x projects] {:value (pr-str {:id (get x "id") :name (get x "name")})
                                                                :label (get x "name")})
                                             (into []))}
                             {:type "textarea"
                              :label "Description"
                              :name :description
                              :value description
                              :optional true}

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
                             #_{:type "select"
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
  (let [token oauth-token
        {trigger-id :trigger_id
         callback-id :callback_id
         {channel-id :id} :channel
         {pivotal-proj-edn-str :project
          description :description
          :as submission-data} :submission} payload
        ]
    (client/post "https://slack.com/api/chat.postMessage" {:content-type "application/json"
                                                           :charset "utf-8"
                                                           :headers {:authorization (str "Bearer " token)}
                                                           :body (json/write-str {:trigger_id trigger-id
                                                                                  :channel channel-id
                                                                                  :text "About to add the comment below"
                                                                                  :attachments [{:text description}
                                                                                                {:text "Select a story"
                                                                                                 :attachment_type "default"
                                                                                                 :callback_id callback-id
                                                                                                 :actions [{:name pivotal-proj-edn-str
                                                                                                            ;; :name "selected-story"
                                                                                                            :type "select"
                                                                                                            :text "Start typing"
                                                                                                            :extra-data "extra data"
                                                                                                            :data_source "external"
                                                                                                            :min_query_length 3
                                                                                                            }]}
                                                                                                ]

                                                                                  })
                                                           })
    ))

(defmethod add-comment-handler "interactive_message"
  [payload]
  ;; TODO: actual create the comment
  (prn ">> add-comment-handler interactive_message: " payload)
  (let [{trigger-id :trigger_id
         callback-id :callback_id
         {channel-id :id} :channel
         [{project-edn-str :name
           [{story-edn-str :value}]:selected_options
           ;; action-value :value
           }] :actions
         ts :message_ts
         original-msg :original_message} payload
        {project-id :id
         project-name :name} (read-string project-edn-str)
        {:keys [story-id story-name]} (read-string story-edn-str)

        description (->> original-msg
                         :attachments
                         (filter #(= 1 (:id %)))
                         first
                         :text)]

    (pivotal/add-comment pivotal-token project-id story-id description)

    (client/post "https://slack.com/api/chat.update" {:content-type  "application/json"
                                                      :charset "utf-8"
                                                      :headers {:authorization (str "Bearer " oauth-token)}
                                                      :body (json/write-str {:trigger_id trigger-id
                                                                             :channel channel-id
                                                                             :ts ts
                                                                             :attachments [{:text
                                                                                            (format "Added comment to Project: %s - Story: %s"
                                                                                                    project-name story-name)
                                                                                            }]
                                                                             })
                                                      })

    ))

(defn dynamic-story-menu-handler [payload]
  (let [{pivotal-proj-edn-str :name  ;; shamelessly hijacked the name field to pass along data
         value :value} payload
        _ (prn "dynamic-story-menu-handler payload: " payload)
        {project-id :id
         project-name :name} (read-string pivotal-proj-edn-str)
        ]
   {:status 200
    :headers {"Content-Type" "application/json"}
    :body {:options
           ;; [{:text "foo" :value "foo"}
           ;;  {:text "bar" :value "bar"}
           ;;  {:text "baz" :value "baz"}
           ;;  ]
           (->> (pivotal/stories pivotal-token project-id :filter-str value)
                (map (fn [x]
                       (let [name (get x "name")
                             story-id (get x "id")]
                         {:text (truncate name)
                          :value (pr-str {:story-name name :story-id story-id})}
                         )))
                (into [])
                )}
    }))


;;;;;;;;;
;;; scratch buffer
;;;;;;;;;;;;;;;;;;;;
