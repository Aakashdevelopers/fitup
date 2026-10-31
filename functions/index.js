const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios");
const FormData = require("form-data");
const TLSSigAPIv2 = require("tls-sig-api-v2");

admin.initializeApp();
const db = admin.firestore();

// Environment Configuration
const TENCENT_SDK_APP_ID = process.env.TENCENT_SDK_APP_ID || "20050167";
const TENCENT_SECRET_KEY = process.env.TENCENT_SECRET_KEY || "55af570dc89b54803f7c55946df7df6610b16d72399380026007e0449d0262a3";
const IMGBB_API_KEY = process.env.IMGBB_API_KEY || "98b5b00cee79645e2ea44a187446c2e3";

/**
 * 1. generateTencentUserSig
 * Securely generates a Tencent UserSig for the authenticated Firebase user.
 */
exports.generateTencentUserSig = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to request a Tencent UserSig."
    );
  }

  const userId = context.auth.uid;
  const expireSeconds = 86400 * 7; // 7 days expiration

  try {
    const api = new TLSSigAPIv2.Api(parseInt(TENCENT_SDK_APP_ID, 10), TENCENT_SECRET_KEY);
    const userSig = api.genUserSig(userId, expireSeconds);

    return {
      sdkAppId: parseInt(TENCENT_SDK_APP_ID, 10),
      userSig: userSig,
      userId: userId,
      expireSeconds: expireSeconds
    };
  } catch (error) {
    console.error("Error generating Tencent UserSig:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Failed to generate Tencent UserSig: " + error.message
    );
  }
});

/**
 * 2. uploadPublicImageProxy
 * Uploads non-sensitive public images (like profile avatars) to ImgBB.
 */
exports.uploadPublicImageProxy = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to upload public images."
    );
  }

  const { imageBase64, mimeType, fileName } = data;
  if (!imageBase64) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Base64 image data is required."
    );
  }

  const validMimeTypes = ["image/jpeg", "image/png", "image/webp", "image/jpg"];
  if (mimeType && !validMimeTypes.includes(mimeType.toLowerCase())) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Unsupported image format. Allowed: JPEG, PNG, WEBP."
    );
  }

  // Estimate binary size from Base64 (3/4 ratio)
  const estimatedSizeInBytes = (imageBase64.length * 3) / 4;
  const maxSizeBytes = 5 * 1024 * 1024; // 5MB limit
  if (estimatedSizeInBytes > maxSizeBytes) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Image file size exceeds maximum limit of 5MB."
    );
  }

  try {
    const form = new FormData();
    form.append("key", IMGBB_API_KEY);
    form.append("image", imageBase64);
    if (fileName) {
      form.append("name", fileName);
    }

    const response = await axios.post("https://api.imgbb.com/1/upload", form, {
      headers: form.getHeaders()
    });

    if (response.data && response.data.success) {
      const imgData = response.data.data;
      return {
        success: true,
        url: imgData.url,
        displayUrl: imgData.display_url,
        thumbUrl: imgData.thumb ? imgData.thumb.url : imgData.url,
        deleteUrl: imgData.delete_url
      };
    } else {
      throw new Error("ImgBB API returned unsuccessful response");
    }
  } catch (error) {
    console.error("ImgBB Upload Proxy Error:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Image upload to ImgBB failed: " + (error.response?.data?.error?.message || error.message)
    );
  }
});

/**
 * 3. processFriendshipOperation
 * Handles sensitive server-side friendship logic safely.
 */
