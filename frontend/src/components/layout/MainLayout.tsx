import type { ReactNode } from 'react';
import type { ConnectionStatus as Status } from '../../model/taskEvent';
import { ConnectionStatus } from '../common/ConnectionStatus';

export function MainLayout({ sidebar, children, connectionStatus, onOpenSidebar }: { sidebar: ReactNode; children: ReactNode; connectionStatus: Status; onOpenSidebar(): void }) {
  return (
    <div className="app-shell">
      {sidebar}
      <section className="main-panel">
        <header className="topbar">
          <button type="button" className="menu-button" onClick={onOpenSidebar} aria-label="Открыть sidebar">☰</button>
          <ConnectionStatus status={connectionStatus} />
        </header>
        {children}
      </section>
    </div>
  );
}
