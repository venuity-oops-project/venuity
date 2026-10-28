import { api } from '../api.js';
import {
  formatDateTime,
  formatMoney,
  todayISO,
  transactionMeta,
} from '../store.js';
import {
  busyButton,
  emptyState,
  escapeHtml,
  icon,
  pageHeader,
  qs,
  toast,
} from '../ui.js';

export function mount(root, { addCleanup } = {}) {
  root.innerHTML = `
    ${pageHeader(
      'Dashboard',
      'A quick look at your play area today.',
      `<button type="button" class="btn btn-secondary" id="dash-refresh">
        ${icon('bx-refresh')} Refresh
      </button>`,
    )}
    <div class="stat-grid" id="dash-stats">
      ${Array.from(
        { length: 4 },
        () =>
          '<div class="stat-card"><div class="skeleton-row" style="width:100%;height:62px;margin:0"></div></div>',
      ).join('')}
    </div>
    <div class="glass-card">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:8px">
        <h2 style="margin:0">Recent transactions</h2>
        <span class="muted" style="font-size:12.5px">Latest 10 wallet movements</span>
      </div>
      <div class="activity-list" id="dash-activity">
        <div class="skeleton-row" style="height:52px"></div>
        <div class="skeleton-row" style="height:52px"></div>
        <div class="skeleton-row" style="height:52px"></div>
      </div>
    </div>
    <div class="glass-card mt-24">
      <h2 style="margin:0 0 16px">Quick actions</h2>
      <div class="quick-grid">
        <a class="quick-action" href="#/recharge">
          ${icon('bx-wallet')}
          <span>Recharge a wallet</span>
        </a>
        <a class="quick-action" href="#/scan">
          ${icon('bx-qr-scan')}
          <span>Open check-in scanner</span>
        </a>
        <a class="quick-action" href="#/customers">
          ${icon('bx-user-plus')}
          <span>Manage customers</span>
        </a>
      </div>
    </div>
  `;

  const statsElement = qs('#dash-stats', root);
  const activityElement = qs('#dash-activity', root);
  const refreshButton = qs('#dash-refresh', root);

  function renderStats(stats, summary) {
    const totalCustomers = Number(stats.totalCustomers) || 0;
    const totalRevenue = Number(stats.totalRevenue) || 0;
    const todayCheckIns = summary ? Number(summary.checkIns) || 0 : null;
    const todayRevenue = summary ? Number(summary.totalRevenue) || 0 : null;

    statsElement.innerHTML = `
      <div class="stat-card">
        <div>
          <div class="stat-label">Total customers</div>
          <div class="stat-value text-gradient">${totalCustomers}</div>
          <div class="stat-foot">Active wallets</div>
        </div>
        <div class="stat-icon indigo">${icon('bx-user')}</div>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">Wallet top-ups</div>
          <div class="stat-value text-gradient">${formatMoney(totalRevenue)}</div>
          <div class="stat-foot">All-time recharge revenue</div>
        </div>
        <div class="stat-icon green">${icon('bx-wallet-alt')}</div>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">Check-ins today</div>
          <div class="stat-value">${todayCheckIns === null ? '\u2014' : todayCheckIns}</div>
          <div class="stat-foot">Visits recorded today</div>
        </div>
        <div class="stat-icon amber">${icon('bx-qr-scan')}</div>
      </div>
      <div class="stat-card">
        <div>
          <div class="stat-label">Revenue today</div>
          <div class="stat-value">${todayRevenue === null ? '\u2014' : formatMoney(todayRevenue)}</div>
          <div class="stat-foot">Top-ups recorded today</div>
        </div>
        <div class="stat-icon rose">${icon('bx-coin-stack')}</div>
      </div>
    `;
  }

  function renderActivity(transactions) {
    if (!transactions || transactions.length === 0) {
      activityElement.innerHTML = emptyState(
        'bx-receipt',
        'No transactions yet',
        'Recharges and check-ins will appear here as they happen.',
      );
      return;
    }

    activityElement.innerHTML = transactions
      .map((transaction) => {
        const meta = transactionMeta(transaction.type);
        const amount = Math.abs(Number(transaction.amount) || 0);
        const sign = meta.credit ? '+' : '-';
        return `
          <div class="activity-item">
            <div class="activity-icon ${meta.credit ? 'credit' : 'debit'}">
              ${icon(meta.icon)}
            </div>
            <div class="activity-main">
              <div class="activity-title">${escapeHtml(transaction.customerName)}</div>
              <div class="activity-meta">
                ${escapeHtml(meta.label)} &middot; ${escapeHtml(formatDateTime(transaction.createdAt))}
              </div>
            </div>
            <div class="activity-amount ${meta.credit ? 'credit' : 'debit'}">
              ${sign}${formatMoney(amount)}
            </div>
          </div>
        `;
      })
      .join('');
  }

  async function load() {
    busyButton(refreshButton, true, 'Refreshing');
    try {
      const [stats, summary] = await Promise.all([
        api.stats(),
        api.analyticsSummary(todayISO(), todayISO()).catch(() => null),
      ]);
      renderStats(stats, summary);
      renderActivity(stats.recentTransactions || []);
    } catch (error) {
      statsElement.innerHTML = `
        <div class="glass-card" style="grid-column:1 / -1">
          ${emptyState('bx-error-circle', 'Could not load the dashboard', error.message)}
        </div>
      `;
      activityElement.innerHTML = '';
      toast(error.message, 'error');
    } finally {
      busyButton(refreshButton, false);
    }
  }

  refreshButton.addEventListener('click', load);
  addCleanup?.(() => refreshButton.removeEventListener('click', load));

  load();
}
