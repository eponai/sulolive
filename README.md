
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

## Project buzzwords
* Fullstack om.next
  * Web, Mobile and Server
* Datomic and Datascript with a unified API
* Server side rendering
* Fullstack testing
* Client and Server code reloading on code change

## Lessons learned

We've described our experience working with an om.next, datascript and datomic architecture in the github wiki. The pages contain ideas, problems and solutions that we've had to figure out, and we've included links and code example where it made sense to us.

We'll start with the more general stuff that might help you in your next project:

* [Handling remote responses](https://github.com/eponai/sulolive/wiki/Remote-responses)
* [Restricting client queries](https://github.com/eponai/sulolive/wiki/Restricting-client-queries)
* [Composable datalog queries](https://github.com/eponai/sulolive/wiki/Composable-datalog-queries)
* [Single point of server calls & git rebase app state](https://github.com/eponai/sulolive/wiki/Single-point-of-server-calls-&-git-rebase-app-state)
* [Datomic queries returning as little as possible](https://github.com/eponai/sulolive/wiki/Datomic-queries---returning-as-little-as-possible)
* [Component & om.next shared](https://github.com/eponai/sulolive/wiki/Component-&-om.next-shared)
* [Full stack testing](https://github.com/eponai/sulolive/wiki/Full-stack-testing)

### Om.next tricks
* [Caching om.next ui props](https://github.com/eponai/sulolive/wiki/Caching-om.next-ui--props)
* [Parser middleware](https://github.com/eponai/sulolive/wiki/Parser-middleware)
* [om.next dedpue parser](https://github.com/eponai/sulolive/wiki/om.next-dedpue-parser)

### Random stuff
* [Datascript string search](https://github.com/eponai/sulolive/wiki/Datascript-string-search)
* [Datomic database functions used from normal code](https://github.com/eponai/sulolive/wiki/Datomic-database-functions-used-from-normal-code)
* [Pagination](https://github.com/eponai/sulolive/wiki/Pagination)
* [SEO](https://github.com/eponai/sulolive/wiki/SEO)
* [Optimizing Server side rendering](https://github.com/eponai/sulolive/wiki/Optimizing-Server-side-rendering)

## Installation

* Clone this project ```git clone git@github.com:eponai/sulolive.git```
* Navigate to the cloned sulolive/ folder ```cd sulolive```

## Usage
### Server
* Start server repl: ```lein repl```
** Initialize local server: ```eponai.repl=> (start-server)```

### Client
#### Web
* Start web client repl: ```rlwrap lein figwheel-web```
* Open Browser and navigate to: ```localhost:3000```

