(defproject trivial-openai "0.0.0"
  :description "A set of trivial text, image and audio bindings for the OpenAI API"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]

                 [http-kit "2.6.0"]
                 [cheshire "5.11.0"]]
  :repl-options {:init-ns trivial-openai.core})
