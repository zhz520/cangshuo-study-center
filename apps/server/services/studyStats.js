// DATE columns are serialized as local calendar keys, never as UTC midnight instants.
function dayKey(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}
function consecutiveDays(days, today = dayKey()) {
  const keys = [...new Set(days)].sort().reverse();
  const previous = new Date(today + 'T12:00:00+08:00');
  previous.setUTCDate(previous.getUTCDate() - 1);
  let expected = keys[0] === today ? today : dayKey(previous);
  let streak = 0;
  for (const key of keys) {
    if (key !== expected) break;
    streak++;
    const date = new Date(expected + 'T12:00:00+08:00');
    date.setUTCDate(date.getUTCDate() - 1);
    expected = dayKey(date);
  }
  return streak;
}
module.exports = { dayKey, consecutiveDays };
