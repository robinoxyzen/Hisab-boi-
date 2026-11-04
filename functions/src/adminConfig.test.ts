import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminEmails, shouldGrantAdmin } from "./adminConfig";

test("admin emails come only from the provided list", () => {
  assert.deepEqual(parseAdminEmails(undefined), []);
  assert.deepEqual(parseAdminEmails("   "), []);
  const list = parseAdminEmails("Admin@example.com, second@example.com");
  assert.deepEqual(list, ["admin@example.com", "second@example.com"]);
  assert.equal(shouldGrantAdmin("Admin@example.com", true, list), true);
  assert.equal(shouldGrantAdmin("admin@example.com", false, list), false);
  assert.equal(shouldGrantAdmin("other@gmail.com", true, parseAdminEmails("a@x.com, b@y.com")), false);
});
