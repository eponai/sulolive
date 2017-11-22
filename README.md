
# Sulolive
The live marketplace online - A product built to make shopping online more interactive by combining live streaming with small business e-commerce.

```
Please excuse the mess. We're working here 👷‍♀️
```

We've made the repository public, but we're still working on this README and the wiki.

CircleCI: [![CircleCI](https://circleci.com/gh/eponai/sulolive/tree/master.svg?style=svg&circle-token=d51f6b92f4dfb834f78b6550371a0588aa39d572)](https://circleci.com/gh/eponai/sulolive/tree/master)

## Rationale - Why open source the whole product

When learning Clojure, it was useful to look at CircleCI's open sourced front end, just to see how a real project can be structured, which problems they need to solve and how they compose libraries as well as hacks to make things work. As our startup isn't running anymore, we hope that our code can be helpful to someone. The entire frontend and server is available, as well as a skeleton for a react-native app.
Om.next alpha-1 came out a few weeks after we started building this app and we adopted it right away. Having client queries affected our whole architecture and it meant that we had to solve problems we’d never encountered before. We also embraced having the same database api on the client and server with datomic and datascript. Hopefully our project can be useful for anyone who wants to see what the code is like in such a project. When creating a startup - especially one that isn’t very successful - we’ve rushed some solutions so everything is not necessarily clean, but it also goes to show that Clojure is a pretty good environment for rushing out code without it getting too messy.
We'll highlight some of the problems we've had to solve, some that might be different because of the architecture. More than 2/3 of the code is written in .cljc, to enable us to share most of the code between web, mobile and server.


## Installation

* Clone this project ```git clone git@github.com:jourmoney/sulo.git```
* Navigate to the cloned sulo/ folder ```cd sulo```

## Usage
### Server
* Start server repl: ```lein repl```
** Initialize local server: ```eponai.repl=> (start-server)```

### Client
#### Web
* Start web client repl: ```rlwrap lein figwheel-web```
* Open Browser and navigate to: ```localhost:3000```

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

