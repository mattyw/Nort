(ns link-shorten.core
  (:use [noir.core]
        [hiccup.core]
        [hiccup.page-helpers]
        [hiccup.form-helpers])
  (:require [noir.server :as server]
            [noir.response :as response]
            [redis.core :as redis])
  (:import (java.net.URI)))


(def redis-url (new URI(System/getenv "REDISTOGO_URL" "redis://localhost:6789")))


(defmacro with-redis [body]
    `(redis/with-server {:host (. getHost redis-url) :port (. getPort redis-url)}
    ~body))

(defn uuid []
    (str (java.util.UUID/randomUUID)))

(defn str-hash [s]
    (str (.hashCode s)))

(defn new-url [url]
    (let [id (str-hash url)]
    (with-redis
        (redis/hset "links" (str-hash url) url))
    id))

(defn validate [url]
    (if (.startsWith url "http://")
        url
        (format "http://%s" url)))

(defpartial redirect [url]
    [:meta {:http-equiv "Refresh" :content (format "0; url=%s" url)}])

(defpartial link-item [item]
    (let [short (nth item 0)
          url (nth item 1)]
    [:tr
        [:td [:a {:href (format "/$%s" short)} short]]
        [:td [:a {:href url} url]]]))

(defpartial shortened-link-lists [items]
    [:h3 "Shortened urls"]
    [:table {:border "1"}
    [:tr
        [:td "shortened"]
        [:td "original link"]]
        (map link-item items)]) ;;Nice use of map

(defpage "/" []
    (html
        [:head
            [:title "Nort: A url shortener"]
            (include-css "style.css")]
        [:div#Container
            [:div#Box1
            [:h1  "NORT"]
            [:p "a url shortener in Clojure using Noir"]]
            [:div#Box2
            [:h2 "Shorten me a link"]
            (form-to [:post "/new"]
                (text-field "url")
                (submit-button "Shorten!"))]
            [:div#Box3
            (shortened-link-lists 
                (with-redis
                    (redis/hgetall "links")))]]))

(defpage [:post "/new"] {:keys [url]}
    (let [validated-url (validate url)]
    (if-let [short-link (new-url validated-url)]
        (response/redirect "/"))))

(defpage "/$:id" {:keys [id]}
    (html
        (html5 
            [:head
            (with-redis
                (redirect (redis/hget "links" id)))
            [:body]])))

(defn -main [& m]
    (let [port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port)))
