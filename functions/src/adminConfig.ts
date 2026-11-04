/** Admin emails come only from ADMIN_EMAILS. There is no address baked into the source. */
export function parseAdminEmails(raw: string | undefined): string[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter((item) => item.length > 0);
}

/** Server-side only. The Android app must not grant admin by comparing emails. */
export function shouldGrantAdmin(email: string | undefined, emailVerified: boolean, list: string[]): boolean {
  if (!email || !emailVerified) return false;
  return list.includes(email.trim().toLowerCase());
}