exports.processFriendshipOperation = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = context.auth.uid;
  const { action, targetUid, requestId } = data;

  if (!action) {
    throw new functions.https.HttpsError("invalid-argument", "Action type is required.");
  }

  if (action === "SEND_REQUEST") {
    if (!targetUid || targetUid === callerUid) {
      throw new functions.https.HttpsError("invalid-argument", "Cannot send friend request to yourself or invalid user.");
    }

    const [u1, u2] = [callerUid, targetUid].sort();
    const friendshipId = `${u1}_${u2}`;

    // Parallelize pre-checks to eliminate sequential network latency
    const [block1, block2, existingReq, existingFriendship] = await Promise.all([
      db.collection("userBlocks").where("blockerUid", "==", callerUid).where("blockedUid", "==", targetUid).get(),
      db.collection("userBlocks").where("blockerUid", "==", targetUid).where("blockedUid", "==", callerUid).get(),
      db.collection("friendRequests")
        .where("senderUid", "==", callerUid)
        .where("receiverUid", "==", targetUid)
        .where("status", "==", "PENDING")
        .get(),
      db.collection("friendships").doc(friendshipId).get()
    ]);

    if (!block1.empty || !block2.empty) {
      throw new functions.https.HttpsError("failed-precondition", "Cannot send request due to user blocking settings.");
    }

    if (!existingReq.empty) {
      throw new functions.https.HttpsError("already-exists", "A pending friend request already exists.");
    }

    if (existingFriendship.exists) {
      throw new functions.https.HttpsError("already-exists", "Users are already friends.");
    }

    // Create Friend Request
    const reqRef = db.collection("friendRequests").doc();
    const now = admin.firestore.FieldValue.serverTimestamp();

    // Fetch caller user doc in parallel with request creation preparation
    const callerUserDocPromise = db.collection("users").doc(callerUid).get();

    await reqRef.set({
      requestId: reqRef.id,
      senderUid: callerUid,
      receiverUid: targetUid,
      status: "PENDING",
      createdAt: now,
      updatedAt: now
    });

    const callerUserDoc = await callerUserDocPromise;
    const callerName = callerUserDoc.exists ? (callerUserDoc.data().displayName || callerUserDoc.data().username) : "A gym buddy";

    const notifRef = db.collection("users").doc(targetUid).collection("notifications").doc();
    await notifRef.set({
      notificationId: notifRef.id,
      type: "FRIEND_REQUEST",
      title: "New Friend Request 🏋️‍♂️",
      body: `${callerName} sent you a friend request.`,
      metadata: { senderUid: callerUid, requestId: reqRef.id },
      isRead: false,
      createdAt: now
    });

    return { success: true, requestId: reqRef.id };
  }

  if (action === "ACCEPT_REQUEST") {
    if (!requestId) {
      throw new functions.https.HttpsError("invalid-argument", "RequestId is required.");
    }

    const reqRef = db.collection("friendRequests").doc(requestId);
    const reqDoc = await reqRef.get();
    if (!reqDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Friend request not found.");
    }

    const reqData = reqDoc.data();
    if (reqData.receiverUid !== callerUid) {
      throw new functions.https.HttpsError("permission-denied", "Only the receiver can accept this request.");
    }

    if (reqData.status !== "PENDING") {
      throw new functions.https.HttpsError("failed-precondition", "Request is no longer pending.");
    }

    const senderUid = reqData.senderUid;
    const [u1, u2] = [senderUid, callerUid].sort();
    const friendshipId = `${u1}_${u2}`;

    const batch = db.batch();
    const now = admin.firestore.FieldValue.serverTimestamp();

    // 1. Update Request
    batch.update(reqRef, { status: "ACCEPTED", updatedAt: now });

    // 2. Create Friendship Record
    const friendshipRef = db.collection("friendships").doc(friendshipId);
    batch.set(friendshipRef, {
      friendshipId: friendshipId,
      user1Uid: u1,
      user2Uid: u2,
      createdAt: now
    });

    // 3. Atomically increment friend counts
    batch.update(db.collection("users").doc(callerUid), {
      friendsCount: admin.firestore.FieldValue.increment(1)
    });
    batch.update(db.collection("users").doc(senderUid), {
      friendsCount: admin.firestore.FieldValue.increment(1)
    });

    await batch.commit();

    // Send Notification to sender
    const receiverUserDoc = await db.collection("users").doc(callerUid).get();
    const receiverName = receiverUserDoc.exists ? (receiverUserDoc.data().displayName || receiverUserDoc.data().username) : "A gym buddy";

    const notifRef = db.collection("users").doc(senderUid).collection("notifications").doc();
    await notifRef.set({
      notificationId: notifRef.id,
      type: "FRIEND_ACCEPT",
      title: "Friend Request Accepted 🎉",
      body: `${receiverName} accepted your friend request. You can now send private Snaps!`,
      metadata: { friendUid: callerUid },
      isRead: false,
      createdAt: now
    });

    return { success: true, friendshipId };
  }

  if (action === "REJECT_REQUEST") {
    if (!requestId) {
      throw new functions.https.HttpsError("invalid-argument", "RequestId is required.");
    }

    const reqRef = db.collection("friendRequests").doc(requestId);
    const reqDoc = await reqRef.get();
    if (!reqDoc.exists) return { success: true };

    if (reqDoc.data().receiverUid !== callerUid) {
      throw new functions.https.HttpsError("permission-denied", "Unauthorized.");
    }

    await reqRef.update({
      status: "REJECTED",
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return { success: true };
  }

  if (action === "REMOVE_FRIEND") {
    if (!targetUid) {
      throw new functions.https.HttpsError("invalid-argument", "TargetUid is required.");
    }

    const [u1, u2] = [callerUid, targetUid].sort();
    const friendshipId = `${u1}_${u2}`;
    const friendshipRef = db.collection("friendships").doc(friendshipId);
    const doc = await friendshipRef.get();

    if (!doc.exists) return { success: true };

    const batch = db.batch();
    batch.delete(friendshipRef);

    batch.update(db.collection("users").doc(callerUid), {
      friendsCount: admin.firestore.FieldValue.increment(-1)
    });
    batch.update(db.collection("users").doc(targetUid), {
      friendsCount: admin.firestore.FieldValue.increment(-1)
    });

    await batch.commit();
    return { success: true };
  }

  if (action === "BLOCK_USER") {
    if (!targetUid || targetUid === callerUid) {
      throw new functions.https.HttpsError("invalid-argument", "Invalid block target.");
    }

    const blockId = `${callerUid}_${targetUid}`;
    const blockRef = db.collection("userBlocks").doc(blockId);

    const [u1, u2] = [callerUid, targetUid].sort();
    const friendshipId = `${u1}_${u2}`;
    const friendshipRef = db.collection("friendships").doc(friendshipId);
    const friendshipDoc = await friendshipRef.get();

    const batch = db.batch();
    batch.set(blockRef, {
      blockId,
      blockerUid: callerUid,
      blockedUid: targetUid,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    if (friendshipDoc.exists) {
      batch.delete(friendshipRef);
      batch.update(db.collection("users").doc(callerUid), {
        friendsCount: admin.firestore.FieldValue.increment(-1)
      });
      batch.update(db.collection("users").doc(targetUid), {
        friendsCount: admin.firestore.FieldValue.increment(-1)
      });
    }

    await batch.commit();
    return { success: true };
  }

  if (action === "UNBLOCK_USER") {
    if (!targetUid) return { success: true };
    const blockId = `${callerUid}_${targetUid}`;
    await db.collection("userBlocks").doc(blockId).delete();
    return { success: true };
  }

  throw new functions.https.HttpsError("unimplemented", "Unknown friendship operation.");
});

/**
 * 4. sendNotificationTrigger
 * Triggered on new notification creation in Firestore to send FCM push messages.
 */
exports.sendNotificationTrigger = functions.firestore
  .document("users/{uid}/notifications/{notificationId}")
  .onCreate(async (snapshot, context) => {
    const targetUid = context.params.uid;
    const notifData = snapshot.data();

    if (!notifData || !notifData.title || !notifData.body) return;

    // Retrieve active device tokens for the user
    const tokensSnapshot = await db.collection("users")
      .doc(targetUid)
      .collection("deviceTokens")
      .get();

    if (tokensSnapshot.empty) {
      console.log(`No active device tokens found for user ${targetUid}`);
      return;
    }

    const tokens = tokensSnapshot.docs.map(doc => doc.id);

    const payload = {
      notification: {
        title: notifData.title,
        body: notifData.body
      },
      data: {
        type: notifData.type || "GENERAL",
        notificationId: notifData.notificationId || "",
        ...(notifData.metadata ? Object.fromEntries(Object.entries(notifData.metadata).map(([k, v]) => [k, String(v)])) : {})
      },
      tokens: tokens
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(payload);
      console.log(`Successfully sent multicast notification to ${response.successCount} devices.`);

      // Clean up invalid or expired registration tokens
      if (response.failureCount > 0) {
        const failedTokens = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            const errCode = resp.error?.code;
            if (
              errCode === "messaging/invalid-registration-token" ||
              errCode === "messaging/registration-token-not-registered"
            ) {
              failedTokens.push(tokens[idx]);
            }
          }
        });

        if (failedTokens.length > 0) {
          const batch = db.batch();
          failedTokens.forEach(token => {
            const tokenRef = db.collection("users").doc(targetUid).collection("deviceTokens").doc(token);
            batch.delete(tokenRef);
          });
          await batch.commit();
          console.log(`Cleaned up ${failedTokens.length} expired FCM tokens.`);
        }
      }
    } catch (error) {
      console.error("FCM Send Error:", error);
    }
  });

