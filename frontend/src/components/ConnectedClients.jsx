import { useEffect, useState } from 'react';
import { fetchClients } from '../api.js';

const clientTimeFormat = new Intl.DateTimeFormat('th-TH', {
  dateStyle: 'short',
  timeStyle: 'medium',
});

export function ConnectedClients() {
  const [clients, setClients] = useState(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let timer;

    async function refresh() {
      try {
        setClients(await fetchClients(controller.signal));
        setUnavailable(false);
      } catch {
        if (!controller.signal.aborted) setUnavailable(true);
      } finally {
        if (!controller.signal.aborted) timer = setTimeout(refresh, 2000);
      }
    }

    refresh();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, []);

  return (
    <section className="panel clients" aria-labelledby="clients-title">
      <h2 id="clients-title">ไคลเอนต์ที่เชื่อมต่อ{clients && !unavailable ? ` (${clients.length})` : ''}</h2>
      {unavailable && <p>โหลดรายชื่อไคลเอนต์ไม่ได้ กำลังลองใหม่</p>}
      {!unavailable && clients === null && <p>กำลังโหลดรายชื่อไคลเอนต์</p>}
      {!unavailable && clients?.length === 0 && <p>ยังไม่มีไคลเอนต์ที่เชื่อมต่อ</p>}
      {!unavailable && clients?.length > 0 && (
        <div className="clients-table">
          <table>
            <thead>
              <tr><th scope="col">Client ID</th><th scope="col">ช่อง</th><th scope="col">ช่วงเวลาส่ง</th><th scope="col">เชื่อมต่อเมื่อ</th></tr>
            </thead>
            <tbody>
              {clients.map((client) => (
                <tr key={client.id}>
                  <td><code>{client.id}</code></td>
                  <td>{client.channel === 'broadcast' ? 'Broadcast' : 'ตามเวลา'}</td>
                  <td>{client.intervalSeconds == null ? '—' : `${client.intervalSeconds} วินาที`}</td>
                  <td><time dateTime={client.connectedAt}>{clientTimeFormat.format(new Date(client.connectedAt))}</time></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
