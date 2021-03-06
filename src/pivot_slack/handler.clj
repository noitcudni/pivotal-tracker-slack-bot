(ns pivot-slack.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [datahike.api :as d]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.data.json :as json]
            [ring.logger :as logger]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [pivot-slack.pivotal :as pivotal]
            [pivot-slack.slack :refer [slack-user-info slack-permalink]]
            [pivot-slack.db :refer [conn] :as conn]
            clj-http.util
            [org.httpkit.server :refer [run-server]]
            [pivot-slack.add-comment :refer [add-comment-handler dynamic-story-menu-handler]]
            [pivot-slack.tokens :refer [oauth-token pivotal-token]]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(def ^:dynamic *server* (atom nil))


(defmulti create-story-handler
  (fn [payload] (:type payload)))

(defmethod create-story-handler "message_action"
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
        elements {:title "Create a new story"
                  :callback_id callback-id
                  :submit_label "Create"
                  :state "what is this for?"
                  :elements [{:type "text"
                              :label "Story Name"
                              :name :story_name
                              :placeholder "Write a story name"}
                             {:type "select"
                              :label "Project"
                              :name :project
                              :placeholder "Select a project"
                              :options  (->> (for [x projects] {:value (get x "id")
                                                                :label (get x "name")})
                                             (into []))}
                             {:type "select"
                              :label "Owner"
                              :name :owner
                              :data_source "users"
                              :optional true}
                             {:type "textarea"
                              :label "Description"
                              :name :description
                              :value description
                              :optional true}
                             ]}
        ]
    (client/post "https://slack.com/api/dialog.open" {:content-type "application/json"
                                                      :headers {:authorization (str "Bearer " token)}
                                                      :body (json/write-str {:dialog elements
                                                                             :trigger_id trigger-id})
                                                      })
    ))


(defn slack-user->pivotal-user-id [slack-user-info pivotal-proj-id]
  (let [{{slack-id :id
          {slack-email :email
           slack-handle :display_name_normalized} :profile} :user
         :as slack-user-info} slack-user-info
        user-entity (d/entity @conn [:slack/user-id slack-id])]
    ;; do we already have a slack->pivotal mapping
    (if (nil? user-entity)
      (let [pivotal-proj-members (pivotal/project-members pivotal-token pivotal-proj-id)]
        (when-let [pivotal-id (get pivotal-proj-members slack-email)]
          ;; Found a matching email in pivotal. Create the slack to pivotal mapping automatically
          (do @(d/transact conn [{:db/id (d/tempid :db.part/user)
                                  :slack/user-id slack-id
                                  :slack/email slack-email
                                  :slack/handle slack-handle
                                  :pivotal/email slack-email
                                  :pivotal/user-id pivotal-id}])
              pivotal-id))
        )
      (:pivotal/user-id user-entity))
    ))


