/** Single configuration default. Override with the ADMIN_EMAILS environment parameter. */
export const DEFAULT_ADMIN_EMAILS = "robinoxyzen@gmail.com";

export function parseAdminEmails(raw: string | undefined): string[] {
  const value = (raw && raw.trim()) || DEFAULT_ADMIN_EMAILS;
  return value
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter((item) => item.length > 0);
}

/** Server-side only. The Android app must not grant admin by comparing emails. */
export function shouldGrantAdmin(email: string | undefined, emailVerified: boolean, list: string[]): boolean {
  if (!email || !emailVerified) return false;
  return list.includes(email.trim().toLowerCase());
}
