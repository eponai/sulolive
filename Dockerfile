FROM openjdk:8-jdk

ADD target/uberjar/budget-uberjar.jar /srv/production.jar

EXPOSE 8080

WORKDIR "/srv"

CMD exec java $JAVA_OPTS -server -Dclojure.compiler.direct-linking=true -cp production.jar eponai.server.core

