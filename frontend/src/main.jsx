import React, { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const statusLabels = {
  connecting: 'กำลังเชื่อมต่อ',
  open: 'เชื่อมต่อแล้ว',
  reconnecting: 'กำลังเชื่อมต่อใหม่',
  closed: 'ตัดการเชื่อมต่อแล้ว',
};

const timeFormat = new Intl.DateTimeFormat('th-TH', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const clientTimeFormat = new Intl.DateTimeFormat('th-TH', {
  dateStyle: 'short',
  timeStyle: 'medium',
});

function ConnectedClients() {
  const [clients, setClients] = useState(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let timer;

    async function refresh() {
      try {
        const response = await fetch('/api/clients', { signal: controller.signal });
        if (!response.ok) throw new Error('Failed to load connected clients');
        setClients(await response.json());
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
              <tr><th scope="col">Client ID</th><th scope="col">ช่วงเวลาส่ง</th><th scope="col">เชื่อมต่อเมื่อ</th></tr>
            </thead>
            <tbody>
              {clients.map((client) => (
                <tr key={client.id}>
                  <td><code>{client.id}</code></td>
                  <td>{client.intervalSeconds} วินาที</td>
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

function Navigation({ broadcast = false }) {
  return (
    <nav className="navigation" aria-label="หน้าของเดโม">
      <a href="/" aria-current={broadcast ? undefined : 'page'}>รับข้อความ</a>
      {broadcast ? (
        <a href="/broadcast" aria-current="page">Broadcast</a>
      ) : (
        <a className="broadcast-link" href="/broadcast" target="_blank" rel="noopener">
          ส่ง Broadcast (เปิดแท็บใหม่)
        </a>
      )}
    </nav>
  );
}

function BroadcastPage() {
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [feedback, setFeedback] = useState('');

  async function sendBroadcast(event) {
    event.preventDefault();
    setSending(true);
    setFeedback('');

    try {
      const response = await fetch('/api/broadcast', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: message.trim() }),
      });
      if (!response.ok) throw new Error('Broadcast request failed');
      const { sent } = await response.json();
      setFeedback(sent ? `ส่งข้อความไปยัง ${sent} ไคลเอนต์แล้ว` : 'ไม่มีไคลเอนต์ที่รับข้อความในขณะนี้');
    } catch {
      setFeedback('ส่งข้อความไม่สำเร็จ ลองอีกครั้ง');
    } finally {
      setSending(false);
    }
  }

  return (
    <main>
      <Navigation broadcast />
      <header>
        <h1>Broadcast</h1>
        <p className="intro">ส่งข้อความทันทีถึงไคลเอนต์ SSE ที่กำลังเชื่อมต่อ</p>
      </header>
      <form className="panel broadcast-form" onSubmit={sendBroadcast}>
        <label htmlFor="broadcast-message">ข้อความ</label>
        <textarea id="broadcast-message" rows="4" maxLength={500} required
          value={message} onChange={(event) => setMessage(event.target.value)} />
        <button className="primary" disabled={sending || !message.trim()}>
          {sending ? 'กำลังส่ง…' : 'ส่งข้อความ'}
        </button>
        <p className="broadcast-feedback" role="status">{feedback}</p>
      </form>
      <ConnectedClients />
    </main>
  );
}

function App() {
  const [enabled, setEnabled] = useState(false);
  const [status, setStatus] = useState('closed');
  const [intervalInput, setIntervalInput] = useState('');
  const [interval, setInterval] = useState('');
  const [latest, setLatest] = useState(null);
  const endpoint = interval ? `/api/events?intervalSeconds=${interval}` : '/api/events';

  useEffect(() => {
    if (!enabled) return;

    const source = new EventSource(endpoint);
    source.onopen = () => setStatus('open');
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        setEnabled(false);
        setStatus('closed');
      } else {
        setStatus('reconnecting');
      }
    };
    const receiveMessage = (event) => {
      const { message } = JSON.parse(event.data);
      setLatest({ message, receivedAt: new Date(), broadcast: event.type === 'broadcast' });
    };
    source.addEventListener('tick', receiveMessage);
    source.addEventListener('broadcast', receiveMessage);

    return () => source.close();
  }, [enabled, endpoint]);

  function toggleConnection() {
    setEnabled(!enabled);
    setStatus(enabled ? 'closed' : 'connecting');
  }

  function applyInterval(event) {
    event.preventDefault();
    const nextInterval = intervalInput === '' ? '' : String(Number(intervalInput));
    if (enabled && nextInterval !== interval) setStatus('connecting');
    setInterval(nextInterval);
  }

  return (
    <main>
      <Navigation />
      <header>
        <p className="stack">SPRING BOOT <span>→</span> REACT</p>
        <h1>Server-Sent Events</h1>
        <p className="intro">รับข้อความจากเซิร์ฟเวอร์ตามช่วงเวลาที่กำหนด</p>
      </header>

      <section className="panel" aria-label="การเชื่อมต่อ">
        <div className="connection">
          <div className="status" role="status" data-state={status}>
            <span aria-hidden="true" />
            {statusLabels[status]}
          </div>
          <code>GET {endpoint}</code>
        </div>
        <button className="primary" onClick={toggleConnection}>
          {enabled ? 'ตัดการเชื่อมต่อ' : 'เชื่อมต่อ'}
        </button>
      </section>

      <form className="panel interval-form" onSubmit={applyInterval}>
        <label>
          ส่งทุกกี่วินาที
          <input type="number" min="1" max="2147483647" step="1" placeholder="1" value={intervalInput}
            onChange={(event) => setIntervalInput(event.target.value)} />
        </label>
        <button type="submit">ใช้ช่วงเวลานี้</button>
      </form>

      <section className="panel latest" aria-labelledby="latest-title">
        <h2 id="latest-title">ได้รับข้อความล่าสุดเมื่อ</h2>
        {latest ? (
          <>
            <time className="latest-time" dateTime={latest.receivedAt.toISOString()}>
              {timeFormat.format(latest.receivedAt)}
            </time>
            <p className="latest-message" lang={latest.broadcast ? undefined : 'en'}>{latest.message}</p>
            {latest.broadcast && <p className="broadcast-kind">Broadcast</p>}
          </>
        ) : (
          <p className="latest-empty">ยังไม่ได้รับข้อความ</p>
        )}
      </section>

      <ConnectedClients />
    </main>
  );
}

const page = window.location.pathname.replace(/\/$/, '') === '/broadcast' ? <BroadcastPage /> : <App />;
createRoot(document.getElementById('root')).render(<StrictMode>{page}</StrictMode>);
