import React, { useState } from 'react';
import UsersPage from './UsersPage.jsx';
import TransferPage from './TransferPage.jsx';

/**
 * 应用骨架：顶部 tab（用户管理 / 转账演示）+ 全局错误横幅。
 * 横幅是 M9 错误链路的落点——后端 500 的可读消息在这里显示给用户。
 */
export default function App() {
  const [tab, setTab] = useState('users');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  return (
    <div className="app">
      <header>
        <h1>MiniSpringBoot Demo</h1>
        <nav>
          <button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
            用户管理
          </button>
          <button className={tab === 'transfer' ? 'active' : ''} onClick={() => setTab('transfer')}>
            转账演示
          </button>
        </nav>
      </header>

      {error && (
        <div className="banner error" role="alert">
          <span>{error}</span>
          <button onClick={() => setError(null)}>×</button>
        </div>
      )}
      {notice && (
        <div className="banner notice" role="status">
          <span>{notice}</span>
          <button onClick={() => setNotice(null)}>×</button>
        </div>
      )}

      <main>
        {tab === 'users' ? (
          <UsersPage onError={setError} onNotice={setNotice} />
        ) : (
          <TransferPage onError={setError} onNotice={setNotice} />
        )}
      </main>

      <footer>
        <small>前端 :9010 → Vite proxy /api → 后端 :9090 → MySQL（minispring_demo）</small>
      </footer>
    </div>
  );
}
