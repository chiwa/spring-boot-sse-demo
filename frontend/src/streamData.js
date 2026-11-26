export function normalizeInterval(input) {
  if (input === '') return '';
  if (/[^0-9]/.test(input)) return null;
  const seconds = Number(input);
  return Number.isInteger(seconds) && seconds >= 1 && seconds <= 2147483647 ? String(seconds) : null;
}

export function readEventMessage(data) {
  try {
    const payload = JSON.parse(data);
    return typeof payload?.message === 'string' ? payload.message : null;
  } catch {
    return null;
  }
}
