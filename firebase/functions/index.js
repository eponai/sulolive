'use strict';

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

// Inspired by child counter:
// https://github.com/firebase/functions-samples/blob/master/child-count/functions/index.js

// Counts the number of visitors for a store by counting their children.
exports.countStoreVisitor = functions.database.ref('/v2/store/{storeId}/visitors/{visitorId}')
.onWrite(event => {
  const locality = event.data.val() || event.data.previous.val();
  const storeId = event.params.storeId;
  const collectionRef = event.data.ref.parent;
  const countPath = '/v2/locality/' + locality + '/visitor-count/' + storeId;
  const countRef = admin.database().ref(countPath)

  // Return the promise from countRef.transaction() so our function 
  // waits for this async event to complete before it exits.
  return countRef.transaction(current => {
    if (event.data.exists() && !event.data.previous.exists()) {
      return (current || 0) + 1;
    }
    else if (!event.data.exists() && event.data.previous.exists()) {
      return (current || 0) - 1;
    }
  }).then(() => {
    console.log('Counter updated.');
  });
});

// If the number of visitors gets deleted, recount the number of visitors
exports.recountStoreVisitors = functions.database.ref('/v2/locality/{locality}/visitor-count/{storeId}').onWrite(event => {
  if (!event.data.exists()) {
    const counterRef = event.data.ref;
    const collectionRef = admin.database().ref('/v2/store/' + event.params.storeId + '/visitors');
    
    // Return the promise from counterRef.set() so our function 
    // waits for this async event to complete before it exits.
    return collectionRef.once('value')
        .then(visitors => counterRef.set(visitors.numChildren()));
  }
});

