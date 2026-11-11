import { useEffect, useState } from 'react';
import type { InteractionInfo } from './api';
import { listInteractions, sequenceUrl } from './api';

/**
 * 时序图（只读）: probe A's fallback (spec P3 §6.1 No-Go). Interactions are edited in Sirius' model explorer (Interaction,
 * its Lifelines and Messages in time order); this page shows each as a picture and offers SVG / PNG downloads.
 */
export function SequencePage({ project }: { project: string }) {
  const [interactions, setInteractions] = useState<InteractionInfo[] | null>(null);
  const [selected, setSelected] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [stamp, setStamp] = useState(0);

  useEffect(() => {
    setError(null);
    listInteractions(project).then(
      (list) => {
        setInteractions(list);
        setSelected((current) => (list.some((i) => i.id === current) ? current : list[0]?.id ?? ''));
      },
      (e: Error) => setError(e.message),
    );
  }, [project, stamp]);

  return (
    <div className="sequence">
      <section>
        <h2>时序图（只读）</h2>
        <p className="hint">在 Sirius 的模型浏览器里建交互（Interaction）、生命线（Lifeline）和按时间顺序排列的消息（Message），这里显示为时序图，可下载 SVG 或 PNG。画布上不能直接编辑时序图（探针 A 的结论）。</p>
        {error && <div className="notice error" role="alert">{error}</div>}
        {interactions && interactions.length === 0 && <p className="hint">这个项目还没有交互。</p>}
        {interactions && interactions.length > 0 && (
          <div className="row">
            <label>
              交互
              <select value={selected} onChange={(e) => setSelected(e.target.value)} aria-label="交互">
                {interactions.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name ?? i.id}（{i.lifelines} 条生命线，{i.messages} 条消息）
                  </option>
                ))}
              </select>
            </label>
            <button type="button" onClick={() => setStamp((s) => s + 1)}>刷新</button>
            {selected && <a href={sequenceUrl(project, selected, 'svg')} download>下载 SVG</a>}
            {selected && <a href={sequenceUrl(project, selected, 'png')} download>下载 PNG</a>}
          </div>
        )}
      </section>
      {selected && <img className="sequence-picture" src={`${sequenceUrl(project, selected, 'svg')}?v=${stamp}`} alt="时序图" />}
    </div>
  );
}
