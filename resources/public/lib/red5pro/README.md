<h3 align="center">
  <img src="assets/red5pro_logo.png" alt="Red5 Pro Logo" />
</h3>
<p align="center">
  <a href="#publisher">publisher</a> &bull;
  <a href="#subscriber">subscriber</a>
</p>
-------

# Red5 Pro HTML5 SDK
> The **Red5 Pro HTML5 SDK** allows you to integrate live streaming video into your desktop and mobile browser.

* [Quickstart](#quickstart)
  * [Installation](#installation)
* [Requirements](#requirements)
* [Usage](#usage)
  * [Publisher](#publisher)
    * [WebRTC](#webrtc-publisher)
    * [Flash/RTMP](#flash-publisher)
    * [Auto Failover](#failover-publisher)
    * [Lifecycle Events](#lifecycle-events-publisher)
  * [Subscriber](#subscriber)
    * [WebRTC](#webrtc-subscriber)
    * [Flash/RTMP](#flash-subscriber)
    * [HLS](#hls-subscriber)
    * [Auto Failover](#failover-subscriber)
    * [Lifecycle Events](#lifecycle-events-subscriber)
* [Contributing](#contributing)

## Quickstart
To begin working with the *Red5 Pro HTML5 SDK* in your project:

### Installation
In a browser:  
[download the latest release](https://github.com/infrared5/red5pro-html-sdk/releases)

```html
<!-- *Recommended WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
<!-- Red5 Pro SDK -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
<!-- video container -->
<div id="video-container">
  <video id="red5pro-subscriber" width="600" height="300" controls autoplay>
  </video>
</div>
<!-- Create subscriber -->
<script>
  (function(red5pro) {

    var rtcSubscriber = new red5pro.RTCSubscriber();
    var viewer = new red5pro.Red5ProPlaybackView();
    viewer.attachSubscriber(rtcSubscriber);

    rtcSubscriber.init({
      protocol: 'ws',
      host: 'localhost',
      port: 8081,
      app: 'live',
      streamName: 'mystream',
      iceServers: [{urls: 'stun:stun2.l.google.com:19302'}]
    })
    .then(rtcSubscriber.play)
    .then(function() {
      console.log('Playing!');
    })
    .catch(function(err) {
      console.log('Something happened. ' + err);
    });

  }(red5prosdk));
</script>
```

---

> **Note**: The following installation process using `npm` is not supported at the time of this writing - August 16th, 2016. Please use the above method of adding the script from the release to the page.

As a client-side module to be later be deployed for the browser:  
[install the module](http://link/to/npm/module)
```sh
$ npm install red5pro-sdk
```

```js
import { RTCSubscriber, Red5ProPlaybackView } from 'red5pro-sdk'

const subscriber = new RTCSubscriber()
const view = new Red5ProPlaybackView()
view.attachSubscriber(subscriber)

subscriber.init({
  protocol: 'ws',
  host: 'localhost',
  port: 8081,
  app: 'live',
  streamName: 'publisher1',
  iceServers: [{urls: 'stun:stun2.l.google.com:19302'}]
})
.then(() => {
  subscriber.play()
})
.then(() => {
  console.log('Playing!')
})
.catch(err => {
  console.log('Something happened. ' + err)
})
```

Included as post build of `red5pro-sdk-example.js` on a webpage:
```html
<!doctype html>
<html>
  <head>
    <!-- * Recommended WebRTC Shim -->
    <script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
  </head>
  <body>
    <div id="video-container">
      <video id="red5pro-subscriber" width="600" height="300" controls autoplay>
      </video>
    </div>
    <!-- Red5 Pro SDK -->
    <script src="lib/red5pro-sdk-example.js"></script>
  </body>
</html>
```

# Requirements
The **Red5 Pro HTML SDK** is intended to communicate with a [Red5 Pro Server](https://www.red5pro.com/), which allows for broadcasting and consuming live streams utilizing [WebRTC](https://developer.mozilla.org/en-US/docs/Web/Guide/API/WebRTC) and various protocols, including [RTMP](https://en.wikipedia.org/wiki/Real_Time_Messaging_Protocol) and [HLS](https://en.wikipedia.org/wiki/HTTP_Live_Streaming).

As such, you will need a distribution of the [Red5 Pro Server](https://www.red5pro.com/) running locally or accessible from the web, such as [Amazon Web Services](https://www.red5pro.com/docs/server/awsinstall/).

**[Start using the Red5 Pro Server today!](https://account.red5pro.com/login)**

# Usage
This section describes using the **Red5 Pro HTML SDK** browser install to create sessions for a [Publisher](#publisher) and a [Subscriber](#subscriber).

## Publisher
The following publisher types / protocols are supported:

* [WebRTC](#webrtc-publisher) (using [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API), [WebRTC](https://developer.mozilla.org/en-US/docs/Web/Guide/API/WebRTC), [getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) and the HTML5 [video](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video) Element)
* [RTMP](#flash-publisher) (using the custom Flash-based publisher developed for Red5 Pro)

Additionally, the **Red5 Pro HTML SDK** allows for automatic detection and failover to determine the correct publisher option to use based on desired order and browser support. To learn more, visit the [Auto Failover](#failover-publisher) section.

<h3 id="webrtc-publisher">WebRTC</h3>

> Utilizes WebSockets and WebRTC support in modern browsers.

_It is recommended to include [adapter.js](https://github.com/webrtc/adapter) when targeting the WebRTC subscriber._

#### Configuration Properties
| Property | Description | Notes |
| --- | --- | --- |
| protocol | The protocol for the WebSocket communication; `ws` or `wss`. | Required. _If deployed remotely, browsers require communication over secure WebSockets._ |
| host | The IP or address that the WebSocket server resides on. | Required |
| port | The port on the host that the WebSocket server resides on; `8081` or `8083`. | Required |
| app | The webapp name that the WebSocket is listening on. | Required |
| streamName | The name of the stream to subscribe to. | Required |
| iceServers | The list of ICE servers to use in requesting a Peer Connection. | Required |
| streamMode | The mode to broadcast; `live`, `record` or `append`. | _Optional. Default: `live`_ |
| bandwidth | A configuration object to setup bandwidth setting in publisher. | _Optional. Default: `{audio: 56, video: 512}`._ |

#### Example
```html
...
<body>
  <div id="video-container">
    <video id="red5pro-publisher"></video>
  </div>
</body>
...
<!-- WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  var iceServers = [{urls: 'stun:stun2.l.google.com:19302'}];

  // Create a new instance of the WebRTC publisher.
  var publisher = new red5prosdk.RTCPublisher();

  // Create a view instance based on video element id.
  var view = new red5prosdk.PublisherView('red5pro-publisher');

  // Access user media.
  navigator.getUserMedia({
    audio: true,
    video: true
  }, function(media) {

    // Upon access of user media,
    // 1. Attach the stream to the publisher.
    // 2. Show the stream as preview in view instance.
    publisher.attachStream(media);
    view.preview(media);

  }, function(error) {
    console.error(error);
  });

  view.attachPublisher(publisher);

  // Initialize
  publisher.init({
      protocol: 'ws',
      host: '10.0.0.1',
      port: 8081,
      app: 'live',
      streamName: 'mystream',
      streamMode: 'live',
      iceServers: iceServers
    })
    .then(function() {
      // Invoke the publish action
      return publisher.publish();
    })
    .catch(function(error) {
      // A fault occurred while trying to initialize and publish the stream.
      console.error(error);
    });
</script>
```

<h3 id="flash-publisher">Flash/RTMP</h3>

> Embeds a SWF file, utilizing [swfobject](https://github.com/swfobject/swfobject), to incorporate publishing over RTMP.

The **Red5 Pro HTML SDK** supports the following SWF integration:

* A bare-bones RTMP publisher - included in the `src` directory as **red5pro-publisher.swf** - and distributed with the `live` webapp of the [Red5 Pro Server](https://account.red5pro.com/login) install.
    * _Note: You will need to provide a URL to the [swfobject](https://github.com/swfobject/swfobject) library which will be dynamically injected at runtime if not - by default - found relative to the page at `lib/swfobject`._
* Your own custom Flash client, by specifying the `swf` property in the init configuration!

#### Configuration Properties
| Property | Description | Notes |
| --- | --- | --- |
| protocol | The protocol of the RTMP streaming endpoint; `rtmp` or `rtmps` | Required |
| host | The IP or address that the stream resides on. | Required |
| port | The port that the stream is accessible on. (e.g., `1935`) | Required |
| app | The application to locate the stream. | Required |
| streamName | The stream name to subscribe to. | Required |
| streamMode | The mode to broadcast; `live`, `record` or `append`. | _Optional. Default: `live`_ |
| swf | The swf file location to use as the Flash client publisher. | _Optional. Default: `lib/red5pro/red5pro-publisher.swf`_ |
| width | The width of the video element within the SWF movie. | _Optional. Default: `320`_ |
| height | The height of the video element within the SWF movie. | _Optional. Default: `240`_ |
| embedWidth | The width of the object element for the SWF movie embed. | _Optional. Default: `320`_ |
| embedHeight | The height of the object element for the SWF movie embed. | _Optional. Default: `240`_ |
| minFlashVersion | Minimum semversion of the target Flash Player. | _Optional. Default: `10.0.0`_ |
| swfobjectURL | Location of the [swfobject](https://github.com/swfobject/swfobject) dependency library that will be dynamically injected. | _Optional. Default: `lib/swfobject/swfobject.js`_ |
| productInstallURL | Location of the **playerProductInstall** SWF used by [swfobject](https://github.com/swfobject/swfobject). | _Optional. Default: `lib/swfobject/playerProductInstall.swf`_ |

#### Example
```html
...
<body>
  <div id="video-container">
    <video id="red5pro-publisher"></video>
  </div>
</body>
...
<!-- WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  var iceServers = [{urls: 'stun:stun2.l.google.com:19302'}];

  // Create a new instance of the WebRTC publisher.
  var publisher = new red5prosdk.RTMPPublisher();

  // Create a view instance based on video element id.
  var view = new red5prosdk.PublisherView('red5pro-publisher');
  view.attachPublisher(publisher);

  // Initialize
  publisher.init({
      protocol: 'rtmp',
      host: '10.0.0.1',
      port: 1935,
      app: 'live',
      streamName: 'mystream',
      swf: 'lib/red5pro/red5pro-publisher.swf'
    })
    .then(function() {
      // Invoke publish action.
      publisher.publish()
    })
    .catch(function(error) {
      // A fault occurred while trying to initialize and publish the stream.
      console.error(error);
    });
```

<h3 id="failover-publisher">Auto Failover and Order</h3>

While you can specifically target a publisher - as described in the previous [Player Flavors](#publisher) section - you may want to let the library select the optimal publisher based on browser compatibility per support flavors.

As you may have noticed form the [previous section](#publisher), the source configuration for each publisher has differing property requirements. This is due simply to the technologies and broadcast strategies that each use:

* The **WebRTC** player utilizes [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API), [WebRTC](https://developer.mozilla.org/en-US/docs/Glossary/WebRTC) and  [getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) to publish a video and to be displayed in an [HTML5 video](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video) element.
* The **Flash/RTMP** player utilizes a SWF file to broadcast and view streaming video from the Flash Player plugin.

As such, the **init** configuration provided to the library to allow for auto-failover player selection should be provided with attributes defining the target source(s) - i.e., `rtc` and/or `rtmp`:

```html
...
<body>
  <div id="video-container">
    <video id="red5pro-publisher"></video>
  </div>
</body>
...
<!-- WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  var iceServers = [{urls: 'stun:stun2.l.google.com:19302'}];

  // Create a new instance of the failover publisher.
  var publisher = new red5prosdk.Red5ProPublisher();

  // Create a view instance based on video element id.
  var view = new red5prosdk.PublisherView('red5pro-publisher');
  view.attachPublisher(publisher);

  // Set publish order and initialize
  publisher
    .setPublishOrder(['rtc', 'rtmp'])
    .init({
      "rtmp": {
        // See above documentation for RTMP source option requirements.
      },
      "rtc": {
        // See above documentation for WebRTC source option requirements.
      }
    })
    .then(function(selectedPublisher) {
      // Publisher implementation determined based on order and browser support.

      // If determined publisher implementation is WebRTC,
      //  set up preview and establish stream.
      if (selectedPublisher.getType().toLowerCase() === publisher.publishTypes.RTC) {
        navigator.getUserMedia({
            audio: true,
            video: true
          }, function (media) {
            selectedPublisher.attachStream(media)
            publisherView.preview(media);
          }, function (error) {
            console.error(error);
          });
      }
      return selectedPublisher.publish();
    })
    .then(function() {
      // Publishing has initiated successfully.
    })
    .catch(function(error) {
    });

</script>
```

<h3 id="lifecycle-events-publisher">Lifecycle Events</h3>
Please refer to the [Publisher Events](PUBLISHER_EVENTS.md) documentation regarding the events API.

---

## Subscriber
The following player types / protocols are supported:

* [WebRTC](#webrtc-subscriber) (using [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API), [WebRTC](https://developer.mozilla.org/en-US/docs/Web/Guide/API/WebRTC) and the HTML5 [video](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video) Element)
* [RTMP](#flash-subscriber) (using the custom Flash-based player developed for Red5 Pro)
* [HLS](#hls-subscriber) (using the HTML5 Video Element)

Additionally, the **Red5 Pro HTML SDK** allows for automatic detection and failover to determine the correct playback option to use based on desired order and browser support. To learn more, visit the [Auto Failover](#failover-subscriber) section.

<h3 id="webrtc-subscriber">WebRTC</h3>

> Utilizes WebSockets and WebRTC support in modern browsers.

_It is recommended to include [adapter.js](https://github.com/webrtc/adapter) when targeting the WebRTC subscriber._

#### Configuration Properties
| Property | Description | Notes |
| --- | --- | --- |
| protocol | The protocol for the WebSocket communication. | Required |
| host | The IP or address that the WebSocket server resides on. | Required |
| port | The port on the host that the WebSocket server resides on. | Required |
| app | The webapp name that the WebSocket is listening on. | Required |
| streamName | The name of the stream to subscribe to. | Required |
| subscriptionId | A unique string representing the requesting client. | _Optional. Default generated by library._ |
| iceServers | The list of ICE servers to use in requesting a Peer Connection. | Required |
| bandwidth | A configuration object to setup playback. | _Optional_ |
| autoplay | Flag to autoplay the stream when received upon negotiation. | _Optional. Default: `true`_ |

#### Example
```html
...
<body>
  <div id="video-container">
    <video id="red5pro-video"></video>
  </div>
</body>
...
<!-- WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>
<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  var iceServers = [{urls: 'stun:stun2.l.google.com:19302'}];

  // Create a view instance based on video element id.
  var viewer = new red5prosdk.PlaybackView('red5pro-video');

  // Create a new instance of the WebRTC subcriber.
  var subscriber = new red5prosdk.RTCSubscriber();
  // Attach the subscriber to the view.
  viewer.attachSubscriber(subscriber);

  // Initialize
  subscriber.init({
    protocol: 'ws',
    host: '10.0.0.1',
    port: 8081,
    app: 'live',
    subscriptionId: 'subscriber-' + Math.floor(Math.random() * 0x10000).toString(16),
    streamName: 'mystream',
    iceServers: iceServers,
    bandwidth: {
      audio: 56,
      video: 512
    }
  })
  .then(function(player) {
    // `player` is the WebRTC Player instance.
    // Invoke the play action.
    player.play();
  })
  .catch(function(error) {
    // A fault occurred while trying to initialize and playback the stream.
    console.error(error)
  });
</script>
```

<h3 id="flash-subscriber">Flash/RTMP</h3>

> Embeds a SWF file, utilizing [swfobject](https://github.com/swfobject/swfobject), to incorporate playback over RTMP.

The **Red5 Pro HTML SDK** supports the following SWF integration:

* A customized [videojs swf](https://github.com/videojs) - included in `src` directory as **red5pro-videojs.swf** - and utilizing the `RTMP` playback support from [videojs](https://github.com/videojs).
    * _Note: You will need to also include the [videojs](http://videojs.com/) script on the page as it is not bundled in the Red5 Pro HTML SDK._
* A bare-bones RTMP playback viewer - included in the `src` directory as **red5pro-subscriber.swf** - and distributed with the `live` webapp of the [Red5 Pro Server](https://account.red5pro.com/login) install.
    * _Note: You will need to provide a URL to the [swfobject](https://github.com/swfobject/swfobject) library which will be dynamically injected at runtime if not - by default - found relative to the page at `lib/swfobject`._
* Your own custom Flash client, by specifying the `swf` property in the init configuration!

#### Configuration Properties
| Property | Description | Notes |
| --- | --- | --- |
| protocol | The protocol of the RTMP streaming endpoint. (e.g., `rtmp`, `rtmps`) | Required |
| host | The IP or address that the stream resides on. | Required |
| port | The port that the stream is accessible on. | Required |
| app | The application to locate the stream. | Required |
| streamName | The stream name to subscribe to. | Required |
| mimeType | The __mimeType__ to assign the source added to the `video` element | _Optional. Default: `rtmp/flv`_ |
| swf | The swf file location to use as the Flash client playback. | _Optional. Default: `lib/red5pro/red5pro-video-js.swf`_ |
| width | The width of the video element within the SWF movie. | _Optional. Default: `320`_ |
| height | The height of the video element within the SWF movie. | _Optional. Default: `240`_ |
| embedWidth | The width of the object element for the SWF movie embed. | _Optional. Default: `320`_ |
| embedHeight | The height of the object element for the SWF movie embed. | _Optional. Default: `240`_ |
|| useVideoJS | Flag to utilize the [videojs](https://github.com/videojs) library. | _Optional. Default: `false`_ |
| minFlashVersion | Minimum semversion of the target Flash Player. | _Optional. Default: `10.0.0`_ |
| swfobjectURL | Location of the [swfobject](https://github.com/swfobject/swfobject) dependency library that will be dynamically injected. | _Optional. Default: `lib/swfobject/swfobject.js`_ |
| productInstallURL | Location of the **playerProductInstall** SWF used by [swfobject](https://github.com/swfobject/swfobject). | _Optional. Default: `lib/swfobject/playerProductInstall.swf`_ |

#### Example
```html
...
<body>
  <div id="video-container">
    <video id="red5pro-video" width="600" height="300"
      class="video-js vjs-default-skin"
      controls autoplay data-setup="{}">
    </video>
  </div>
</body>
...
<!-- Optional VideoJS support. -->
<link href="lib/videojs/video-js.min.css" rel="stylesheet">
<script src="lib/videojs/video.min.js"></script>
<script src="lib/videojs/videojs-media-sources.min.js"></script>

<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  // Create a view instance based on video element id.
  var viewer = new red5prosdk.PlaybackView('red5pro-video');

  // Create a new instance of the Flash/RTMP subcriber.
  var subscriber = new red5prosdk.RTMPSubscriber();
  // Attach the subscriber to the view.
  viewer.attachSubscriber(subscriber);

  // Initialize
  subscriber.init({
    protocol: 'rtmp',
    host: '10.0.0.1',
    port: 1935,
    app: 'live',
    streamName: 'mystream',
    mimeType: 'rtmp/flv',
    swf: 'lib/red5pro-video-js.swf'
  })
  .then(function(player) {
    // `player` is the WebRTC Player instance.
    // Invoke the play action.
    player.play();
  })
  .catch(function(error) {
    // A fault occurred while trying to initialize and playback the stream.
    console.error(error)
  });
```

The above example defaults to integrating with the [videojs](https://github.com/videojs) library and the [custom player build](https://github.com/red5pro/video-js-swf/tree/feature/red5pro) created for the **Red5 Pro HTML SDK**.

This is the default behavior when the `useVideoJS` property of the init configuration is set or left as the default value of `true`.

By setting `useVideoJS: false` on the init configuration, you can allow the library to load the default `red5pro-subscriber.swf`, or specify your own custom SWF!

<h3 id="hls-subscriber">HLS</h3>

> Utilizes the [HLS support](https://github.com/videojs/videojs-contrib-hls) for [videojs](https://github.com/videojs).

#### Configuration Properties
| Property | Description | Notes |
| --- | --- | --- |
| protocol | The protocol uri that the stream source resides on. | Required |
| host | The IP or address uri that the stream source resides on. | Required |
| port | The port uri that the stream source resides on. | Required |
| app | The webapp name that the stream source resides in. | Required |
| streamName | The stream name to subscribe to. | Required |
| mimeType | The mime-type of the stream source. | _Optional. Default: `application/x-mpegURL`_ |
| swf | The fallback SWF file to use if HTML5 `video` element is not supported. | _Optional. Default: `lib/red5pro/red5pro-video-js.swf`_ |

#### Example
```html
...
<body>
  <div id="video-container">
    <video id="red5pro-video" width="600" height="300"
      class="video-js vjs-default-skin"
      controls autoplay data-setup="{}">
    </video>
  </div>
</body>
...
<!-- Required VideoJS support for HLS playback. -->
<link href="lib/videojs/video-js.min.css" rel="stylesheet">
<script src="lib/videojs/video.min.js"></script>
<script src="lib/videojs/videojs-media-sources.min.js"></script>
<script src="lib/videojs/videojs.hls.min.js"></script>

<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
  // Create a view instance based on video element id.
  var viewer = new red5prosdk.PlaybackView('red5pro-video');

  // Create a new instance of the HLS subcriber.
  var subscriber = new red5prosdk.HLSSubscriber();
  // Attach the subscriber to the view.
  viewer.attachSubscriber(subscriber);

  // Initialize
  subscriber.init({
    protocol: 'http',
    host: '10.0.0.1',
    port: 5080,
    app: 'live',
    streamName: 'mystream',
    mimeType: 'application/x-mpegURL',
    swf: 'lib/red5pro/red5pro-video-js.swf'
  })
  .then(function(player) {
    // `player` is the WebRTC Player instance.
    // Invoke the play action.
    player.play();
  })
  .catch(function(error) {
    // A fault occurred while trying to initialize and playback the stream.
    console.error(error)
  });
```

In the above example, the SWF fallback - used when the [videojs](https://github.com/videojs) library determines that HLS support is not sufficient - is the [custom videojs client](https://github.com/red5pro/video-js-swf) developed for the **Red5 Pro HTML SDK**.

You can provide your own custom SWF, just as you can for the Flash/RTMP playback, by setting the `swf` init configuration property.

## Auto Failover and Order
While you can specifically target a player - as described in the previous [Player Flavors](#subscriber) section - you may want to let the library select the optimal player based on browser compatibility per support flavors.

As you may have noticed form the [previous section](#subscriber), the source configuration for each player has differing property requirements. This is due simply to the technologies and playback strategies that each use:

* The **WebRTC** player utilizes [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API) and [WebRTC](https://developer.mozilla.org/en-US/docs/Glossary/WebRTC) to subscribe to a video to be displayed in an [HTML5 video](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video) element.
* The **Flash/RTMP** player utilizes a SWF file to playback streaming video in the Flash Player plugin.
* The **HLS** player utilizes the [HTTP Live Streaming protocol](https://developer.mozilla.org/en-US/Apps/Fundamentals/Audio_and_video_delivery/Live_streaming_web_audio_and_video#HLS) to subscribe to a stream and [VideoJS](http://videojs.com/) to provide playback with optional fallback of SWF.

As such, the **init** configuration provided to the library to allow for auto-failover player selection should be provided with attributes defining the target source(s) - i.e., `rtc`, `rtmp` and/or `hls`:

```html
...
<body>
  <div id="video-container">
    <video id="red5pro-video" width="600" height="300"
      class="video-js vjs-default-skin"
      controls autoplay data-setup="{}">
    </video>
  </div>
</body>
...
<!-- WebRTC Shim -->
<script src="http://webrtc.github.io/adapter/adapter-latest.js"></script>

<!-- VideoJS support for HLS playback. -->
<link href="lib/videojs/video-js.min.css" rel="stylesheet">
<script src="lib/videojs/video.min.js"></script>
<script src="lib/videojs/videojs-media-sources.min.js"></script>
<script src="lib/videojs/videojs.hls.min.js"></script>

<!-- Exposes `red5prosdk` on the window global. -->
<script src="lib/red5pro/red5pro-sdk.min.js"></script>
...
<script>
  // Create a view instance based on video element id.
  var viewer = new red5prosdk.PlaybackView('red5pro-video');

  // Create a new instance of the HLS subcriber.
  var subscriber = new red5prosdk.Red5ProSubscriber();
  // Attach the subscriber to the view.
  viewer.attachSubscriber(subscriber);

  subscriber
    .setPlaybackOrder(['rtc', 'rtmp', 'hls'])
    .init({
       "rtmp": {
          // See above documentation for RTMP source option requirements.
        },
        "rtc": {
          // See above documentation for WebRTC source option requirements.
        },
        "hls": {
          // See above documentation for HLS source option requirements
        }
    })
    .then((player) => {
      // `player` has been determined from browser support.
      // Invoke play action
      player.play()
    })
    .then(function() {
      // Playback has initiated successfully.
    })
    .catch(function(error) {
      // A fault occurred in finding failover player and playing stream.
      console.error(error)
    });
</script>
```

Important things to note:

* Only `rtc`, `rtmp` and `hls` are supported values for order and are also accessible as enums on `Red5ProVidepPlayer.playbackTypes`

<h3 id="lifecycle-events-subscriber">Lifecycle Events</h3>
Please refer to the [Subscriber Events](SUBSCRIBER_EVENTS.md) documentation regarding the events API.

# Contributing
> Please refer to the [Contributing Documentation](CONTRIBUTING.md) to learn more about contributing to the development of the Red5 Pro HTML SDK.