(defmethod create-story-handler "dialog_submission"
  [payload]
  (let [token oauth-token
        {trigger-id :trigger_id
         callback-id :callback_id
         {channel-id :id} :channel
         {pivotal-proj-id :project
          slack-uid :owner
          description :description
          story-name :story_name
          :as submission-data} :submission
         } payload

        ;; Get user's email from user id
        ;; If slack email doesn't match any of the pivotal email, give the end user the mapping option
        {{slack-id :id
          {slack-email :email
           slack-handle :display_name_normalized} :profile} :user
         :as slack-u-info} (when-not (nil? slack-uid)
                             (slack-user-info oauth-token slack-uid))
        _ (prn "slack-u-info : " slack-u-info) ;;xxx

        ;;TODO handle slack-u-info being nil
        pivotal-user-id (slack-user->pivotal-user-id slack-u-info pivotal-proj-id)
        _ (prn "pivotal-user-id: " pivotal-user-id) ;;xxx
        _ (prn "pivotal-proj-id: " pivotal-proj-id) ;;xxx


        text "Your story has been created!!"

        _ (prn "channel-id:"  channel-id)

        ;; create the story and worry about assign it later
        {pivotal-story-id :id
         pivotal-story-url :url
         :as create-story-resp} (clojure.walk/keywordize-keys (pivotal/create-story pivotal-token pivotal-proj-id
                                                                                    :name story-name
                                                                                    :description description
                                                                                    :owner-id pivotal-user-id))
        _ (prn "story-resp" create-story-resp) ;;xxx
        ]

    (if (or pivotal-user-id (not slack-uid))
      ;; found this user or didn't assign to anyone
      (client/post "https://slack.com/api/chat.postMessage" {:content-type "application/json"
                                                             :charset "utf-8"
                                                             :headers {:authorization (str "Bearer " token)}
                                                             :body (json/write-str {:trigger_id trigger-id
                                                                                    :channel channel-id
                                                                                    :text text
                                                                                    :attachments [{:text (format "Pivotal Story: %s" pivotal-story-url)}
                                                                                                  {:text (format "Your story has beeen created and assigned to %s."
                                                                                                                 (if slack-id (format "<@%s>" slack-id) "no one"))}
                                                                                                  ]})
                                                             })
      ; linkage or invite
      (client/post "https://slack.com/api/chat.postMessage" {:content-type "application/json"
                                                             :charset "utf-8"
                                                             :headers {:authorization (str "Bearer " token)}
                                                             :body (json/write-str {:trigger_id trigger-id
                                                                                    :channel channel-id
                                                                                    :text (str "Couldn't find " slack-email " in Pivotal Tracker.")
                                                                                    :attachments [{:text (format "You can either invite <@%s> to Pivotal" slack-id)
                                                                                                   ;; :fallback "fallback"
                                                                                                   :attachment_type "default"
                                                                                                   :callback_id callback-id
                                                                                                   :actions [{:name "invite"
                                                                                                              :type "button"
                                                                                                              :text (str "invite "slack-email)
                                                                                                              :value (json/write-str {:slack-id slack-id
                                                                                                                                      :slack-email slack-email
                                                                                                                                      :slack-handle slack-handle
                                                                                                                                      :story-id pivotal-story-id
                                                                                                                                      :project-id pivotal-proj-id})
                                                                                                              }]}
                                                                                                  {:text (format "Or, you can link <@%s> to an existing Pivotal user" slack-id)
                                                                                                   ;; :fallack "fallback"
                                                                                                   :callback_id callback-id
                                                                                                   :actions [{:name "linkage"
                                                                                                              :type "button"
                                                                                                              :text (str "link " slack-handle)
                                                                                                              :value (json/write-str {:slack-id slack-id
                                                                                                                                      :slack-email slack-email
                                                                                                                                      :slack-handle slack-handle
                                                                                                                                      :story-id pivotal-story-id
                                                                                                                                      :project-id pivotal-proj-id})}]
                                                                                                   }]})
                                                             })
      )
    ))

