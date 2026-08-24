const functions = require("firebase-functions/v1");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const TOPIC_EMERGENCY = "emergency_alerts";
const TOPIC_ANNOUNCEMENTS = "announcements";

async function sendToTopic(topic, title, body, type, id) {
  const message = {
    topic,
    notification: {
      title,
      body,
    },
    data: {
      type,
      title,
      body,
      id: id || "",
    },
    android: {
      priority: "high",
      notification: {
        channelId: type === "emergency_alert" ? "emergency_alerts" : "announcements",
        sound: "default",
      },
    },
  };

  await getMessaging().send(message);
}

exports.notifyOnEmergencyAlert = functions.firestore
  .document("emergency_alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return;

    const title = data.title || "Emergency Alert";
    const body = data.content || "A new emergency alert was posted.";
    await sendToTopic(
      TOPIC_EMERGENCY,
      title,
      body,
      "emergency_alert",
      context.params.alertId,
    );
  });

exports.notifyOnAnnouncementCreated = functions.firestore
  .document("announcements/{announcementId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return;
    if (data.status !== "APPROVED") return;

    const title = data.title || "New Announcement";
    const body = data.content || "A new announcement was posted.";
    await sendToTopic(
      TOPIC_ANNOUNCEMENTS,
      title,
      body,
      "announcement",
      context.params.announcementId,
    );
  });

exports.notifyOnAnnouncementApproved = functions.firestore
  .document("announcements/{announcementId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    if (!before || !after) return;
    if (before.status === "APPROVED" || after.status !== "APPROVED") return;

    const title = after.title || "New Announcement";
    const body = after.content || "A new announcement was posted.";
    await sendToTopic(
      TOPIC_ANNOUNCEMENTS,
      title,
      body,
      "announcement",
      context.params.announcementId,
    );
  });
