FROM openjdk:8-jdk

ADD target/uberjar/budget-uberjar.jar /srv/production.jar
RUN cd resources && jar cf /srv/assets.jar \
    public/assets/css/app.css \
    public/release/js/out/budget.js

EXPOSE 8080

WORKDIR "/srv"

CMD exec java $JAVA_OPTS -server -Dclojure.compiler.direct-linking=true -cp production.jar:assets.jar eponai.server.core