// ============================================================================
// DAILY STREAK SYSTEM CALLABLE FUNCTIONS
// ============================================================================

const STREAK_MILESTONES = [
  { milestone: 3, rewardId: "milestone_3", rewardType: "BADGE", title: "3-Day Novice Flame 🔥", description: "Completed 3 consecutive workout check-ins!", rewardValue: "Bronze Flame Badge" },
  { milestone: 7, rewardId: "milestone_7", rewardType: "TITLE", title: "7-Day Iron Warrior 🏋️‍♂️", description: "Conquered a full week of continuous workout check-ins!", rewardValue: "Iron Warrior Title" },
  { milestone: 14, rewardId: "milestone_14", rewardType: "FRAME", title: "14-Day Silver Streak ⚡", description: "Maintained a 2-week active workout streak!", rewardValue: "Silver Profile Frame" },
  { milestone: 30, rewardId: "milestone_30", rewardType: "BADGE", title: "30-Day Gold Champion 👑", description: "Achieved a full 1-month fitness streak milestone!", rewardValue: "Gold Champion Badge" },
  { milestone: 60, rewardId: "milestone_60", rewardType: "TITLE", title: "60-Day Platinum Legend 💎", description: "60 consecutive days of unwavering workout discipline!", rewardValue: "Platinum Legend Title" },
  { milestone: 100, rewardId: "milestone_100", rewardType: "FRAME", title: "100-Day Diamond Titan 🏆", description: "Ultimate century streak achieved! You are a Gym Titan!", rewardValue: "Diamond Titan Frame" }
];

