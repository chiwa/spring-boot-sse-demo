import { useEffect, useState } from 'react';
import { readEventMessage } from '../streamData.js';

export function useEventStream(endpoint, eventName) {
  const [status, setStatus] = useState('connecting');
  const [latest, setLatest] = useState(null);
  const enabled = status !== 'closed' && status !== 'error';

  useEffect(() => {
    if (!enabled) return;

    function updateStatus(next) {
      setStatus((current) => current === 'closed' || current === 'error' ? current : next);
    }

    updateStatus('connecting');
    const source = new EventSource(endpoint);
    source.onopen = () => updateStatus('open');
    source.onerror = () => {
      updateStatus(source.readyState === EventSource.CLOSED ? 'error' : 'reconnecting');
    };
    source.addEventListener(eventName, (event) => {
      const message = readEventMessage(event.data);
      if (message !== null) setLatest({ message, receivedAt: new Date() });
    });

    return () => source.close();
  }, [enabled, endpoint, eventName]);

  function toggleConnection() {
    setStatus((current) => current === 'closed' || current === 'error' ? 'connecting' : 'closed');
  }

  return { enabled, status, latest, toggleConnection };
}
