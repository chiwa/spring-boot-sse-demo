export function Navigation({ broadcast = false }) {
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