(defmethod create-story-handler "interactive_message"
  [payload]
  (let [{trigger-id :trigger_id
         callback-id :callback_id
         {channel-id :id} :channel
         [{action-name :name
           action-value :value :as action-info}] :actions
         ts :message_ts} payload

        action-value (if (nil? action-value)
                       (->> action-info
                            :selected_options
                            first
                            :value)
                       action-value)

        {:keys [slack-id slack-email slack-handle
                story-id project-id pivotal-uid pivotal-email] :as action-value} (->> action-value json/read-str clojure.walk/keywordize-keys)
        ]

    (cond (= action-name "linkage")
          (let [proj-members (pivotal/project-members pivotal-token project-id)]
            (do (prn "Executing linkage")
                (client/post "https://slack.com/api/chat.update" {:content-type "application/json"
                                                                  :charset "utf-8"
                                                                  :headers {:authorization (str "Bearer " oauth-token)}
                                                                  :body (json/write-str {:trigger_id trigger-id
                                                                                         :channel channel-id
                                                                                         :ts ts

                                                                                         :attachments [{:text (format "Link <@%s> to one of the following Pivotal user" slack-id)
                                                                                                        ;; :fallback "fallback"
                                                                                                        :attachment_type "default"
                                                                                                        :callback_id callback-id
                                                                                                        :actions [{:name "linkage-target"
                                                                                                                   :type "select"
                                                                                                                   :text "Select a Pivotal user"
                                                                                                                   :options (->> (for [[pivotal-email pivotal-uid] proj-members]
                                                                                                                                   {:text pivotal-email
                                                                                                                                    :value (-> action-value
                                                                                                                                               (merge {:pivotal-uid pivotal-uid
                                                                                                                                                       :pivotal-email pivotal-email})
                                                                                                                                               json/write-str)})
                                                                                                                                 (into []))
                                                                                                                   }]}
                                                                                                       ]})})))
          (= action-name "invite")
          ;; 1) send whoever an invite
          ;; 2) transact the linkage information
          ;; 3) Update the pivotal story with the chosen user
          ;; 4) Print out verbiage
          (do
            (let [pivotal-uid (-> (pivotal/send-invite* pivotal-token project-id :email slack-email)
                                  :body
                                  json/read-str
                                  clojure.walk/keywordize-keys
                                  (get-in [:person :id]))
                  _ (prn "created pivotal-uid: " pivotal-uid) ;;xxx
                  ]
              @(d/transact conn [{:db/id (d/tempid :db.part/user)
                                  :slack/user-id slack-id
                                  :slack/email slack-email
                                  :slack/handle slack-handle
                                  :pivotal/email slack-email ;; pivotal invite was sent to the slack email
                                  :pivotal/user-id pivotal-uid}])

              (let [pivotal-story-url (get-in (pivotal/update-story* pivotal-token project-id story-id :owner-id pivotal-uid) [:body "url"])]
                (client/post "https://slack.com/api/chat.update" {:content-type "application/json"
                                                                  :charset "utf-8"
                                                                  :headers {:authorization (str "Bearer " oauth-token)}
                                                                  :body (json/write-str {:trigger_id trigger-id
                                                                                         :channel channel-id
                                                                                         :ts ts
                                                                                         :attachments [{:text (format "Pivotal Story: %s" pivotal-story-url)}
                                                                                                       {:text (format "Your story has been created and assigned to <@%s>. An pivotal invite was sent to <@%s> at %s."
                                                                                                                      slack-id slack-id slack-email)}]
                                                                                         })}))))


          (= action-name "linkage-target")
          (do
            ;; 1) transact the linkage information
            ;; 2) update the pivotal story with the chosen user
            ;; 3) Print out verbiage
            @(d/transact conn [{:db/id (d/tempid :db.part/user)
                                :slack/user-id slack-id
                                :slack/email slack-email
                                :slack/handle slack-handle
                                :pivotal/email pivotal-email
                                :pivotal/user-id pivotal-uid}])

            (let [pivotal-story-url (get-in (pivotal/update-story* pivotal-token project-id story-id :owner-id pivotal-uid) [:body "url"])]
              (client/post "https://slack.com/api/chat.update" {:content-type "application/json"
                                                                :charset "utf-8"
                                                                :headers {:authorization (str "Bearer " oauth-token)}
                                                                :body (json/write-str {:trigger_id trigger-id
                                                                                       :channel channel-id
                                                                                       :ts ts
                                                                                       :attachments [{:text (format "Pivotal Story: " pivotal-story-url)}
                                                                                                     {:text (format "Your story has been created and assigned to <@%s>. <@%s> has been linked to %s (pivotal user)."
                                                                                                                    slack-id slack-id pivotal-email)}]
                                                                                       })})))
          )))


