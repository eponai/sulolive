# Subscriber Events
This document describes the events dispatched from the Subscriber of the Red5 Pro HTML SDK.

* [Listening to Subscriber Events](#listening-to-subscriber-events)
* [Common Events](#common-events)
* [WebRTC Subscriber Events](#webrtc-subscriber-events)
* [RTMP Subscriber Events](#rtmp-subscriber-events)
* [HLS Subscriber Events](#hls-subscriber-events)

## Listening to Subscriber Events
The Subscriber(s) included in the SDK are event emitters that have a basic API to subscribing and unsubscribing to events either by name or by wildcard.

To subscribe to all events from a subscriber:

```js
function handleSubscriberEvent (event) {
  // The name of the event:
  var type = event.type;
  // The dispatching publisher instance:
  var subscriber = event.subscriber;
  // Optional data releated to the event (not available on all events):
  var data = event.data;
}

var subscriber = new red5prosdk.RTCSubscriber();
subscriber.on('*', handleSubscriberEvent);
```

> The `*` type assignment is considered a "Wildcard" subscription - all events being issued by the subscriber instance will invoke the assign event handler.

To unsubscribe to all events from a subscriber after assinging an event handler:

```js
subscriber.off('*', handleSubscriberEvent);
```

The following sections of this document describe the event types that can also be listened to directly, instead of using the `*` wildcard.

## Common Events
The following events are common across all Subscriber implementations from the Red5 Pro HTML SDK. They can be accessed from the global `red5prosdk` object from the `SubscriberEventTypes` attribute.

| Access | Name | Meaning |
| --- | --- | --- |
| CONNECT_SUCCESS | 'Connect.Success' | When the subscriber has established a required remote connection, such as to a WebSocket or RTMP-based server. |
| CONNECT_FAILURE | 'Connect.Failure' | When the subscriber has failed to establish a required remote connection for consuming a stream. |
| SUBSCRIBE_START | 'Subscribe.Start' | When the subscriber has started a subscribing to a stream. |
| SUBSCRIBE_FAIL | 'Subscribe.Fail' | When the subscriber has failed to start subscribing to a stream. |
| SUBSCRIBE_INVALID_NAME | 'Subscribe.InvalidName' | When the subscriber is cannot start subscribing to stream because a stream associated with the `streamName` is not available. |
| SUBSCRIBE_STOP | 'Subscribe.Stop' | When the subscriber has successfully closed an active subscription to a stream. |
| SUBSCRIBE_METADATA | 'Subscribe.Metadata' | When metadata is received on the client from the server. |

## WebRTC Subscriber Events
The following events are specific to the `RTCSubscriber` implementation and accessible on the global `red5prosdk` object from the `RTCSubscriberEventTypes` attribute. These events are dispatched during the lifecycle of thre trickle ICE functionality required to start subscribing to a stream:

| Access | Name | Meaning |
| --- | --- | --- |
| PEER_CONNECTION_AVAILABLE | 'WebRTC.PeerConnection.Available' | When the negotation process has produced a valid `PeerConnection`. |
| OFFER_START | 'WebRTC.Offer.Start' | When the subscriber requests to start an offer on the `PeerConnection`. |
| OFFER_END | 'WebRTC.Offer.End' | When the subscriber has received a `SessionDescription` from a requested offer over the `PeerConnection`. |
| ANSWER_START | 'WebRTC.Answer.Start' | When the subscriber requests to send an answer on the `PeerConnection`. |
| ANSWER_END | 'WebRTC.Answer.End' | When the subscriber has received an answer (in form of a `MediaStream`) over the `PeerConnection`. |
| CANDIDATE_START | 'WebRTC.Candidate.Start' | When the subscriber requests to send a candidate on the `PeerConnection`. |
| CANDIDATE_END | 'WebRTC.Candidate.End' | When the subscriber has received a candidate over the `PeerConnection`. |
| ICE_TRICKLE_COMPLETE | 'WebRTC.IceTrickle.Complete' | When the negotaiton process (a.k.a. trickle) has completed and the subscriber will attempt at consuming a stream. |

## RTMP Subscriber Events
The following events are specific to the `RTMPSubscriber` implementation and accessible on the global `red5prosk` object from the `RTMPSubscriberEventTypes` attribute:

| Access | Name | Meaning |
| --- | --- | --- |
| EMBED_SUCCESS | 'FlashPlayer.Embed.Success' | When the subscriber-based SWF is successfully embedded in the page. |
| EMBED_FAILURE | 'FlashPlayer.Embed.Failure' | When the subscriber-based SWF fails to be embedded properly in the page. |

## HLS Subscriber Events
_There are currently no HLS-specific events. Please refer to the [common events](#common-events)._

