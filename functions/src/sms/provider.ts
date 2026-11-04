export interface OutboundSms {
  to: string;
  body: string;
}

export interface SmsResult {
  ok: boolean;
  id?: string;
  error?: string;
}

/** Pluggable Bangladeshi SMS gateway. The API key always comes from the environment. */
export interface SmsProvider {
  readonly name: string;
  send(message: OutboundSms): Promise<SmsResult>;
}

export interface SmsEnv {
  provider?: string;
  apiUrl?: string;
  apiKey?: string;
  senderId?: string;
}

export function createSmsProvider(env: SmsEnv): SmsProvider | null {
  if (!env.apiKey || !env.apiUrl) return null;
  const style = (env.provider || "bulksmsbd").toLowerCase();
  return new HttpSmsProvider(style, env.apiUrl, env.apiKey, env.senderId || "HISABBOI");
}

class HttpSmsProvider implements SmsProvider {
  constructor(
    readonly name: string,
    private readonly url: string,
    private readonly apiKey: string,
    private readonly senderId: string,
  ) {}

  async send(message: OutboundSms): Promise<SmsResult> {
    const headers: Record<string, string> = {};
    let body: string;
    if (this.name === "sslwireless") {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify({
        api_token: this.apiKey,
        sid: this.senderId,
        msisdn: message.to,
        sms: message.body,
        csms_id: String(Date.now()),
      });
    } else if (this.name === "generic") {
      headers["Content-Type"] = "application/json";
      headers.Authorization = `Bearer ${this.apiKey}`;
      body = JSON.stringify({ to: message.to, message: message.body, sender: this.senderId });
    } else {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      body = new URLSearchParams({
        api_key: this.apiKey,
        senderid: this.senderId,
        number: message.to,
        message: message.body,
      }).toString();
    }
    const response = await fetch(this.url, { method: "POST", headers, body });
    const text = await response.text();
    if (!response.ok) return { ok: false, error: text.slice(0, 300) };
    return { ok: true, id: text.slice(0, 120) };
  }
}
