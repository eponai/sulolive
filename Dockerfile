FROM openjdk:8-jdk

WORKDIR /app
COPY project.clj /app/project.clj 

COPY secrets.sh /app/secrets.sh

#ENV DATOMIC_EMAIL=$DATOMIC_USERNAME
#ENV DATOMIC_KEY=$DATOMIC_PASSWORD

RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod u+x lein
RUN /bin/bash -c "source secrets.sh && ./lein deps" 

RUN apt-get update
RUN apt-get -y install curl gnupg
RUN curl -sL https://deb.nodesource.com/setup_8.x  | bash -
RUN apt-get -y install nodejs

COPY package.json /app/package.json
COPY bower.json /app/bower.json
RUN npm install
RUN npm install -g bower
RUN bower install --allow-root

# COPY . .
# RUN ./lein uberjar
# RUN ./lein prod-build-web

EXPOSE 8080

COPY sulo-style /app/sulo-style
WORKDIR /app/sulo-style
RUN npm install
RUN bower install --allow-root

WORKDIR /app
COPY . .

RUN ./lein uberjar
RUN ./lein prod-build-web

#RUN sh /app/scripts/compile-css.sh

RUN cd /app/resources && jar cf assets.jar public/release/js/out/budget.js
#    public/assets/css \
#    public/assets/flags \
#    public/release/js/out/budget.js

RUN mv /app/target/uberjar/budget-*-standalone.jar /app/target/uberjar/budget-uberjar.jar

CMD exec java -Xmx300m -Xss512k -server -Dclojure.compiler.direct-linking=true -cp target/uberjar/budget-uberjar.jar:resources/assets.jar eponai.server.core

