import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { defineString } from "firebase-functions/params";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import * as functionsV1 from "firebase-functions/v1";
import { setGlobalOptions } from "firebase-functions/v2";
import { parseAdminEmails, shouldGrantAdmin } from "./adminConfig";
import { createSmsProvider } from "./sms/provider";
import { reminderSms, transactionSms } from "./sms/templates";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

const adminEmails = defineString("ADMIN_EMAILS", {
  default: "robinoxyzen@gmail.com",
  description: "Comma-separated verified Google emails that receive the admin custom claim.",
});

function emailList(): string[] {
  return parseAdminEmails(process.env.ADMIN_EMAILS || adminEmails.value());
}

async function grantIfListed(uid: string, email: string | undefined, verified: boolean): Promise<boolean> {
  if (!shouldGrantAdmin(email, verified, emailList())) return false;
  await getAuth().setCustomUserClaims(uid, { admin: true });
  return true;
}

/** New Google users whose verified email is in ADMIN_EMAILS become admins. */
export const grantAdminOnCreate = functionsV1
  .region("asia-south1")
  .auth.user()
  .onCreate(async (user) => {
    await grantIfListed(user.uid, user.email, user.emailVerified);
  });

/** Existing users call this after sign-in. The decision is server-side; the client only reads the claim. */
export const refreshAdminClaim = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first");
  }
  const email = request.auth.token.email as string | undefined;
  const verified = request.auth.token.email_verified === true;
  const granted = await grantIfListed(request.auth.uid, email, verified);
  return { admin: granted };
});

async function notifyUser(uid: string, title: string, body: string, txId: string) {
  const tokensSnap = await getFirestore().collection(`users/${uid}/fcmTokens`).get();
  const tokens = tokensSnap.docs.map((doc) => String(doc.get("token") || "")).filter(Boolean);
  if (tokens.length === 0) return;
  const response = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: { title, body, txId, type: "payment_received" },
  });
  await Promise.all(
    response.responses.map(async (item, index) => {
      const code = item.error?.code || "";
      if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) {
        await tokensSnap.docs[index].ref.delete().catch(() => undefined);
      }
    }),
  );
}

async function sendSms(to: string, body: string): Promise<{ ok: boolean; error?: string }> {
  const provider = createSmsProvider({
    provider: process.env.SMS_PROVIDER,
    apiUrl: process.env.SMS_API_URL,
    apiKey: process.env.SMS_API_KEY,
    senderId: process.env.SMS_SENDER_ID,
  });
  if (!provider) return { ok: false, error: "sms_not_configured" };
  return provider.send({ to, body });
}

export const onTransactionCreated = onDocumentCreated(
  "users/{uid}/businesses/{businessId}/transactions/{txId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    const uid = event.params.uid;
    const direction = String(data.direction || "");
    const source = String(data.source || "");
    const amount = Number(data.amount || 0);
    const contact = String(data.contactName || "কাস্টমার");
    if (direction === "received" || source === "qr_confirm" || source === "webhook") {
      await notifyUser(uid, "পেমেন্ট সফল", `${amount} · ${contact}`, snap.id);
    }
    if (data.smsRequested !== true) return;
    const business = await getFirestore().doc(`users/${uid}/businesses/${event.params.businessId}`).get();
    const contactSnap = await getFirestore()
      .doc(`users/${uid}/businesses/${event.params.businessId}/contacts/${data.contactId}`)
      .get();
    const phone = String(contactSnap.get("phone") || "");
    if (!phone) {
      await snap.ref.update({ smsStatus: "no_phone" });
      return;
    }
    const text = transactionSms(
      String(business.get("name") || "দোকান"),
      direction,
      amount,
      Number(contactSnap.get("balance") || 0),
    );
    const result = await sendSms(phone, text);
    await snap.ref.update({
      smsStatus: result.ok ? "sent" : result.error || "failed",
      smsBody: text,
    });
  },
);

export const onReminderWritten = onDocumentWritten(
  "users/{uid}/businesses/{businessId}/reminders/{reminderId}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const data = after.data();
    if (!data || data.channel !== "gateway" || data.status !== "queued" || data.smsSent === true) return;
    const phone = String(data.phone || "");
    const text = String(data.message || reminderSms("দোকান", Number(data.amount || 0)));
    if (!phone) {
      await after.ref.update({ status: "no_phone" });
      return;
    }
    const result = await sendSms(phone, text);
    await after.ref.update({
      status: result.ok ? "sent" : "failed",
      smsSent: result.ok,
      smsError: result.error || "",
      sentAt: Date.now(),
    });
  },
);

/**
 * Future hook for an MFS or bank to tell the shopkeeper a customer paid them.
 * DISABLED unless PAYMENT_WEBHOOK_ENABLED=true.
 * This function never receives, holds, routes, or settles money. It only writes
 * the shopkeeper's own ledger row and sends "পেমেন্ট সফল".
 *
 * POST /paymentWebhook
 * Header: x-hisab-webhook-secret
 * Body: { uid, businessId, amount, contactId?, payerName?, note?, providerRef? }
 */
export const paymentWebhook = onRequest(async (req, res) => {
  if (process.env.PAYMENT_WEBHOOK_ENABLED !== "true") {
    res.status(403).json({
      ok: false,
      error: "webhook_disabled",
      hint: "Set PAYMENT_WEBHOOK_ENABLED=true and PAYMENT_WEBHOOK_SECRET. No money moves through this app.",
    });
    return;
  }
  const secret = process.env.PAYMENT_WEBHOOK_SECRET || "";
  if (!secret || req.header("x-hisab-webhook-secret") !== secret) {
    res.status(401).json({ ok: false, error: "unauthorized" });
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ ok: false, error: "method_not_allowed" });
    return;
  }
  const body = req.body || {};
  const uid = String(body.uid || "");
  const businessId = String(body.businessId || "");
  const amount = Number(body.amount || 0);
  if (!uid || !businessId || !(amount > 0)) {
    res.status(400).json({ ok: false, error: "uid_business_amount_required" });
    return;
  }
  const db = getFirestore();
  const now = Date.now();
  const contactId = String(body.contactId || "");
  const payer = String(body.payerName || "কাস্টমার");
  const tx = db.collection(`users/${uid}/businesses/${businessId}/transactions`).doc();
  await tx.set({
    contactId,
    contactName: payer,
    direction: "received",
    amount,
    description: String(body.note || "বাইরের পেমেন্ট নোটিশ"),
    items: [],
    dateMillis: now,
    photoBase64: "",
    smsRequested: false,
    source: "webhook",
    providerRef: String(body.providerRef || ""),
    deleted: false,
    createdAt: now,
  });
  if (contactId) {
    const contactRef = db.doc(`users/${uid}/businesses/${businessId}/contacts/${contactId}`);
    const contact = await contactRef.get();
    if (contact.exists) {
      await contactRef.update({
        balance: FieldValue.increment(-amount),
        updatedAt: now,
      });
    }
  }
  await db.collection(`users/${uid}/businesses/${businessId}/cashEntries`).add({
    kind: "qr_in",
    amount,
    note: payer,
    items: [],
    dateMillis: now,
    deleted: false,
    createdAt: now,
  });
  res.status(202).json({ ok: true, transactionId: tx.id });
});