/**
 * Helper to get authoritative YYYY-MM-DD date for a timezone string.
 */
function getAuthoritativeDateString(timezone) {
  try {
    const options = { timeZone: timezone || "UTC", year: "numeric", month: "2-digit", day: "2-digit" };
    const formatter = new Intl.DateTimeFormat("en-CA", options);
    return formatter.format(new Date());
  } catch (e) {
    const formatter = new Intl.DateTimeFormat("en-CA", { timeZone: "UTC", year: "numeric", month: "2-digit", day: "2-digit" });
    return formatter.format(new Date());
  }
}

/**
 * Helper to get previous calendar day string YYYY-MM-DD in UTC.
 */
function getYesterdayDateString(todayStr) {
  const date = new Date(todayStr + "T00:00:00Z");
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().split("T")[0];
}

/**
 * Helper to compute next milestone progress details.
 */
function getNextMilestoneInfo(currentStreak) {
  for (const m of STREAK_MILESTONES) {
    if (currentStreak < m.milestone) {
      const prevMilestone = STREAK_MILESTONES.find(x => x.milestone < m.milestone)?.milestone || 0;
      const totalSteps = m.milestone - prevMilestone;
      const currentProgress = currentStreak - prevMilestone;
      const percent = Math.min(100, Math.max(0, Math.round((currentProgress / totalSteps) * 100)));
      return {
        nextMilestone: m.milestone,
        nextMilestoneTitle: m.title,
        nextMilestoneReward: m.rewardValue,
        daysRemaining: m.milestone - currentStreak,
        progressPercent: percent
      };
    }
  }
  return {
    nextMilestone: 100,
    nextMilestoneTitle: "Max Milestone Reached! 👑",
    nextMilestoneReward: "Titan Legend Status",
    daysRemaining: 0,
    progressPercent: 100
  };
}

