FROM openjdk:8u102-jdk

ADD target/uberjar/budget-0.1.0-SNAPSHOT-standalone.jar /srv/production.jar

EXPOSE 8080

WORKDIR "/srv"
CMD ["java", "-server", "-Xmx700m", "-Dclojure.compiler.direct-linking=true", "-cp", "production.jar", "eponai.server.core"]

