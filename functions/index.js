/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

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
    if (!userId) {
      throw new HttpsError(
        "unauthenticated",
        "The function must be called while authenticated."
      );
    }

    const { totalCost } = request.data;

    if (
      typeof totalCost !== "number" ||
      !Number.isInteger(totalCost) ||
      totalCost < 0
    ) {
      logger.error("Invalid totalCost received:", totalCost);
      throw new HttpsError(
        "invalid-argument",
        "The provided totalCost is not a valid integer."
      );
    }

    const userRef = db.collection("users").doc(userId);

    return db.runTransaction(async (transaction) => {
      const userDoc = await transaction.get(userRef);

      if (!userDoc.exists) {
        throw new HttpsError("not-found", "User not found.");
      }

      const currentPoints = userDoc.data().points || 0;

      if (currentPoints < totalCost) {
        throw new HttpsError(
          "failed-precondition",
          "Insufficient points."
        );
      }

      transaction.update(userRef, { points: currentPoints - totalCost });

      return { message: "Points deducted successfully!" };
    });
  } catch (error) {
    logger.error("Error in createListing function:", error);

    if (error instanceof HttpsError) {
      throw error;
    }

    throw new HttpsError(
      "internal",
      `An unknown error occurred. ${error.message}`
    );
  }
});

/**
 * 2. Function to complete a listing and add points to the fulfiller
 */
exports.completeListing = onCall(async (request) => {
  const listerId = request.auth?.uid;
  if (!listerId) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const { listingId, fulfillerId } = request.data;
  if (!listingId || !fulfillerId) {
    throw new HttpsError(
      "invalid-argument",
      "Missing listingId or fulfillerId."
    );
  }

  const listingRef = db.collection("listings").doc(listingId);
  const fulfillerRef = db.collection("users").doc(fulfillerId);

  return db.runTransaction(async (transaction) => {
    const listingDoc = await transaction.get(listingRef);
    const fulfillerDoc = await transaction.get(fulfillerRef);

    if (!listingDoc.exists || !fulfillerDoc.exists) {
      throw new HttpsError(
        "not-found",
        "Listing or fulfiller not found."
      );
    }

    const listingData = listingDoc.data();

    if (listingData.ownerID !== listerId) {
      throw new HttpsError(
        "permission-denied",
        "Only the listing owner can mark it as complete."
      );
    }

    if (listingData.status === "fulfilled") {
      throw new HttpsError(
        "failed-precondition",
        "This listing has already been fulfilled."
      );
    }

    const pointsToGive =
      listingData.price !== "Free" && listingData.price !== "Other"
        ? parseInt(listingData.price, 10)
        : 0;

    const currentPoints = fulfillerDoc.data().points || 0;

    transaction.update(fulfillerRef, {
      points: currentPoints + pointsToGive,
    });

    transaction.update(listingRef, {
      status: "fulfilled",
      fulfilledBy: fulfillerId,
      fulfilledTimestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      message: "Listing fulfilled and points transferred successfully!",
    };
  });
});

/**
 * 3. Function to redeem an item and deduct points
 */
exports.redeemItem = onCall(async (request) => {
  const userId = request.auth?.uid;
  if (!userId) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const { itemId, pointsCost } = request.data;

  const userRef = db.collection("users").doc(userId);
  const redemptionHistoryRef = db.collection("redemptionHistory").doc();
  const itemRef = db.collection("redeemableItems").doc(itemId);

  return db.runTransaction(async (transaction) => {
    const userDoc = await transaction.get(userRef);
    const itemDoc = await transaction.get(itemRef);

    if (!userDoc.exists || !itemDoc.exists) {
      throw new HttpsError("not-found", "User or item not found.");
    }

    const currentPoints = userDoc.data().points || 0;

    if (currentPoints < pointsCost) {
      throw new HttpsError(
        "failed-precondition",
        "Insufficient points."
      );
    }

    transaction.update(userRef, { points: currentPoints - pointsCost });

    transaction.set(redemptionHistoryRef, {
      userId: userId,
      itemId: itemId,
      itemName: itemDoc.data().itemName,
      pointsCost: pointsCost,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      confirmationCode: Math.random().toString(36).substring(2, 15),
    });

    return {
      message: "Item redeemed successfully!",
      redemptionId: redemptionHistoryRef.id,
    };
  });
});
