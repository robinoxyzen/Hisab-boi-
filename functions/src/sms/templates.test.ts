import assert from "node:assert/strict";
import test from "node:test";
import { reminderSms, transactionSms } from "./templates";

test("templates name the shop and do not include a payment link", () => {
  const text = transactionSms("খান স্টোর", "received", 50, 10);
  assert.match(text, /পেলাম/);
  assert.match(text, /খান স্টোর/);
  assert.equal(text.includes("http"), false);
  assert.match(reminderSms("খান স্টোর", 20), /তাগাদা/);
});
