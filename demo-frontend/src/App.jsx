import React, { useState } from 'react';
import {
  Activity,
  ArrowRightLeft,
  Database,
  Globe2,
  Minimize2,
  ServerCog,
  Users,
  X,
} from 'lucide-react';
import UsersPage from './UsersPage.jsx';
import TransferPage from './TransferPage.jsx';

function BrandContent({ highAvailability = false }) {
  return (
    <>
      <span className="brand-mark" aria-hidden="true">
        <Activity size={20} strokeWidth={1.7} />
      </span>
      <span>
        <span className="brand-name">MiniSpringBoot</span>
        <span className="brand-kicker">
          APPLICATION KERNEL · {highAvailability ? 'M10 HA' : 'M9'}
        </span>
      </span>
    </>
  );
}

/**
 * 应用骨架：顶部 tab（用户管理 / 转账演示）+ 全局错误横幅。
 * 横幅展示后端返回的可读错误消息。
 */
export default function App() {
  const highAvailability = window.location.port === '9080';
  const [tab, setTab] = useState('users');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [collapsed, setCollapsed] = useState(false);

  const showError = (message) => {
    setNotice(null);
    setError(message);
  };

  const showNotice = (message) => {
    setError(null);
    setNotice(message);
  };

  const toggleCollapsed = () => {
    // “收起”是纯观景态：退出工作区时同步结束当前操作反馈，
    // 避免旧消息悬在背景之上，展开后也不会恢复过期提示。
    setError(null);
    setNotice(null);
    setCollapsed((value) => !value);
  };

  return (
    <div className="app">
      <div className={`app-shell${collapsed ? ' app-shell--collapsed' : ''}`}>
        <header className="topbar">
          {collapsed ? (
            <button
              type="button"
              className="brand-lockup brand-lockup--expand"
              aria-label="展开 MiniSpringBoot 界面"
              title="展开界面"
              onClick={toggleCollapsed}
            >
              <BrandContent highAvailability={highAvailability} />
            </button>
          ) : (
            <div className="brand-lockup">
              <BrandContent highAvailability={highAvailability} />
            </div>
          )}

          {!collapsed && (
            <div className="topbar-actions">
              <nav className="primary-tabs" aria-label="演示模块">
                <button
                  type="button"
                  className={tab === 'users' ? 'active' : ''}
                  aria-current={tab === 'users' ? 'page' : undefined}
                  onClick={() => setTab('users')}
                >
                  <Users size={17} aria-hidden="true" />
                  用户管理
                </button>
                <button
                  type="button"
                  className={tab === 'transfer' ? 'active' : ''}
                  aria-current={tab === 'transfer' ? 'page' : undefined}
                  onClick={() => setTab('transfer')}
                >
                  <ArrowRightLeft size={17} aria-hidden="true" />
                  转账演示
                </button>
              </nav>
              <button
                type="button"
                className="shell-toggle"
                aria-expanded={!collapsed}
                aria-controls="application-workspace"
                onClick={toggleCollapsed}
              >
                <Minimize2 size={17} aria-hidden="true" />
                <span>收起界面</span>
              </button>
            </div>
          )}
        </header>

        {!collapsed && (error || notice) && (
          <div className="banner-stack" aria-label="操作消息">
            {error && (
              <div className="banner error" role="alert">
                <span>{error}</span>
                <button type="button" aria-label="关闭错误消息" onClick={() => setError(null)}>
                  <X size={16} aria-hidden="true" />
                </button>
              </div>
            )}
            {notice && (
              <div className="banner notice" role="status">
                <span>{notice}</span>
                <button type="button" aria-label="关闭成功消息" onClick={() => setNotice(null)}>
                  <X size={16} aria-hidden="true" />
                </button>
              </div>
            )}
          </div>
        )}

        <main id="application-workspace" className="workspace" hidden={collapsed}>
          {tab === 'users' ? (
            <UsersPage onError={showError} onNotice={showNotice} />
          ) : (
            <TransferPage onError={showError} onNotice={showNotice} />
          )}
        </main>

        <footer
          className={`system-rail${highAvailability ? ' system-rail--ha' : ''}`}
          aria-label="运行链路"
          hidden={collapsed}
        >
          <div className="rail-node">
            <Globe2 size={22} aria-hidden="true" />
            <span><strong>浏览器</strong><small>{highAvailability ? ':9080' : ':9010'}</small></span>
          </div>
          <span className="rail-link" aria-hidden="true">/api</span>
          <div className="rail-node">
            <ServerCog size={22} aria-hidden="true" />
            <span>
              <strong>{highAvailability ? 'Nginx' : 'MiniSpring'}</strong>
              <small>{highAvailability ? 'least_conn' : ':9090'}</small>
            </span>
          </div>
          {highAvailability && <span className="rail-link" aria-hidden="true">proxy</span>}
          {highAvailability && (
            <div className="rail-node">
              <Activity size={22} aria-hidden="true" />
              <span><strong>MiniSpring ×3</strong><small>:9091 · :9092 · :9093</small></span>
            </div>
          )}
          <span className="rail-link" aria-hidden="true">JDBC</span>
          <div className="rail-node">
            <Database size={22} aria-hidden="true" />
            <span><strong>MySQL</strong><small>minispring_demo</small></span>
          </div>
        </footer>
      </div>
    </div>
  );
}