(defroutes app-routes
  (GET "/" []
       (prn "GET!!!")
       {:body "Hello World!"})

  (POST "/interactivity" [:as req]
        (let [{callback-id :callback_id
               :as payload} (clojure.walk/keywordize-keys (json/read-str (-> (:form-params req)
                                                                             (get "payload"))))
              _ (prn "form-params: " (:form-params req)) ;;xxx
              ]

          (case callback-id
            "create-story" (create-story-handler payload)
            "add-comment" (add-comment-handler payload)
            )

          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body ""}))


  (POST "/dynamic-options" [:as req]
        (let [_ (prn "dynamic-options: " req)
              {callback-id :callback_id
               :as payload} (clojure.walk/keywordize-keys (json/read-str (-> (:form-params req)
                                                                             (get "payload"))))
              ;; _ (prn "callback_id: " callback-id)
              ]
          ;; Note: According to https://api.slack.com/dialogs#dynamic_select_elements, it's label not text.

          (case callback-id
            "add-comment" (dynamic-story-menu-handler payload)
            )

          #_(response {:options [{:label "WTF!!"
                                :value "wtf"}]
                     ;; :selected_options [[{:text "WTF!!"
                     ;;                      :value "wtf"}]]
                     })
          #_{:status 200
           :headers {"Content-Type" "application/json"
                     }
           :body {:options [{:label "WTF!"
                             :value 1}]
                  }
           }
          )
        )

  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      logger/wrap-with-logger
      ;; (wrap-transit-response {:encoding :json :opts {}})
      wrap-json-response
      ))

(defn stop-server []
  (when-not (nil? @*server*)
    (@*server* :timeout 100)
    (reset! *server* nil)))

(defn server
  []
  (reset! *server* (run-server #'app {:port 3000})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  test ;;;;;;;;;;;;;;;;;;;;;;;
#_{:options (->> (pivotal/stories pivotal-token 166031
                       :filter-str "crawl hostel"
                       ;; :with-state "unstarted"
                       ;; :with-story-type "feature"

                       ;; :with-state ["unstarted" "unscheduled"]
                       )
      (map (fn [x]
             (let [name (get x "name")
                   story-id (get x "id")
                   ]
               {:label name
                :value story-id
                })
             ))
      (into [])
      )}


(comment
  ;; (pivotal/projects pivotal-token)

  (-> (client/get "https://www.pivotaltracker.com/services/v5/accounts"
                  {:headers {:X-TrackerToken  pivotal-token}
                   :content-type "application/json"})
      ;; :body
      ;; json/read-str
      ;; first
      ;; (get "id")
      )

  (-> (client/get "https://www.pivotaltracker.com/services/v5/accounts/3220/memberships"
                  {:headers {:X-TrackerToken pivotal-token}
                   :content-type "application/json"})
      :body
      json/read-str
      )



  (defn pivotal-project []
    (->> (client/get "https://www.pivotaltracker.com/services/v5/projects"
                     {:headers {:X-TrackerToken pivotal-token}
                      :content-type "application/json"})
         :body
         json/read-str
         (map (fn [x]
                (select-keys x ["id" "name"])
                ))))

  (let [proj (pivotal-project)
        id (get (first proj) "id")]
    (->> (client/get (str "https://www.pivotaltracker.com/services/v5/projects/" id "/memberships")
                     {:headers {:X-TrackerToken pivotal-token}
                      :content-type "application/json"})
         :body
         json/read-str
         ;; (map #(get-in % ["person" "name"]))
         ))

  ;; 1348344 swing
  (->> (client/get "https://www.pivotaltracker.com/services/v5/projects/166031/epics"
                   {:headers {:X-TrackerToken pivotal-token}
                    :content-type "application/json"})
       :body
       json/read-str
       )


  (->> (client/get "https://www.pivotaltracker.com/services/v5/projects/166031/epics"
                   {:headers {:X-TrackerToken pivotal-token}
                    :content-type "application/json"})
       :body
       json/read-str
       (map #(get % "name"))
       pivot-slack.handler      ))
