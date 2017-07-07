// Give the service worker access to Firebase Messaging.
// Note that you can only use Firebase Messaging here, other Firebase libraries
// are not available in the service worker.
importScripts('https://www.gstatic.com/firebasejs/3.9.0/firebase-app.js');
importScripts('https://www.gstatic.com/firebasejs/3.9.0/firebase-messaging.js');

// Initialize the Firebase app in the service worker by passing in the
// messagingSenderId.
firebase.initializeApp({
  'messagingSenderId': '252203166563'
});

// Retrieve an instance of Firebase Messaging so that it can handle background
// messages.
const messaging = firebase.messaging();

messaging.setBackgroundMessageHandler(function(payload) {
  console.log('[firebase-messaging-sw.js] Received background message ', payload);
  console.log('[firebase-messaging-sw.js] Received background message ', payload["data"]);
  console.log('[firebase-messaging-sw.js] Received background message ', payload["data"]["title]"]);
  // Customize notification here
  const notificationTitle = payload["data"]["title]"];
  const notificationOptions = {
    body: payload["data"]["body]"],
    icon: '/assets/img/auth0-icon.png'
  };

  return self.registration.showNotification(notificationTitle,
      notificationOptions);
});