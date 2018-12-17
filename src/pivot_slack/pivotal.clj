(ns pivot-slack.pivotal
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
   ))

(def api-prefix  "https://www.pivotaltracker.com/services/v5")

(defn projects* [token]
  (->> (client/get (str api-prefix "/projects")
                   {:headers {:X-TrackerToken token}
                    :content-type "application/json"})
       :body
       json/read-str
       ))

(defn projects [token]
  (->> (projects* token)
       (map #(select-keys % ["id" "name"]))
       ))


(defn project-members* [token project-id]
  (->> (client/get (str api-prefix "/projects/" project-id "/memberships")
                   {:headers {:X-TrackerToken token}
                    :content-type "application/json"})
       :body
       json/read-str
       ))

(defn project-members
  "Returns a map of {pivotal-email pivotal-id}"
  [token project-id]
  (->> (project-members* token project-id)
       (map (fn [x]
              [(get-in x ["person" "email"])
               (get-in x ["person" "id"])]))
       (into {})))

(defn project-epics* [token project-id]
  (->> (client/get (str api-prefix "/projects/" project-id "/epics"))
       :body
       json/read-str
       ))

(defn stories
  "
  with-story-type: Valid enumeration values: feature, bug, chore, release.
  with_state: Valid enumeration values: accepted, delivered, finished, started,
              rejected, planned, unstarted, unscheduled
  Note: pivotal's api can't support both filter-str with another parameter
  "
  [token project-id & {:keys [with-label with-story-type with-state filter-str]}]
  (->> (client/get (str api-prefix "/projects/" project-id "/stories")
                   {:headers {:X-TrackerToken token}
                    :content-type "application/json"
                    :query-params (->> {:with_label with-label
                                        :with_story_type with-story-type
                                        :filter filter-str
                                        :with_state with-state}
                                       (remove (fn [[_ v]] (nil? v)))
                                       (into {}))
                    })
       :body
       json/read-str
       ))

(defn create-story [token project-id & {:keys [name description story-type owner-id]}]
  (->> (client/post (str api-prefix "/projects/" project-id "/stories")
                    {:headers {:X-TrackerToken token}
                     :content-type "application/json"
                     :body (-> (merge {:name name
                                       :description description}
                                      (when story-type
                                        {:with_story_type story-type})
                                      (when owner-id
                                        {:owner_ids [owner-id]}))
                               json/write-str)
                     })
       :body
       json/read-str))

(defn update-story* [token project-id story-id & {:keys [owner-id]}]
  (let [resp (client/put (str api-prefix "/projects/" project-id "/stories/" story-id)
                         {:headers {:X-TrackerToken token}
                          :content-type "application/json"
                          :body (-> {:owner_ids [owner-id]}
                                    json/write-str)})]
    (-> resp
        (assoc :body (-> resp
                         :body
                         json/read-str)))
    ))

(defn send-invite* [token project-id & {:keys [email]}]
  (client/post  (str api-prefix "/projects/" project-id "/memberships")
                {:headers {:X-TrackerToken token}
                 :content-type "application/json"
                 :body (-> {:role :member
                            :email email}
                           json/write-str)
                 }))

(defn upload* [token project-id file-path]
  (client/post (str api-prefix "/projects/" project-id "/uploads")
               {:headers {:X-TrackerToken token}
                :multipart [{:name "file" :content (clojure.java.io/file file-path)}]
                }))

(defn upload [token project-id file-path]
  (-> (upload* token project-id file-path)
      :body
      json/read-str))

(defn add-comment [token project-id story-id text]
  ;; TODO: what about attachments
  (client/post (str api-prefix "/projects/" project-id "/stories/" story-id "/comments")
               {:headers {:X-TrackerToken token}
                :content-type "application/json"
                :body (-> {:text text} json/write-str)
                }))

;; (upload (:pivotal-api-token env) 166031 "/Users/lih/Desktop/transactor-disconnect-error.png")