/**
 * 5. getStreakStatus
 * Returns current user streak metadata, today check-in status, milestone progress, and rewards.
 */
exports.getStreakStatus = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = context.auth.uid;
  const timezone = data && data.timezone ? data.timezone : "UTC";
  const todayDate = getAuthoritativeDateString(timezone);

  const streakRef = db.collection("users").doc(uid).collection("streak").doc("current");
  const streakDoc = await streakRef.get();

  let currentStreak = 0;
  let longestStreak = 0;
  let totalCheckIns = 0;
  let lastCheckInDate = null;
  let isCheckedInToday = false;

  if (streakDoc.exists) {
    const sData = streakDoc.data();
    currentStreak = sData.currentStreak || 0;
    longestStreak = sData.longestStreak || 0;
    totalCheckIns = sData.totalCheckIns || 0;
    lastCheckInDate = sData.lastCheckInDate || null;
    isCheckedInToday = (lastCheckInDate === todayDate);
  }

  // Retrieve user rewards
  const rewardsSnapshot = await db.collection("users").doc(uid).collection("streakRewards").get();
  const rewardsMap = {};
  rewardsSnapshot.forEach(doc => {
    rewardsMap[doc.id] = doc.data();
  });

  // Prepare full rewards list merging default milestones
  const rewardsList = STREAK_MILESTONES.map(m => {
    const userReward = rewardsMap[m.rewardId];
    let status = "LOCKED";
    if (userReward) {
      status = userReward.status || "UNLOCKED";
    } else if (currentStreak >= m.milestone) {
      status = "UNLOCKED";
    }

    return {
      rewardId: m.rewardId,
      milestone: m.milestone,
      rewardType: m.rewardType,
      rewardValue: m.rewardValue,
      title: m.title,
      description: m.description,
      status: status,
      claimedAt: userReward ? userReward.claimedAt : null
    };
  });

  const milestoneInfo = getNextMilestoneInfo(currentStreak);

  return {
    uid: uid,
    currentStreak: currentStreak,
    longestStreak: longestStreak,
    totalCheckIns: totalCheckIns,
    lastCheckInDate: lastCheckInDate,
    todayDate: todayDate,
    isCheckedInToday: isCheckedInToday,
    timezone: timezone,
    nextMilestoneInfo: milestoneInfo,
    rewards: rewardsList
  };
});

/**
 * 6. dailyCheckIn
 * Atomically performs daily check-in with streak calculation, history, and milestone unlocking.
 */
