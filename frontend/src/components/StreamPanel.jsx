import { useEventStream } from '../hooks/useEventStream.js';

const statusLabels = {
  connecting: 'กำลังเชื่อมต่อ',
  open: 'เชื่อมต่อแล้ว',
  reconnecting: 'กำลังเชื่อมต่อใหม่',
  error: 'เชื่อมต่อไม่สำเร็จ',
  closed: 'ตัดการเชื่อมต่อแล้ว',
};

const timeFormat = new Intl.DateTimeFormat('th-TH', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

export function StreamPanel({ eventName, title, endpoint, children }) {
  const { enabled, status, latest, toggleConnection } = useEventStream(endpoint, eventName);

  return (
    <section className="panel stream-panel" aria-labelledby={`${eventName}-title`}>
      <h2 id={`${eventName}-title`}>{title}</h2>
      <div className="stream-controls">
        <div className="connection">
          <div className="status" role="status" data-state={status}>
            <span aria-hidden="true" />
            {statusLabels[status]}
          </div>
          <code>GET {endpoint}</code>
        </div>
        <button className="primary" type="button" onClick={toggleConnection}>
          {enabled ? 'ตัดการเชื่อมต่อ' : 'เชื่อมต่อ'}
        </button>
      </div>
      {children}
      <div className="latest">
        {latest ? (
          <>
            <p className="latest-label">ได้รับข้อความล่าสุดเมื่อ</p>
            <time className="latest-time" dateTime={latest.receivedAt.toISOString()}>
              {timeFormat.format(latest.receivedAt)}
            </time>
            <p className="latest-message" lang={eventName === 'tick' ? 'en' : undefined}>{latest.message}</p>
          </>
        ) : (
          <p className="latest-empty">ยังไม่ได้รับข้อความ</p>
        )}
      </div>
    </section>
  );
}
