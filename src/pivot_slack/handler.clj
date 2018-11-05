(ns pivot-slack.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [datahike.api :as d]
            [clojure.data.json :as json]
            [ring.logger :as logger]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [pivot-slack.pivotal :as pivotal]
            [pivot-slack.db :refer [conn] :as conn]
            clj-http.util
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(def ^:dynamic *server* (atom nil))

(def oauth-token (:slack-oauth-token env))
(def pivotal-token (:pivotal-api-token env))

(defmulti create-story-handler
  (fn [payload] (get payload "type")))


(defn slack-user-info [oauth-token user]
  (-> (client/get "https://slack.com/api/users.info" {:query-params {:token oauth-token
                                                                     :user user}})
      :body
      json/read-str
      clojure.walk/keywordize-keys
      ))



(defmethod create-story-handler "message_action"
  [payload]
  (let [trigger-id (get payload "trigger_id")
        callback-id (get payload "callback_id")
        token oauth-token
        ;; fetch project(s)
        projects (pivotal/projects pivotal-token)
        elements {:title "Create a new story"
                  :callback_id callback-id
                  :submit_label "Submit"
                  :state "what is this for?"
                  :elements [{:type "text"
                              :label "Story Name"
                              :name :story_name
                              :placeholder "Write a story name"}
                             ;; TODO project should be a dynamic select
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
                              :optional true}
                             ]}
        ]
    (prn "message_action") ;;xxx

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
                                  :pivotal/id pivotal-id}])
              pivotal-id))
        )
      (:pivotal/user-id user-entity))
    ))

(defn slack-user<=>pivotal-user []
  )

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
         } (clojure.walk/keywordize-keys payload)

        ;; TODO: get user's email from user id
        ;; If slack email doesn't match any of the pivotal email, give the end user the mapping option
        slack-u-info (slack-user-info oauth-token slack-uid)
        _ (prn "slack-u-info : " slack-u-info) ;;xxx

        pivotal-user-id (slack-user->pivotal-user-id slack-u-info pivotal-proj-id)
        _ (prn "pivotal-user-id: " pivotal-user-id) ;;xxx


        ;; pivotal-proj-members (pivotal/project-members pivotal-token pivotal-proj-id)
        ;; _ (prn "pivotal-proj-members: " pivotal-proj-members) ;;xxx


        text "Your story has been created!!"

        _ (prn "channel-id:"  channel-id)
        ;; _ (prn "submission data: " (get payload "submission"))
        ]

    (prn (client/post "https://slack.com/api/chat.postMessage" {:content-type "application/json"
                                                                :charset "utf-8"
                                                                :headers {:authorization (str "Bearer " token)}
                                                                :body (json/write-str {:trigger_id trigger-id
                                                                                       :channel channel-id
                                                                                       :text text
                                                                                       :attachments [{:text "Choose a game to play"
                                                                                                      :fallback "fallback"
                                                                                                      :attachment_type "default"
                                                                                                      :callback_id callback-id
                                                                                                      :actions [{:name "action_name"
                                                                                                                 :text "action_text"
                                                                                                                 :type "select"
                                                                                                                 :options [{:text "Hearts"
                                                                                                                            :value "hearts"}
                                                                                                                           {:text "Chess"
                                                                                                                            :value "Chess"}]}
                                                                                                                ]}
                                                                                                     {:text "Choose a game to play2"
                                                                                                      :fallback "fallback"
                                                                                                      :attachment_type "default"
                                                                                                      :callback_id callback-id
                                                                                                      :actions [{:name "action_name"
                                                                                                                 :text "action_text"
                                                                                                                 :type "select"
                                                                                                                 :options [{:text "Hearts"
                                                                                                                            :value "hearts"}
                                                                                                                           {:text "Chess"
                                                                                                                            :value "Chess"}]}
                                                                                                                ]}
                                                                                                     ]
                                                                                       })
                                                                }))
    ))

(defmulti dynamic-option-handler
  (fn [payload] (get payload "callback_id")))



(defmethod dynamic-option-handler "create-story"
  [payload]
  ;; generate a list of users

  )

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

  (POST "/dynamic-options" [:as req]
        (let [_ (prn "dynamic-options: " req)]
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body ""})
        )

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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  test ;;;;;;;;;;;;;;;;;;;;;;;
;; get account id
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
       ))
