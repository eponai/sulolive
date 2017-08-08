FROM openjdk:8-jdk

ADD target/uberjar/budget-uberjar.jar /srv/production.jar
ADD resources/public/assets/css/app.css /srv/public/assets/css/app.css
ADD resources/public/release/js/out/budget.js /srv/public/release/js/out/budget.js

EXPOSE 8080

WORKDIR "/srv"

RUN jar cf assets.jar \
    public/assets/css/app.css \
    public/release/js/out/budget.js

CMD exec java $JAVA_OPTS -server -Dclojure.compiler.direct-linking=true -cp production.jar:assets.jar eponai.server.core

