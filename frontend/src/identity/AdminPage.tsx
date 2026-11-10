import { useCallback, useEffect, useState } from 'react';
import type { AuditEntry, RoleMapping } from './auth';
import { deleteMapping, listAudit, listMappings, putMapping } from './auth';

// Administration (P2 T8): the role mapping (Polarion role → tool role, a table, not code) and the audit log.

const ROLES = [
  ['ADMIN', '项目管理员'],
  ['ARCHITECT', '架构师'],
  ['REVIEWER', '评审人'],
  ['OBSERVER', '观察者'],
] as const;

export function AdminPage({ project }: { project: string }) {
  const [mappings, setMappings] = useState<RoleMapping[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [action, setAction] = useState('');
  const [form, setForm] = useState({ polarionProject: '*', polarionRole: '', toolRole: 'ARCHITECT' });
  const [error, setError] = useState<string | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setMappings(await listMappings());
    } catch (e) {
      setError((e as Error).message);
    }
    try {
      setAudit(await listAudit(project, action));
      setAuditError(null);
    } catch (e) {
      setAudit([]);
      setAuditError((e as Error).message);
    }
  }, [project, action]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const run = async (task: () => Promise<unknown>) => {
    setError(null);
    try {
      await task();
      await reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="page">
      <h2>角色映射</h2>
      <p className="hint">
        Polarion 项目：项目 ID、<code>*</code>（所有项目）或 <code>(unbound)</code>（未绑定 Polarion 的项目）。Polarion 角色：项目角色 ID、
        <code>global:&lt;id&gt;</code> 或 <code>*</code>（所有已登录用户）。用户取匹配行中最高的角色，都不匹配时为观察者。
      </p>
      <table>
        <thead>
          <tr>
            <th>Polarion 项目</th>
            <th>Polarion 角色</th>
            <th>工具角色</th>
            <th>修改人</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {mappings.map((m) => (
            <tr key={m.id}>
              <td>{m.polarionProject}</td>
              <td>{m.polarionRole}</td>
              <td>{ROLES.find(([id]) => id === m.toolRole)?.[1] ?? m.toolRole}</td>
              <td>{m.createdBy}</td>
              <td>
                <button type="button" onClick={() => void run(() => deleteMapping(m.id))}>
                  删除
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          void run(() => putMapping(form.polarionProject, form.polarionRole, form.toolRole));
        }}
      >
        <input value={form.polarionProject} onChange={(e) => setForm({ ...form, polarionProject: e.target.value })} placeholder="Polarion 项目" />
        <input value={form.polarionRole} onChange={(e) => setForm({ ...form, polarionRole: e.target.value })} placeholder="Polarion 角色" />
        <select value={form.toolRole} onChange={(e) => setForm({ ...form, toolRole: e.target.value })}>
          {ROLES.map(([id, label]) => (
            <option key={id} value={id}>
              {label}
            </option>
          ))}
        </select>
        <button type="submit" disabled={!form.polarionProject.trim() || !form.polarionRole.trim()}>
          添加或替换
        </button>
      </form>
      {error && <p className="error">{error}</p>}

      <h2>审计日志{project ? '（本项目）' : ''}</h2>
      <label>
        动作
        <select value={action} onChange={(e) => setAction(e.target.value)}>
          <option value="">全部</option>
          {['ACCESS_DENIED', 'LOCK_FORCE_RELEASE', 'MERGE', 'BASELINE_DELETE', 'REPORT_GENERATE', 'BRANCH_ABANDON', 'IMPORT', 'ROLE_MAPPING_CHANGE'].map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
      </label>
      {auditError && <p className="error">{auditError}</p>}
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>谁</th>
            <th>动作</th>
            <th>元素</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          {audit.map((a) => (
            <tr key={a.id}>
              <td>{new Date(a.createdAt).toLocaleString()}</td>
              <td>{a.actor}</td>
              <td>{a.action}</td>
              <td>{a.elementId ?? ''}</td>
              <td>{a.detail ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