exports.dailyCheckIn = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = context.auth.uid;
  const timezone = data && data.timezone ? data.timezone : "UTC";
  const todayDate = getAuthoritativeDateString(timezone);
  const yesterdayDate = getYesterdayDateString(todayDate);

  const streakRef = db.collection("users").doc(uid).collection("streak").doc("current");
  const historyRef = db.collection("users").doc(uid).collection("streakHistory").doc(todayDate);

  let unlockedMilestone = null;
  let isNewCheckIn = false;
  let updatedCurrentStreak = 1;
  let updatedLongestStreak = 1;
  let updatedTotalCheckIns = 1;

  await db.runTransaction(async (transaction) => {
    const streakDoc = await transaction.get(streakRef);

    let currentStreak = 0;
    let longestStreak = 0;
    let totalCheckIns = 0;
    let lastCheckInDate = null;

    if (streakDoc.exists) {
      const sData = streakDoc.data();
      currentStreak = sData.currentStreak || 0;
      longestStreak = sData.longestStreak || 0;
      totalCheckIns = sData.totalCheckIns || 0;
      lastCheckInDate = sData.lastCheckInDate || null;
    }

    // Check if already checked in today
    if (lastCheckInDate === todayDate) {
      isNewCheckIn = false;
      updatedCurrentStreak = currentStreak;
      updatedLongestStreak = longestStreak;
      updatedTotalCheckIns = totalCheckIns;
      return; // Early return inside transaction
    }

    isNewCheckIn = true;

    // Calculate streak
    if (lastCheckInDate === yesterdayDate) {
      updatedCurrentStreak = currentStreak + 1;
    } else {
      // Missed a day or first check-in
      updatedCurrentStreak = 1;
    }

    updatedLongestStreak = Math.max(longestStreak, updatedCurrentStreak);
    updatedTotalCheckIns = totalCheckIns + 1;

    // 1. Write streak doc
    transaction.set(streakRef, {
      currentStreak: updatedCurrentStreak,
      longestStreak: updatedLongestStreak,
      totalCheckIns: updatedTotalCheckIns,
      lastCheckInAt: admin.firestore.FieldValue.serverTimestamp(),
      lastCheckInDate: todayDate,
      timezone: timezone,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    // 2. Write streak history doc
    transaction.set(historyRef, {
      date: todayDate,
      streakValue: updatedCurrentStreak,
      checkedInAt: admin.firestore.FieldValue.serverTimestamp(),
      activityType: "DAILY_CHECKIN",
      rewardClaimed: false
    });

    // 3. Check for unlocked milestone
    const matchingMilestone = STREAK_MILESTONES.find(m => m.milestone === updatedCurrentStreak);
    if (matchingMilestone) {
      const rewardRef = db.collection("users").doc(uid).collection("streakRewards").doc(matchingMilestone.rewardId);
      transaction.set(rewardRef, {
        rewardId: matchingMilestone.rewardId,
        milestone: matchingMilestone.milestone,
        rewardType: matchingMilestone.rewardType,
        rewardValue: matchingMilestone.rewardValue,
        title: matchingMilestone.title,
        description: matchingMilestone.description,
        status: "UNLOCKED",
        unlockedAt: admin.firestore.FieldValue.serverTimestamp(),
        claimedAt: null
      }, { merge: true });

      unlockedMilestone = {
        rewardId: matchingMilestone.rewardId,
        milestone: matchingMilestone.milestone,
        rewardType: matchingMilestone.rewardType,
        rewardValue: matchingMilestone.rewardValue,
        title: matchingMilestone.title,
        description: matchingMilestone.description
      };
    }
  });

  const milestoneInfo = getNextMilestoneInfo(updatedCurrentStreak);

  return {
    success: true,
    isNewCheckIn: isNewCheckIn,
    message: isNewCheckIn ? "Check-in successful! Streak increased!" : "Already checked in today!",
    currentStreak: updatedCurrentStreak,
    longestStreak: updatedLongestStreak,
    totalCheckIns: updatedTotalCheckIns,
    lastCheckInDate: todayDate,
    isCheckedInToday: true,
    unlockedMilestone: unlockedMilestone,
    nextMilestoneInfo: milestoneInfo
  };
});

/**
 * 7. getStreakHistory
 * Returns history docs for specified year and month.
 */
