FROM openjdk:8-jdk

ADD target/uberjar/budget-uberjar.jar /srv/production.jar
ADD resources/public/assets/css /srv/public/assets/css
ADD resources/public/assets/flags /srv/public/assets/flags
ADD resources/public/release/js/out/budget.js /srv/public/release/js/out/budget.js

EXPOSE 8080

WORKDIR "/srv"

RUN jar cf assets.jar \
    public/assets/css \
    public/assets/flags \
    public/release/js/out/budget.js

CMD exec java $JAVA_OPTS -server -Dclojure.compiler.direct-linking=true -cp production.jar:assets.jar eponai.server.core

