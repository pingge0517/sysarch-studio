import { useState } from 'react';
import type { AccessGrant, Me } from './auth';
import { badge, login, loginDev, logout, parseRoles } from './auth';

/**
 * The header's sign-in (P2 T8). Polarion mode: the user's Polarion personal access token, sent once to the backend,
 * which verifies it with Polarion and sets the session cookie; the field is cleared right away. Dev mode: a name and,
 * optionally, Polarion roles (e.g. sysarch/project_reviewer).
 */
export function SignIn({ me, grant, onChange }: { me: Me | null; grant: AccessGrant | null; onChange: () => void }) {
  const [open, setOpen] = useState(false);
  const [secret, setSecret] = useState('');
  const [roles, setRoles] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (me?.user) {
    return (
      <span className="signin" title={grant ? `在本项目的权限：${grant.permissions.join('、')}` : undefined}>
        {badge(me, grant)}
        {me.mode === 'dev' && <em className="dev">dev</em>}
        <button
          type="button"
          onClick={async () => {
            await logout();
            onChange();
          }}
        >
          退出
        </button>
      </span>
    );
  }

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      if (me?.mode === 'dev') {
        await loginDev(secret.trim(), parseRoles(roles));
      } else {
        await login(secret.trim());
      }
      setSecret('');
      setOpen(false);
      onChange();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <span className="signin">
      {badge(me, null)}
      {!open && (
        <button type="button" onClick={() => setOpen(true)}>
          登录
        </button>
      )}
      {open && (
        <form
          className="signin-form"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          {me?.mode === 'dev' ? (
            <>
              <input placeholder="名字（dev 模式）" value={secret} onChange={(e) => setSecret(e.target.value)} autoFocus />
              <input placeholder="Polarion 角色，可空，如 sysarch/project_reviewer" value={roles} onChange={(e) => setRoles(e.target.value)} />
            </>
          ) : (
            <input
              type="password"
              autoComplete="off"
              placeholder="Polarion 个人访问令牌"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              autoFocus
            />
          )}
          <button type="submit" disabled={busy || !secret.trim()}>
            {busy ? '验证中…' : '登录'}
          </button>
          <button type="button" onClick={() => setOpen(false)}>
            取消
          </button>
          {error && <span className="error">{error}</span>}
        </form>
      )}
    </span>
  );
}