exports.getStreakHistory = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = context.auth.uid;
  const year = data.year || new Date().getFullYear();
  const month = data.month || (new Date().getMonth() + 1);

  const monthStr = month < 10 ? "0" + month : "" + month;
  const startPrefix = `${year}-${monthStr}-01`;
  const endPrefix = `${year}-${monthStr}-31`;

  const historySnapshot = await db.collection("users")
    .doc(uid)
    .collection("streakHistory")
    .where("date", ">=", startPrefix)
    .where("date", "<=", endPrefix)
    .get();

  const historyMap = {};
  historySnapshot.forEach(doc => {
    historyMap[doc.id] = doc.data();
  });

  return {
    year: year,
    month: month,
    history: historyMap
  };
});

/**
 * 8. claimStreakReward
 * Claims an unlocked milestone reward.
 */
exports.claimStreakReward = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = context.auth.uid;
  const rewardId = data.rewardId;

  if (!rewardId) {
    throw new functions.https.HttpsError("invalid-argument", "Reward ID is required.");
  }

  const rewardRef = db.collection("users").doc(uid).collection("streakRewards").doc(rewardId);
  const rewardDoc = await rewardRef.get();

  if (!rewardDoc.exists) {
    // Check if user reached streak milestone but doc hasn't been written yet
    const milestoneDef = STREAK_MILESTONES.find(m => m.rewardId === rewardId);
    if (!milestoneDef) {
      throw new functions.https.HttpsError("not-found", "Invalid reward ID.");
    }

    const streakDoc = await db.collection("users").doc(uid).collection("streak").doc("current").get();
    const currentStreak = (streakDoc.exists && streakDoc.data().currentStreak) || 0;

    if (currentStreak < milestoneDef.milestone) {
      throw new functions.https.HttpsError("failed-precondition", `Streak milestone of ${milestoneDef.milestone} days not yet reached.`);
    }

    // Auto-create unlocked reward doc
    await rewardRef.set({
      rewardId: milestoneDef.rewardId,
      milestone: milestoneDef.milestone,
      rewardType: milestoneDef.rewardType,
      rewardValue: milestoneDef.rewardValue,
      title: milestoneDef.title,
      description: milestoneDef.description,
      status: "CLAIMED",
      unlockedAt: admin.firestore.FieldValue.serverTimestamp(),
      claimedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return {
      success: true,
      rewardId: rewardId,
      status: "CLAIMED",
      rewardValue: milestoneDef.rewardValue,
      message: "Reward claimed successfully!"
    };
  }

  const rData = rewardDoc.data();
  if (rData.status === "CLAIMED") {
    return {
      success: true,
      rewardId: rewardId,
      status: "CLAIMED",
      alreadyClaimed: true,
      message: "Reward was already claimed!"
    };
  }

  await rewardRef.update({
    status: "CLAIMED",
    claimedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  return {
    success: true,
    rewardId: rewardId,
    status: "CLAIMED",
    rewardValue: rData.rewardValue,
    message: "Reward claimed successfully!"
  };
});

// ============================================================================
// PROFILE & USERNAME MANAGEMENT CALLABLE FUNCTIONS
// ============================================================================

/**
 * 9. checkUsernameAvailability
 * Checks if a requested username is available or belongs to the authenticated user.
 */
exports.checkUsernameAvailability = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = context.auth.uid;
  const rawUsername = (data && data.username) ? data.username : "";
  const normalized = rawUsername.toLowerCase().trim().replace(/[^a-z0-9_]/g, "");

  if (!normalized || normalized.length < 3 || normalized.length > 30) {
    return {
      available: false,
      normalizedUsername: normalized,
      reason: "Username must be 3-30 characters long using lowercase letters, numbers, and underscores."
    };
  }

  const docRef = db.collection("usernames").doc(normalized);
  const doc = await docRef.get();

  if (!doc.exists) {
    return { available: true, normalizedUsername: normalized };
  }

  const mappedUid = doc.data().uid;
  if (mappedUid === callerUid) {
    return { available: true, normalizedUsername: normalized, isCurrent: true };
  }

  return { available: false, normalizedUsername: normalized, reason: "Username is already taken." };
});

