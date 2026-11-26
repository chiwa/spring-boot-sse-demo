import { useState } from 'react';
import { normalizeInterval } from '../streamData.js';
import { ConnectedClients } from '../components/ConnectedClients.jsx';
import { Navigation } from '../components/Navigation.jsx';
import { StreamPanel } from '../components/StreamPanel.jsx';

export function ReceiverPage() {
  const [intervalInput, setIntervalInput] = useState('');
  const [intervalError, setIntervalError] = useState(false);
  const [appliedInterval, setAppliedInterval] = useState('');
  const endpoint = appliedInterval ? `/api/events?intervalSeconds=${appliedInterval}` : '/api/events';

  function applyInterval(event) {
    event.preventDefault();
    const result = normalizeInterval(intervalInput);
    setIntervalError(result === null);
    if (result !== null) setAppliedInterval(result);
  }

  return (
    <main>
      <Navigation />
      <header>
        <p className="stack">SPRING BOOT <span>→</span> REACT</p>
        <h1>Server-Sent Events</h1>
        <p className="intro">เชื่อมต่อช่องข้อความตามเวลาและช่อง Broadcast อัตโนมัติเมื่อเปิดหน้านี้</p>
      </header>

      <StreamPanel eventName="tick" title="ข้อความตามเวลา" endpoint={endpoint}>
        <form className="interval-form" onSubmit={applyInterval} noValidate>
          <label>
            ส่งทุกกี่วินาที
            <input type="text" inputMode="numeric" placeholder="1" value={intervalInput}
              aria-invalid={intervalError}
              aria-describedby={intervalError ? 'interval-error' : undefined}
              onChange={(event) => {
                setIntervalInput(event.target.value);
                setIntervalError(false);
              }} />
          </label>
          <button type="submit">ใช้ช่วงเวลานี้</button>
          {intervalError && <span id="interval-error" className="interval-error" role="alert">กรอกช่วงเวลาเป็นจำนวนเต็มบวกไม่เกิน 2,147,483,647</span>}
        </form>
      </StreamPanel>
      <StreamPanel eventName="broadcast" title="ข้อความ Broadcast" endpoint="/api/broadcast/events" />
      <ConnectedClients />
    </main>
  );
}
