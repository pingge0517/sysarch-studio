import { useEffect, useState } from 'react';
import type { Me } from '../identity/auth';
import { fetchMe } from '../identity/auth';
import type { EmbedParams, EmbedTarget } from './api';
import { fetchTarget, links } from './api';

/**
 * The page a Polarion page frames (?embed=1&polarionProject=…&workItem=…): who is signed in, the model of the Polarion
 * project and, for a Work Item, the element bound to it, with links into the model. Signing in happened on the way
 * here (Polarion single sign-on); without it the page says how to get in.
 */
export function EmbedPage({ params }: { params: EmbedParams }) {
  const [me, setMe] = useState<Me | null>(null);
  const [target, setTarget] = useState<EmbedTarget | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMe().then(setMe, () => setMe(null));
    if (!params.polarionProject) {
      setError('缺少 polarionProject 参数');
      return;
    }
    fetchTarget(params).then(setTarget, (e: Error) => setError(e.message));
  }, [params]);

  return (
    <main className="embed">
      <header className="embed-header">
        <strong>SysArch 架构模型</strong>
        <span className="embed-user">{me?.user ? `${me.name ?? me.user}` : '未登录'}</span>
      </header>
      {error && (
        <p className="error">
          {error}
          {!me?.user && '（请从 Polarion 的架构页面重新打开，以便自动登录）'}
        </p>
      )}
      {target && (
        <section>
          <p>
            Polarion 项目 <code>{target.polarionProject}</code> 的模型：<strong>{target.projectName}</strong>
          </p>
          {target.workItemId && (
            <p>
              Work Item <code>{target.workItemId}</code>
              {target.elementId ? (
                <>
                  {' '}
                  绑定到元素 <code>{target.elementId}</code>
                  {target.linkState && target.linkState !== 'ACTIVE' && <span className="warning">（绑定状态：{target.linkState}）</span>}
                </>
              ) : null}
            </p>
          )}
          {target.note && <p className="warning">{target.note}</p>}
          <nav className="embed-links">
            {links(target).map((link) => (
              <a key={link.href} href={link.href} target="_self">
                {link.label}
              </a>
            ))}
          </nav>
        </section>
      )}
    </main>
  );
}
