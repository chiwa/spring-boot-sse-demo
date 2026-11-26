async function requestJson(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return response.json();
}

export function fetchClients(signal) {
  return requestJson('/api/clients', { signal });
}

export function publishBroadcast(message) {
  return requestJson('/api/broadcast', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  });
}
