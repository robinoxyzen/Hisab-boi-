import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminEmails, shouldGrantAdmin } from "./adminConfig";

test("default bootstrap admin is the configured example", () => {
  assert.deepEqual(parseAdminEmails(undefined), ["robindotx@gmail.com"]);
  assert.equal(shouldGrantAdmin("RobinDotx@gmail.com", true, parseAdminEmails(undefined)), true);
  assert.equal(shouldGrantAdmin("robindotx@gmail.com", false, parseAdminEmails(undefined)), false);
  assert.equal(shouldGrantAdmin("other@gmail.com", true, parseAdminEmails("a@x.com, b@y.com")), false);
});
