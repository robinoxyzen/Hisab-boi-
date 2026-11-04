const digits = ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"];

export function banglaAmount(amount: number): string {
  const fixed = Math.abs(amount).toFixed(2);
  const text = fixed.replace(/[0-9]/g, (d) => digits[Number(d)]);
  return `${amount < 0 ? "-" : ""}৳${text}`;
}

export function transactionSms(business: string, direction: string, amount: number, balance: number): string {
  const dir = direction === "received" ? "পেলাম" : "দিলাম";
  const side = balance >= 0 ? "পাবো" : "দেবো";
  return `${business}: ${dir} ${banglaAmount(amount)}। বর্তমান ব্যালেন্স ${banglaAmount(Math.abs(balance))} (${side})। — হিসাব বই`;
}

export function reminderSms(business: string, amount: number): string {
  return `${business}: বকেয়া তাগাদা। আপনার কাছে পাবো ${banglaAmount(amount)}। অনুগ্রহ করে পরিশোধ করুন। — হিসাব বই`;
}
