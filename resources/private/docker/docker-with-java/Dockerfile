FROM openjdk:8-jdk

RUN apt-get -y -qq update
RUN apt-get -y -qq install curl 
RUN apt-get -y -qq install tar

RUN curl -L -o /tmp/docker.tgz https://get.docker.com/builds/Linux/x86_64/docker-17.05.0-ce.tgz
RUN tar -xz -C /tmp -f /tmp/docker.tgz
RUN mkdir -p /usr/bin
RUN mv /tmp/docker/* /usr/bin

ENV PATH="/usr/bin:${PATH}"
