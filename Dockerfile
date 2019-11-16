FROM clojure:openjdk-11-lein-2.9.1

WORKDIR /app

CMD lein repl :headless
