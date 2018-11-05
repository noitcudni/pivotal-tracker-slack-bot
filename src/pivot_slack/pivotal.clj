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
