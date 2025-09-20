const { setGlobalOptions } = require("firebase-functions/v2/options");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

// Initialize Firebase Admin
admin.initializeApp();
const db = admin.firestore();

// Set global options for cost control
setGlobalOptions({ maxInstances: 10 });

/**
 * 1. Function to deduct points for a listing
 */
exports.createListing = onCall(async (request) => {
  try {
    const userId = request.auth?.uid;
    if (!userId) throw new HttpsError("unauthenticated", "Must be authenticated.");

    const { totalCost } = request.data;

    if (typeof totalCost !== "number" || !Number.isInteger(totalCost) || totalCost < 0) {
      throw new HttpsError("invalid-argument", "totalCost must be a non-negative integer.");
    }

    const userRef = db.collection("users").doc(userId);

    return db.runTransaction(async (t) => {
      const userDoc = await t.get(userRef);
      if (!userDoc.exists) throw new HttpsError("not-found", "User not found.");

      const currentPoints = userDoc.data().points || 0;
      if (currentPoints < totalCost) throw new HttpsError("failed-precondition", "Insufficient points.");

      t.update(userRef, { points: currentPoints - totalCost });

      return { message: "Points deducted successfully!" };
    });
  } catch (error) {
    logger.error("Error in createListing:", error);
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("internal", error.message || "Unknown error");
  }
});

/**
 * 2. Function to complete a listing and add points to the fulfiller
 */
exports.completeListing = onCall(async (request) => {
  const listerId = request.auth?.uid;
  if (!listerId) throw new HttpsError("unauthenticated", "Must be authenticated.");

  const { listingId, fulfillerId } = request.data;
  if (!listingId || !fulfillerId) throw new HttpsError("invalid-argument", "Missing listingId or fulfillerId.");

  const listingRef = db.collection("listings").doc(listingId);
  const fulfillerRef = db.collection("users").doc(fulfillerId);

  return db.runTransaction(async (t) => {
    const listingDoc = await t.get(listingRef);
    const fulfillerDoc = await t.get(fulfillerRef);

    if (!listingDoc.exists || !fulfillerDoc.exists) {
      throw new HttpsError("not-found", "Listing or fulfiller not found.");
    }

    const listingData = listingDoc.data();

    if (listingData.ownerID !== listerId) throw new HttpsError("permission-denied", "Only owner can complete the listing.");
    if (listingData.status === "fulfilled") throw new HttpsError("failed-precondition", "Listing already fulfilled.");

    const pointsToGive = (listingData.price !== "Free" && listingData.price !== "Other")
      ? parseInt(listingData.price, 10)
      : 0;

    const currentPoints = fulfillerDoc.data().points || 0;

    t.update(fulfillerRef, { points: currentPoints + pointsToGive });
    t.update(listingRef, {
      status: "fulfilled",
      fulfilledBy: fulfillerId,
      fulfilledTimestamp: admin.firestore.FieldValue.serverTimestamp()
    });

    return { message: "Listing fulfilled and points transferred successfully!" };
  });
});

/**
 * 3. Corrected redeemItem function
 *   Only uses userId, pointsCost, itemName
 */
exports.redeemItem = onCall(async (request) => {
  const userId = request.auth?.uid;
  if (!userId) throw new HttpsError("unauthenticated", "Must be authenticated.");

  const { pointsCost, itemName } = request.data;
  if (!pointsCost || !itemName) throw new HttpsError("invalid-argument", "Missing pointsCost or itemName.");

  const userRef = db.collection("users").doc(userId);
  const redemptionRef = db.collection("redemptionHistory").doc();

  return db.runTransaction(async (t) => {
    const userDoc = await t.get(userRef);
    if (!userDoc.exists) throw new HttpsError("not-found", "User not found.");

    const currentPoints = userDoc.data().points || 0;
    if (currentPoints < pointsCost) throw new HttpsError("failed-precondition", "Insufficient points.");

    // Deduct points and record redemption
    t.update(userRef, { points: currentPoints - pointsCost });
    t.set(redemptionRef, {
      userId,
      itemName,
      pointsCost,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      confirmationCode: Math.random().toString(36).substring(2, 10)
    });

    return { message: "Redeemed successfully!", remainingPoints: currentPoints - pointsCost };
  });
});
