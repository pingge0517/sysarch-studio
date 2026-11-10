import { useCallback, useEffect, useState } from 'react';
import type { AccessGrant } from '../identity/auth';
import { can, json } from '../identity/auth';

// Element comments (P2 T8: 评审人 "只读 + 评论"). Everyone reads; reviewers, architects and administrators write.

interface Comment {
  id: string;
  projectId: string;
  elementId: string;
  author: string;
  body: string;
  createdAt: string;
}

export function CommentsPage({ project, grant }: { project: string; grant: AccessGrant | null }) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [filter, setFilter] = useState('');
  const [elementId, setElementId] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const query = new URLSearchParams({ projectId: project });
    if (filter.trim()) query.set('elementId', filter.trim());
    try {
      setComments(await json<Comment[]>(await fetch(`/api/v1/comments?${query}`)));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [project, filter]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const post = async () => {
    setError(null);
    try {
      await json(
        await fetch('/api/v1/comments', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ projectId: project, elementId: elementId.trim(), body }),
        }),
      );
      setBody('');
      await reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const allowed = can(grant, 'COMMENT');
  return (
    <section className="page">
      <h2>评论</h2>
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          void post();
        }}
      >
        <input placeholder="元素 ID，如 PD-003" value={elementId} onChange={(e) => setElementId(e.target.value)} disabled={!allowed} />
        <textarea placeholder={allowed ? '评论内容' : '你的角色只能查看评论'} value={body} onChange={(e) => setBody(e.target.value)} disabled={!allowed} rows={2} />
        <button type="submit" disabled={!allowed || !elementId.trim() || !body.trim()}>
          发表
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      <label>
        只看元素 <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="全部" />
      </label>
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>元素</th>
            <th>作者</th>
            <th>内容</th>
          </tr>
        </thead>
        <tbody>
          {comments.map((c) => (
            <tr key={c.id}>
              <td>{new Date(c.createdAt).toLocaleString()}</td>
              <td>{c.elementId}</td>
              <td>{c.author}</td>
              <td className="comment-body">{c.body}</td>
            </tr>
          ))}
          {comments.length === 0 && (
            <tr>
              <td colSpan={4}>还没有评论</td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
