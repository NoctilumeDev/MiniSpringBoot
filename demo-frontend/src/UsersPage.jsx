import React, { useEffect, useState } from 'react';
import { api } from './api.js';

/**
 * 用户管理页：列表/新建/编辑/删除，全部真实落 MySQL（写后刷新，页面即库）。
 */
export default function UsersPage({ onError, onNotice }) {
  const [users, setUsers] = useState(null);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState({ name: '', email: '' });
  const [busy, setBusy] = useState(false);

  const reload = async () => {
    try {
      setUsers(await api.listUsers());
    } catch (e) {
      onError(`加载用户列表失败 — ${e.message}`);
    }
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const create = async (e) => {
    e.preventDefault();
    if (!name.trim() || !email.trim()) {
      onError('name 与 email 均必填');
      return;
    }
    setBusy(true);
    try {
      const created = await api.createUser({ name: name.trim(), email: email.trim() });
      onNotice(`已新建用户 #${created.id}（落库 MySQL）`);
      setName('');
      setEmail('');
      await reload();
    } catch (err) {
      onError(`新建失败 — ${err.message}`);
    } finally {
      setBusy(false);
    }
  };

  const startEdit = (u) => {
    setEditingId(u.id);
    setDraft({ name: u.name, email: u.email });
  };

  const saveEdit = async (id) => {
    setBusy(true);
    try {
      await api.updateUser(id, draft);
      setEditingId(null);
      onNotice(`用户 #${id} 已更新（落库 MySQL）`);
      await reload();
    } catch (err) {
      onError(`更新失败 — ${err.message}`);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id) => {
    if (!window.confirm(`确认删除用户 #${id}？将真实删除 MySQL 中的该行。`)) {
      return;
    }
    setBusy(true);
    try {
      await api.deleteUser(id);
      onNotice(`用户 #${id} 已删除（MySQL 行消失）`);
      await reload();
    } catch (err) {
      onError(`删除失败 — ${err.message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h2>用户（users 表）</h2>

      <form className="row-form" onSubmit={create}>
        <input placeholder="name" value={name} onChange={(e) => setName(e.target.value)} />
        <input placeholder="email（唯一键）" value={email} onChange={(e) => setEmail(e.target.value)} />
        <button type="submit" disabled={busy}>新建（POST /users）</button>
      </form>

      {users === null ? (
        <p>加载中…</p>
      ) : users.length === 0 ? (
        <p>（表为空）</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>id</th>
              <th>name</th>
              <th>email</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) =>
              editingId === u.id ? (
                <tr key={u.id} className="editing">
                  <td>{u.id}</td>
                  <td>
                    <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                  </td>
                  <td>
                    <input value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
                  </td>
                  <td>
                    <button disabled={busy} onClick={() => saveEdit(u.id)}>保存</button>
                    <button onClick={() => setEditingId(null)}>取消</button>
                  </td>
                </tr>
              ) : (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>
                    <button onClick={() => startEdit(u)}>编辑</button>
                    <button className="danger" disabled={busy} onClick={() => remove(u.id)}>删除</button>
                  </td>
                </tr>
              )
            )}
          </tbody>
        </table>
      )}
      <p className="hint">行数与内容应与 <code>docker exec minispring-mysql mysql … -e "select * from users"</code> 完全一致。</p>
    </section>
  );
}
