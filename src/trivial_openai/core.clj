(ns trivial-openai.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io]

            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [cheshire.core :as json]))

;;;;; Config and basics
;; Change default client for your whole application:
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def API_KEY (System/getenv "OPENAI_API_KEY"))

(defn -api-openai [endpoint & {:keys [body multipart version method] :or {version "v1" method :get}}]
  (assert (or (and body (not multipart))
              (and (not body) multipart)
              (and (not body) (not multipart))))
  (let [url (str "https://api.openai.com/" version "/" endpoint)
        method (if body :post method)
        auth {"Authorization" (str "Bearer " API_KEY)}
        content-type (cond body {"Content-Type" "application/json"}
                           multipart {"Content-Type" "multipart/form-data"}
                           :else {})
        callback (fn [{:keys [status headers body error]}]
                   (if error
                     (println "FAILED: " error)
                     body))
        min-opts {:url url :method method :headers (merge auth content-type)}
        opts (cond body (merge min-opts {:body (json/encode body)})
                   multipart (merge min-opts {:multipart multipart})
                   :else min-opts)]
    @(http/request opts callback)))

;;;;;;;;;; Models
(defn models []
  (-> (-api-openai "models")
      json/decode
      (get "data")))

;;;;;;;;;; Text-related API
(defn -filter-nil [m]
  (into {} (keep (fn [e] (if (val e) e)) m)))

(defn completion [prompt & {:keys [model max-tokens temperature count echo stop presence-penalty frequency-penalty logprobs best-of]
                            :or {model "text-davinci-003" max-tokens 2048 temperature 1.0 count 1 echo false
                                 presence-penalty 0 frequency-penalty 0 best-of 1}}]
  ;; TODO - stream logit_bias user
  (assert (>= 2048 max-tokens))
  (assert (>= 2.0 temperature 0.0))
  (assert (or (nil? stop) (string? stop) (every? string? stop)))
  (assert (>= 2.0 presence-penalty -2.0))
  (assert (>= 2.0 frequency-penalty -2.0))
  (assert (or (nil? logprobs) (>= 5 logprobs 1)))
  (assert (or (>= best-of count)))
  (json/decode
    (-api-openai
     "completions"
     :body (-filter-nil
            {:prompt prompt
             :model model
             :n count
             :max_tokens max-tokens
             :echo (boolean echo)
             :temperature temperature
             :stop stop
             :logprobs logprobs
             :presence_penalty presence-penalty
             :frequency_penalty frequency-penalty
             :best_of best-of}))))

(defn chat [messages & {:keys [model temperature count max-tokens stop presence-penalty frequency-penalty]
                        :or {model "gpt-3.5-turbo" temperature 1.0 count 1
                             presence-penalty 0 frequency-penalty 0}}]
  ;; TODO - stream logit_bias user
  (assert
   (every?
    (fn [msg]
      (and (select-keys msg [:role :content :name])
           (#{:system :user :assistant} (:role msg))
           (string? (:content msg))))
    messages))
  (assert (>= 2.0 temperature 0.0))
  (assert (or (nil? max-tokens) (>= max-tokens 1)))
  (assert (or (nil? stop) (string? stop) (every? string? stop)))
  (assert (>= 2.0 presence-penalty -2.0))
  (assert (>= 2.0 frequency-penalty -2.0))
  (json/decode
   (-api-openai
    "chat/completions"
    :body (-filter-nil
           {:model model
            :n count :temperature temperature
            :max_tokens max-tokens
            :stop stop
            :presence_penalty presence-penalty
            :frequency_penalty frequency-penalty
            :messages messages}))))

;;;;; File-related section
;;;;;;;;;; Utility
(defn map->multipart
  "General file request utility. Takes a map and returns a multi-part set of parameters suitable for feeding into the API"
  [m]
  (->> m
       (map
        (fn [[k v]]
          (let [res {:name (if (keyword? k) (name k) (str k))
                     :content (cond (keyword? v) (name v)
                                    (= java.io.File (class v)) v
                                    :else (str v))}]
            (if (= java.io.File (class v))
              (assoc res :filename (.getName v))
              res))))
       (into [])))

;;;;;;;;;; Audio API
(defn -api-audio [endpoint filename {:keys [prompt model response-format temperature language] :or {prompt "" model "whisper-1" response-format :json temperature 1.0 language "en"}}]
  (let [file (io/file filename)]
    (assert (.exists file))
    (assert (>= 1.0 temperature 0.0))
    (assert (#{:json :text :srt :verbose_json :vtt} response-format))
    (let [res (-api-openai
               (str "audio/" endpoint)
               :method :post
               :multipart (map->multipart
                           {:language language
                            :temperature temperature
                            :model model
                            :prompt prompt
                            :response_format response-format
                            :file file}))]
      (if (#{:json :verbose_json} response-format)
        (json/decode res)
        res))))

(defn transcription [filename & {:keys [prompt response-format temperature language] :as opts}]
  (-api-audio "transcriptions" filename opts))

(defn translation [filename & {:keys [prompt response-format temperature] :as opts}]
  (-api-audio "translations" filename opts))

;;;;;;;;;; Images API
(defn -image-resp [format response]
  (let [res-slot (name format)
        decoded (json/decode response)
        eget (fn [k] (get-in decoded ["error" k]))]
    (if (contains? decoded "error")
      (cond (str/starts-with? (eget "message") "Your request was rejected as a result of our safety system")
            {:state :error
             :type :safety-error}

            (= "server_error" (eget "type"))
            {:state :error :type :server-error}

            :else
            {:state :error
             :type (keyword (eget "type"))
             :code (eget "code")
             :param (eget "param")
             :message (eget "message")})
      (map #(get % res-slot) (get decoded "data")))))

(defn image-url->file [url path]
  @(http/get
    url {:as :byte-array}
    (fn [{:keys [status headers body error opts]}]
      (if (= 200 status)
        (with-open [w (java.io.BufferedOutputStream. (java.io.FileOutputStream. path))]
          (.write w body))
        error))))

(defn image [prompt & {:keys [count size response-format] :or {count 1 size 1024 response-format :url}}]
  (assert (>= 10 count 1))
  (assert (#{256 512 1024} size))
  (assert (#{:url :b64_json} response-format))
  (-image-resp
   response-format
   (-api-openai
    "images/generations"
    :body {:prompt prompt
           :n count
           :size (str size "x" size)
           :response_format response-format})))

(defn image-edit [filename prompt & {:keys [mask count size response-format] :or {size 1024 response-format :url count 1} :as opts}]
  (assert (#{:url :b64_json} response-format))
  (assert (#{256 512 1024} size))
  (assert (>= 10 count 1))
  (let [file (io/file filename)
        mfile (io/file (io/resource (or mask (str "mask-" size ".png"))))]
    (assert (.exists file))
    (assert (.exists mfile))
    (let [props {:n count :size (str size "x" size)
                 :response_format response-format
                 :image file :mask mfile}
          multipart (map->multipart (if prompt (assoc props :prompt prompt) props))]
      (-image-resp
       response-format
       (-api-openai "images/edits"
        :method :post :multipart multipart)))))

(defn image-variations [filename & {:keys [count size response-format] :or {size 1024 response-format :url count 1} :as opts}]
  (assert (#{:url :b64_json} response-format))
  (assert (#{256 512 1024} size))
  (assert (>= 10 count 1))
  (let [file (io/file filename)]
    (assert (.exists file))
    (let [props {:n count :size (str size "x" size) :response_format response-format :image file}
          multipart (map->multipart props)]
      (-image-resp response-format (-api-openai "images/variations" :method :post :multipart multipart)))))
