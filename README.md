
# Sulolive
The live marketplace online - A product built to make shopping online more interactive by combining live streaming with small business e-commerce.

```
Please excuse the mess. We're working here 👷‍♀️
```

We've made the repository public, but we're still working on this README and the wiki.

CircleCI: [![CircleCI](https://circleci.com/gh/eponai/sulolive/tree/master.svg?style=svg&circle-token=d51f6b92f4dfb834f78b6550371a0588aa39d572)](https://circleci.com/gh/eponai/sulolive/tree/master)

## Live demo

http://sulo-demo.us-east-1.elasticbeanstalk.com

The demo includes the full web application for both visitors and logged in users. We are using an in-memory database, so you are welcome to play around and make any changes you want. 

#### A few things to consider:
- **Database**: Since the demo can be used by anyone, expect changes you make to stick around for a while. We might reset the demo sometimes, but we don't have a schedule for that.
- **Login**: In the code you'll see our implementation a passwordless login flow using Auth0. We're bypassing that flow in the demo to let anyone login and see the shop owner's UI.
- **Payments**: Stripe was integrated as the payment service. We use our own fake version of Stripe in this demo, so any functionality involving payments might not behave as expected (you'll not be charged money at any point).
- **Live streaming**: To save on costs, we had to shut down our streaming server. You'll see an animated example video in place of the streams.
- **Photo uploads**: Again to save costs, we are on a free tier with our photo storage service and have a limit on uploading photos. Expect any photo uploads to not work properly.

## Rationale - Why open source the whole product

When learning Clojure, it was useful to look at CircleCI's open sourced frontend, just to see how a real project can be structured, which problems they need to solve and how they compose libraries as well as hacks to make things work. As our startup isn't running anymore, we hope that our code can be helpful to someone - just as CircleCI's code was helpful to us. The entire frontend and server is available as well as a skeleton for a react-native app. We've even included all the scripts we used to deploy the product, so you can even take a look at them.

**What more should we put in the rationale?**

Om.next alpha-1 came out a few weeks after we started building this app and we adopted it right away. Having client queries affected our whole architecture and it meant that we had to solve problems we’d never encountered before. We also embraced having the same database api on the client and server with datomic and datascript. Hopefully our project can be useful for anyone who wants to see what the code is like in such a project. When creating a startup - especially one that isn’t very successful - we’ve rushed some solutions so everything is not necessarily clean, but it also goes to show that Clojure is a pretty good environment for rushing out code without it getting too messy.

We'll highlight some of the problems we've had to solve, some that might be different because of the architecture. More than 2/3 of the code is written in .cljc, to enable us to share most of the code between web, mobile and server.

## Installation

* Clone this project ```git clone git@github.com:eponai/sulo.git```
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


