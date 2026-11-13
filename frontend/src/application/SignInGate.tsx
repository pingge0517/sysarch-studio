import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { Me } from '../identity/auth';
import { fetchMe, needsSignIn } from '../identity/auth';
import { SignIn } from '../identity/SignIn';

/**
 * P4 T8 (security finding F4): in polarion mode the backend answers reads only for a signed-in user, so Sirius'
 * application would show nothing but errors. Until someone is signed in this shows the sign-in page instead; arriving
 * through Polarion single sign-on (the extension's page, the Work Item panel) signs in on the way.
 */
export function SignInGate({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  const reload = useCallback(() => {
    fetchMe().then(
      (m) => {
        setMe(m);
        setFailed(null);
      },
      (e: Error) => setFailed(e.message),
    );
  }, []);

  useEffect(reload, [reload]);

  if (failed) {
    return (
      <main className="signin-gate">
        <h1>SysArch Studio</h1>
        <p className="error">无法连接后端：{failed}</p>
      </main>
    );
  }
  if (me === null) {
    return null;
  }
  if (needsSignIn(me)) {
    return (
      <main className="signin-gate">
        <h1>SysArch Studio</h1>
        <p>请先登录。从 Polarion 中的 SysArch 页面或工作项面板进入时会自动登录；也可以在这里使用 Polarion 个人访问令牌登录。</p>
        <div className="signin-gate-form">
          <SignIn me={me} grant={null} onChange={reload} />
        </div>
      </main>
    );
  }
  return <>{children}</>;
}
