import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminEmails, shouldGrantAdmin } from "./adminConfig";

test("default bootstrap admin is the configured example", () => {
  assert.deepEqual(parseAdminEmails(undefined), ["robinoxyzen@gmail.com"]);
  assert.equal(shouldGrantAdmin("RobinOxyzen@gmail.com", true, parseAdminEmails(undefined)), true);
  assert.equal(shouldGrantAdmin("robinoxyzen@gmail.com", false, parseAdminEmails(undefined)), false);
  assert.equal(shouldGrantAdmin("other@gmail.com", true, parseAdminEmails("a@x.com, b@y.com")), false);
});
