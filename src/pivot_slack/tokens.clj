(ns pivot-slack.tokens
  (:require [environ.core :refer [env]])
  )

(def oauth-token (:slack-oauth-token env))
(def pivotal-token (:pivotal-api-token env))
