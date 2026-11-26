import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BroadcastPage } from './pages/BroadcastPage.jsx';
import { ReceiverPage } from './pages/ReceiverPage.jsx';
import './styles.css';

const page = window.location.pathname.replace(/\/$/, '') === '/broadcast' ? <BroadcastPage /> : <ReceiverPage />;
createRoot(document.getElementById('root')).render(<StrictMode>{page}</StrictMode>);
