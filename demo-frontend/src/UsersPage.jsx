import React, { useEffect, useRef, useState } from 'react';
import {
  Database,
  Mail,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Trash2,
  UserRound,
  Users,
} from 'lucide-react';
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
  const [deleteCandidate, setDeleteCandidate] = useState(null);
  const [busy, setBusy] = useState(false);
  const deleteDialogRef = useRef(null);

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

  useEffect(() => {
    const dialog = deleteDialogRef.current;
    if (deleteCandidate && dialog && !dialog.open) dialog.showModal();
  }, [deleteCandidate]);

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

  const requestRemove = (user) => {
    setDeleteCandidate(user);
  };

  const cancelRemove = () => {
    if (!busy) setDeleteCandidate(null);
  };

  const confirmRemove = async () => {
    if (!deleteCandidate) return;
    const id = deleteCandidate.id;
    setBusy(true);
    try {
      await api.deleteUser(id);
      setDeleteCandidate(null);
      onNotice(`用户 #${id} 已删除（MySQL 行消失）`);
      await reload();
    } catch (err) {
      onError(`删除失败 — ${err.message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="workspace-view users-view" aria-labelledby="users-title">
      <header className="view-heading">
        <div>
          <p className="eyebrow">DATA LEDGER · USER DOMAIN</p>
          <h2 id="users-title">用户 <span>users 表</span></h2>
          <p>界面读写直接穿过 MiniSpring 容器，并由 MySQL 返回最终事实。</p>
        </div>
        <div className="view-counter" aria-label={users === null ? '用户记录同步中' : `${users.length} 条用户记录`}>
          <Users size={20} aria-hidden="true" />
          <span><small>记录</small><strong>{users === null ? '··' : String(users.length).padStart(2, '0')}</strong></span>
        </div>
      </header>

      <div className="users-layout">
        <div className="ledger-pane">
          <div className="section-heading">
            <Database size={18} aria-hidden="true" />
            <div><span>数据库实录</span><small>SELECT · users</small></div>
          </div>

          {users === null ? (
            <p className="empty-state">正在同步 users 表…</p>
          ) : users.length === 0 ? (
            <p className="empty-state">users 表当前为空。</p>
          ) : (
            <div className="table-wrap">
              <table>
                <colgroup>
                  <col className="user-col-id" />
                  <col className="user-col-name" />
                  <col className="user-col-email" />
                  <col className="user-col-actions" />
                </colgroup>
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
                        <td className="edit-cell" colSpan="4">
                          <div className="edit-panel">
                            <span className="edit-id"><small>ID</small><strong>{u.id}</strong></span>
                            <label className="edit-field" htmlFor={`edit-name-${u.id}`}>
                              <span>姓名</span>
                              <input
                                id={`edit-name-${u.id}`}
                                value={draft.name}
                                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                              />
                            </label>
                            <label className="edit-field" htmlFor={`edit-email-${u.id}`}>
                              <span>邮箱</span>
                              <input
                                id={`edit-email-${u.id}`}
                                type="email"
                                value={draft.email}
                                onChange={(e) => setDraft({ ...draft, email: e.target.value })}
                              />
                            </label>
                            <div className="action-group edit-actions">
                            <button type="button" disabled={busy} onClick={() => saveEdit(u.id)}>
                              <Save size={15} aria-hidden="true" />保存
                            </button>
                            <button type="button" onClick={() => setEditingId(null)}>
                              <RotateCcw size={15} aria-hidden="true" />取消
                            </button>
                            </div>
                          </div>
                        </td>
                      </tr>
                    ) : (
                      <tr key={u.id}>
                        <td data-label="ID">{u.id}</td>
                        <td data-label="姓名" title={u.name}>{u.name}</td>
                        <td data-label="邮箱" title={u.email}>{u.email}</td>
                        <td data-label="操作">
                          <div className="action-group">
                            <button type="button" onClick={() => startEdit(u)}>
                              <Pencil size={15} aria-hidden="true" />编辑
                            </button>
                            <button type="button" className="danger" disabled={busy} onClick={() => requestRemove(u)}>
                              <Trash2 size={15} aria-hidden="true" />删除
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <aside className="command-pane" aria-labelledby="create-user-title">
          <div className="section-heading">
            <Plus size={18} aria-hidden="true" />
            <div><span id="create-user-title">新建用户</span><small>POST · /users</small></div>
          </div>

          <form className="command-form" onSubmit={create}>
            <label htmlFor="create-name"><UserRound size={15} aria-hidden="true" />姓名</label>
            <input
              id="create-name"
              autoComplete="name"
              placeholder="请输入姓名"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <label htmlFor="create-email"><Mail size={15} aria-hidden="true" />邮箱 <span>唯一键</span></label>
            <input
              id="create-email"
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <button className="primary-action" type="submit" disabled={busy}>
              <Plus size={17} aria-hidden="true" />
              {busy ? '正在写入…' : '新建用户'}
            </button>
          </form>
        </aside>
      </div>

      <p className="hint">写后立即重读；当前行数与内容应同 MySQL 的 <code>users</code> 表完全一致。</p>

      {deleteCandidate && (
        <dialog
          ref={deleteDialogRef}
          className="confirm-dialog"
          role="alertdialog"
          aria-labelledby="delete-confirm-title"
          aria-describedby="delete-confirm-description"
          onCancel={(event) => {
            event.preventDefault();
            cancelRemove();
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault();
              cancelRemove();
            }
          }}
        >
          <span className="confirm-dialog__mark" aria-hidden="true">
            <Trash2 size={20} />
          </span>
          <p className="confirm-dialog__eyebrow">DELETE · /users/{deleteCandidate.id}</p>
          <h3 id="delete-confirm-title">确认删除这条用户记录？</h3>
          <p id="delete-confirm-description">
            用户 <strong>{deleteCandidate.name}</strong>（#{deleteCandidate.id}）将从 MySQL 中真实删除，
            此操作不能撤销。
          </p>
          <div className="confirm-dialog__actions">
            <button type="button" autoFocus disabled={busy} onClick={cancelRemove}>
              取消
            </button>
            <button type="button" className="danger" disabled={busy} onClick={confirmRemove}>
              <Trash2 size={15} aria-hidden="true" />
              {busy ? '正在删除…' : '确认删除'}
            </button>
          </div>
        </dialog>
      )}
    </section>
  );
}
