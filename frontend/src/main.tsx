import { StrictMode, Suspense, lazy } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { EmbedPage } from './embed/EmbedPage';
import { embedParams } from './embed/api';
import './styles.css';

// loaded only outside embed mode: the Polarion embed page does not need Sirius' application (about 4.5 MB)
const SysArchApplication = lazy(() => import('./application/SysArchApplication').then((m) => ({ default: m.SysArchApplication })));

// One interface (P4 T3): Sirius Web's own application hosts SysArch — its project browser and diagram editor, with the
// shell's pages contributed as the route /sysarch (application/SysArchApplication.tsx).
// Embedding in Polarion stays as it was: ?embed=1 without a view is the landing page a Polarion page frames, and the
// shell's own pages with ?embed=1&view=… open in a compact frame, both without Sirius' application around them.
const search = new URLSearchParams(window.location.search);
const embed = embedParams(window.location.search);
if (embed) {
  document.body.classList.add('embedded');
}

createRoot(document.getElementById('root')!).render(
  embed ? <StrictMode>{search.has('view') ? <App /> : <EmbedPage params={embed} />}</StrictMode> : (
    <Suspense fallback={null}>
      <SysArchApplication />
    </Suspense>
  ),
);
