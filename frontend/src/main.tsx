import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { EmbedPage } from './embed/EmbedPage';
import { embedParams } from './embed/api';
import './styles.css';

// Embedding in Polarion: ?embed=1 without a view is the landing page a Polarion page frames; the shell's own pages
// (?embed=1&view=…) are the usual ones, in a compact frame.
const embed = embedParams(window.location.search);
const viewRequested = new URLSearchParams(window.location.search).has('view');
if (embed) {
  document.body.classList.add('embedded');
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>{embed && !viewRequested ? <EmbedPage params={embed} /> : <App />}</StrictMode>,
);
