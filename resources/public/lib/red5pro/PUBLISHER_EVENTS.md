# Publisher Events
This document describes the events dispatched from the Publisher of the Red5 Pro HTML SDK.

* [Listening to Publisher Events](#listening-to-publisher-events)
* [Common Events](#common-events)
* [WebRTC Publisher Events](#webrtc-publisher-events)
* [RTMP Publisher Events](#rtmp-publisher-events)

## Listening to Publisher Events
The Publisher(s) included in the SDK are event emitters that have a basic API to subscribing and unsubscribing to events either by name or by wildcard.

To subscribe to all events from a publisher:

```js
function handlePublisherEvent (event) {
  // The name of the event:
  var type = event.type;
  // The dispatching publisher instance:
  var publisher = event.publisher;
  // Optional data releated to the event (not available on all events):
  var data = event.data;
}

var publisher = new red5prosdk.RTCPublisher();
publisher.on('*', handlePublisherEvent);
```

> The `*` type assignment is considered a "Wildcard" subscription - all events being issued by the publisher instance will invoke the assign event handler.

To unsubscribe to all events from a publisher after assinging an event handler:

```js
publisher.off('*', handlePublisherEvent);
```

The following sections of this document describe the event types that can also be listened to directly, instead of using the `*` wildcard.

## Common Events
The following events are common across all Publisher implementations from the Red5 Pro HTML SDK. They can be accessed from the global `red5prosdk` object from the `PublisherEventTypes` attribute.

| Access | Name | Meaning |
| --- | --- | --- |
| CONNECT_SUCCESS | 'Connect.Success' | When the publisher has established a required remote connection, such as to a WebSocket or RTMP-based server. |
| CONNECT_FAILURE | 'Connect.Failure' | When the publisher has failed to establish a required remote connection for streaming. |
| PUBLISH_START | 'Publish.Start' | When the publisher has started a broadcast stream. |
| PUBLISH_FAIL | 'Publish.Fail' | When the publisher has failed to start a broadcast stream. |
| PUBLISH_INVALID_NAME | 'Publish.InvalidName' | When the publisher is rejected from starting a broadcast stream because the `streamName` provided is already in use. |
| UNPUBLISH_SUCCESS | 'Unpublish.Success' | When the publisher has successfully closed an active broadcast stream. |

## WebRTC Publisher Events
The following events are specific to the `RTCPublisher` implementation and accessible on the global `red5prosdk` object from the `RTCPublisherEventTypes` attribute. These events are dispatched during the lifecycle of thre trickle ICE functionality required to start a broadcast:

| Access | Name | Meaning |
| --- | --- | --- |
| MEDIA_STREAM_AVAILABLE | 'WebRTC.MediaStream.Available' | When the negotation process has returned a `MediaStream` object to use. |
| PEER_CONNECTION_AVAILABLE | 'WebRTC.PeerConnection.Available' | When the negotation process has produced a valid `PeerConnection`. |
| OFFER_START | 'WebRTC.Offer.Start' | When the publisher requests to send an offer using a `SessionDescription` on the `PeerConnection`. |
| OFFER_END | 'WebRTC.Offer.End' | When the publisher has received an answer from the `SDP` offer on the `PeerConnection`. |
| ICE_TRICKLE_COMPLETE | 'WebRTC.IceTrickle.Complete' | When the negotaiton process (a.k.a. trickle) has completed and the publisher will attempt at opening a broadcast stream. |

## RTMP Publisher Events
The following events are specific to the `RTMPPublisher` implementation and accessible on the global `red5prosk` object from the `RTMPPublisherTypes` attribute:

| Access | Name | Meaning |
| --- | --- | --- |
| EMBED_SUCCESS | 'FlashPlayer.Embed.Success' | When the publisher-based SWF is successfully embedded in the page. |
| EMBED_FAILURE | 'FlashPlayer.Embed.Failure' | When the publisher-based SWF fails to be embedded properly in the page. |

