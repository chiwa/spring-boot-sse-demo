import { useRef, useState } from 'react';
import { publishBroadcast } from '../api.js';
import { ConnectedClients } from '../components/ConnectedClients.jsx';
import { Navigation } from '../components/Navigation.jsx';

export function BroadcastPage() {
  const inFlight = useRef(false);
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [feedback, setFeedback] = useState('');

  async function sendBroadcast(event) {
    event.preventDefault();
    if (inFlight.current || !message.trim()) return;
    inFlight.current = true;
    setSending(true);
    setFeedback('');

    try {
      const { sent } = await publishBroadcast(message.trim());
      setFeedback(sent ? `ส่งข้อความไปยัง ${sent} ไคลเอนต์แล้ว` : 'ไม่มีไคลเอนต์ที่รับข้อความในขณะนี้');
    } catch (error) {
      setFeedback(error.status === 400
        ? 'กรอกข้อความความยาว 1–500 ตัวอักษร'
        : 'ส่งข้อความไม่สำเร็จ ลองอีกครั้ง');
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  return (
    <main>
      <Navigation broadcast />
      <header>
        <h1>Broadcast</h1>
        <p className="intro">ส่งข้อความทันทีถึงไคลเอนต์ที่เชื่อมต่อช่อง Broadcast</p>
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
