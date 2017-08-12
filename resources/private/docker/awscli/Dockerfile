FROM ubuntu:14.04

RUN apt-get -y -qq update
RUN apt-get -y -qq install python3.4-dev
RUN apt-get -y -qq install curl
RUN apt-get -y -qq install git

RUN curl -O https://bootstrap.pypa.io/get-pip.py
RUN python3.4 get-pip.py --user
RUN ~/.local/bin/pip install awscli --upgrade --user

ENV PATH="~/.local/bin:${PATH}"

RUN apt-get -y -qq install jq
