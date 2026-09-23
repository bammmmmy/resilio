const functions = require("firebase-functions/v1");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const TOPIC_EMERGENCY = "emergency_alerts";
const TOPIC_ANNOUNCEMENTS = "announcements";
const WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=14.5845&longitude=121.1754" +
  "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_gusts_10m" +
  "&hourly=temperature_2m,weather_code,precipitation,precipitation_probability,wind_speed_10m" +
  "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset" +
  "&past_days=1&forecast_days=7&timezone=Asia%2FSingapore";

async function createWeatherSnapshot() {
  const response = await fetch(WEATHER_URL);
  if (!response.ok) throw new Error(`Weather request failed: ${response.status}`);
  const payload = await response.json();
  const current = payload.current;
  const hourly = payload.hourly;
  const currentIndex = hourly.time.findIndex((time) => time === current.time);
  const start = Math.max(0, currentIndex - 23);
  return {
    current,
    hourly,
    daily: payload.daily,
    rain24h: currentIndex >= 0
      ? hourly.precipitation.slice(start, currentIndex + 1).reduce((sum, value) => sum + value, 0)
      : 0,
    precipProbability: currentIndex >= 0
      ? hourly.precipitation_probability[currentIndex] || 0
      : 0,
  };
}

exports.getWeatherSnapshot = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
  if (req.method === "OPTIONS") { res.status(204).send(""); return; }
  try {
    const reference = getFirestore().collection("weather_snapshots").doc("current");
    const snapshot = await reference.get();
    let data = snapshot.exists ? snapshot.data() : null;
    if (!data?.current || !data?.hourly || !data?.daily) {
      data = await createWeatherSnapshot();
      await reference.set({ ...data, fetchedAt: FieldValue.serverTimestamp() });
    }
    res.status(200).json(data);
  } catch (error) {
    res.status(502).json({ error: "Weather data is temporarily unavailable." });
  }
});

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

exports.refreshWeatherSnapshot = functions.pubsub
  .schedule("every 10 minutes")
  .timeZone("Asia/Manila")
  .onRun(async () => {
    const data = await createWeatherSnapshot();
    await getFirestore().collection("weather_snapshots").doc("current").set({
      ...data,
      fetchedAt: FieldValue.serverTimestamp(),
    });
  });

exports.notifyOnEmergencyAlert = functions.firestore
  .document("emergency_alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return;
    // Only notify immediately if it's already APPROVED (e.g. created by Chairman)
    if (data.status !== "APPROVED") return;

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

exports.notifyOnEmergencyAlertApproved = functions.firestore
  .document("emergency_alerts/{alertId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    if (!before || !after) return;
    // Only notify when status changes to APPROVED
    if (before.status === "APPROVED" || after.status !== "APPROVED") return;

    const title = after.title || "Emergency Alert";
    const body = after.content || "A new emergency alert was posted.";
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