/**
 * 10. getMyProfile
 * Fetches authenticated user's profile from Firestore.
 */
exports.getMyProfile = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = context.auth.uid;
  const userDoc = await db.collection("users").doc(callerUid).get();

  if (!userDoc.exists) {
    throw new functions.https.HttpsError("not-found", "User profile not found.");
  }

  return { success: true, profile: userDoc.data() };
});

/**
 * 11. updateMyProfile
 * Atomically validates and updates profile information & username mapping.
 */
exports.updateMyProfile = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = context.auth.uid;
  const { fullName, username, bio, gender, dateOfBirth, location, avatarUrl } = data;

  if (fullName !== undefined && fullName !== null && fullName.trim().length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "Full Name cannot be empty.");
  }

  let normalizedUsername = null;
  if (username !== undefined && username !== null) {
    normalizedUsername = username.toLowerCase().trim().replace(/[^a-z0-9_]/g, "");
    if (!normalizedUsername || normalizedUsername.length < 3 || normalizedUsername.length > 30) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Username must be 3-30 characters long and contain only lowercase letters, numbers, or underscores."
      );
    }
  }

  await db.runTransaction(async (transaction) => {
    const userRef = db.collection("users").doc(callerUid);
    const userDoc = await transaction.get(userRef);

    const oldUsername = userDoc.exists ? userDoc.data().username : null;
    const oldNormalized = oldUsername ? oldUsername.toLowerCase().trim() : null;

    if (normalizedUsername) {
      const usernameRef = db.collection("usernames").doc(normalizedUsername);
      const usernameDoc = await transaction.get(usernameRef);

      if (usernameDoc.exists && usernameDoc.data().uid !== callerUid) {
        throw new functions.https.HttpsError("already-exists", `Username @${normalizedUsername} is already taken.`);
      }

      // If username changed, delete old mapping
      if (oldNormalized && oldNormalized !== normalizedUsername) {
        const oldUsernameRef = db.collection("usernames").doc(oldNormalized);
        transaction.delete(oldUsernameRef);
      }

      transaction.set(usernameRef, {
        uid: callerUid,
        createdAt: usernameDoc.exists && usernameDoc.data().createdAt ? usernameDoc.data().createdAt : admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }

    const updates = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    };

    if (fullName !== undefined && fullName !== null) updates.displayName = fullName.trim();
    if (username !== undefined && username !== null) {
      updates.username = normalizedUsername;
      updates.normalizedUsername = normalizedUsername;
    }
    if (bio !== undefined && bio !== null) updates.bio = bio.trim();
    if (gender !== undefined && gender !== null) updates.gender = gender;
    if (dateOfBirth !== undefined && dateOfBirth !== null) updates.dateOfBirth = dateOfBirth;
    if (location !== undefined && location !== null) updates.location = location.trim();
    if (avatarUrl !== undefined && avatarUrl !== null) updates.avatarUrl = avatarUrl;

    if (userDoc.exists) {
      transaction.update(userRef, updates);
    } else {
      updates.uid = callerUid;
      updates.createdAt = admin.firestore.FieldValue.serverTimestamp();
      transaction.set(userRef, updates, { merge: true });
    }
  });

  const updatedDoc = await db.collection("users").doc(callerUid).get();
  return { success: true, profile: updatedDoc.data() };
});

/**
 * 12. updateProfileImage
 * Updates profile avatar URL for authenticated user.
 */
exports.updateProfileImage = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = context.auth.uid;
  const { avatarUrl } = data;

  if (!avatarUrl) {
    throw new functions.https.HttpsError("invalid-argument", "Avatar URL is required.");
  }

  await db.collection("users").doc(callerUid).update({
    avatarUrl: avatarUrl,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  return { success: true, avatarUrl: avatarUrl };
});


