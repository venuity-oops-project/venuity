/**
 * Users management page: user list, details drawer, create/edit, status actions,
 * bulk operations, CSV import/export, and audit log viewer.
 */

import { api } from '../api.js';
import { state } from '../store.js';
import {
  escapeHtml,
  icon,
  pageHeader,
  qs,
  qsa,
  toast,
  busyButton,
  debounce,
  emptyState,
  openModal,
  confirmDialog,
} from '../ui.js';

export function mount(root, { addCleanup }) {
  const isAdmin = state.user && state.user.role === 'ADMIN';

  let currentTab = 'users'; // 'users' | 'audit'
  let query = '';
  let roleFilter = '';
  let statusFilter = '';
  let currentPage = 0;
  let pageSize = 20;
  let totalPages = 1;
  let totalUsers = 0;
  let selectedUserIds = new Set();
  let usersList = [];

  // Audit filters
  let auditActor = '';
  let auditAction = '';
  let auditTarget = '';
  let auditPage = 0;
  let auditTotalPages = 1;

  root.innerHTML = `
    ${pageHeader(
      'Users & Access',
      'Manage staff and administrator accounts, security policies, and audit logs.',
      `
        <div class="header-action-group">
          <button type="button" class="btn btn-secondary" id="btn-export-users">${icon('bx-download')} Export</button>
          <button type="button" class="btn btn-secondary" id="btn-import-users">${icon('bx-upload')} Import</button>
          <button type="button" class="btn btn-primary" id="btn-create-user">${icon('bx-user-plus')} New User</button>
        </div>
      `,
    )}

    <div class="user-mgmt-tabs">
      <button type="button" class="tab-btn active" id="tab-users-btn">${icon('bx-group')} Users Directory</button>
      <button type="button" class="tab-btn" id="tab-audit-btn">${icon('bx-history')} Audit Logs</button>
    </div>

    <!-- USERS VIEW -->
    <div id="users-view-panel">
      <div class="glass-card filter-toolbar" style="padding:16px;margin-bottom:20px">
        <div class="filter-group-row">
          <div class="search-input-wrapper">
            ${icon('bx-search', 'search-icon')}
            <input type="text" class="form-input search-box" id="users-search" placeholder="Search by name, username, email, phone...">
          </div>
          <select class="form-input filter-select" id="users-role-filter">
            <option value="">All Roles</option>
            <option value="ADMIN">Administrator</option>
            <option value="STAFF">Staff</option>
          </select>
          <select class="form-input filter-select" id="users-status-filter">
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Disabled</option>
            <option value="LOCKED">Locked</option>
            <option value="EXPIRED">Expired</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>

        <div class="bulk-action-bar" id="bulk-action-bar" style="display:none;margin-top:14px;padding-top:14px;border-top:1px solid rgba(255,255,255,0.08);display:none;align-items:center;gap:12px;flex-wrap:wrap">
          <span class="muted" style="font-weight:600;font-size:13px" id="bulk-selected-count">0 selected</span>
          <button type="button" class="btn btn-secondary btn-sm" id="bulk-enable-btn">${icon('bx-check')} Enable</button>
          <button type="button" class="btn btn-secondary btn-sm" id="bulk-disable-btn">${icon('bx-block')} Disable</button>
          <button type="button" class="btn btn-secondary btn-sm" id="bulk-staff-btn">${icon('bx-user')} Set as Staff</button>
          <button type="button" class="btn btn-secondary btn-sm" id="bulk-admin-btn">${icon('bx-shield')} Set as Admin</button>
          <button type="button" class="btn btn-danger btn-sm" id="bulk-archive-btn">${icon('bx-archive')} Archive</button>
        </div>
      </div>

      <div class="glass-card table-wrapper" style="overflow-x:auto">
        <table class="data-table" id="users-table">
          <thead>
            <tr>
              <th style="width:36px"><input type="checkbox" id="select-all-users" aria-label="Select all"></th>
              <th>User</th>
              <th>Role</th>
              <th>Status</th>
              <th>Contact</th>
              <th>Created</th>
              <th>Last Login</th>
              <th style="text-align:right">Actions</th>
            </tr>
          </thead>
          <tbody id="users-table-body">
            <tr><td colspan="8" style="text-align:center;padding:40px" class="muted">Loading users...</td></tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar" id="users-pagination" style="display:flex;justify-content:space-between;align-items:center;margin-top:16px;flex-wrap:wrap;gap:12px">
        <span class="muted" style="font-size:13px" id="users-page-info">Showing 0 of 0</span>
        <div style="display:flex;gap:8px;align-items:center">
          <button type="button" class="btn btn-secondary btn-sm" id="users-prev-btn" disabled>${icon('bx-chevron-left')} Prev</button>
          <span style="font-weight:600;font-size:13px" id="users-current-page">1</span>
          <button type="button" class="btn btn-secondary btn-sm" id="users-next-btn" disabled>Next ${icon('bx-chevron-right')}</button>
        </div>
      </div>
    </div>

    <!-- AUDIT VIEW -->
    <div id="audit-view-panel" style="display:none">
      <div class="glass-card filter-toolbar" style="padding:16px;margin-bottom:20px">
        <div class="filter-group-row">
          <input type="text" class="form-input search-box" id="audit-actor-filter" placeholder="Filter by actor...">
          <input type="text" class="form-input search-box" id="audit-target-filter" placeholder="Filter by target user...">
          <input type="text" class="form-input search-box" id="audit-action-filter" placeholder="Filter by action (e.g. LOGIN)...">
          <button type="button" class="btn btn-secondary" id="btn-export-audit">${icon('bx-download')} Export CSV</button>
        </div>
      </div>

      <div class="glass-card table-wrapper" style="overflow-x:auto">
        <table class="data-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Target</th>
              <th>IP Address</th>
              <th>Status</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody id="audit-table-body">
            <tr><td colspan="7" style="text-align:center;padding:40px" class="muted">Loading audit entries...</td></tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar" id="audit-pagination" style="display:flex;justify-content:flex-end;align-items:center;margin-top:16px;gap:8px">
        <button type="button" class="btn btn-secondary btn-sm" id="audit-prev-btn" disabled>${icon('bx-chevron-left')} Prev</button>
        <span style="font-weight:600;font-size:13px" id="audit-current-page">1</span>
        <button type="button" class="btn btn-secondary btn-sm" id="audit-next-btn" disabled>Next ${icon('bx-chevron-right')}</button>
      </div>
    </div>
  `;

  // UI Element References
  const usersView = qs('#users-view-panel', root);
  const auditView = qs('#audit-view-panel', root);
  const tabUsersBtn = qs('#tab-users-btn', root);
  const tabAuditBtn = qs('#tab-audit-btn', root);

  const searchInput = qs('#users-search', root);
  const roleSelect = qs('#users-role-filter', root);
  const statusSelect = qs('#users-status-filter', root);
  const selectAll = qs('#select-all-users', root);
  const bulkBar = qs('#bulk-action-bar', root);
  const bulkCount = qs('#bulk-selected-count', root);

  const usersBody = qs('#users-table-body', root);
  const pageInfo = qs('#users-page-info', root);
  const pageNum = qs('#users-current-page', root);
  const prevBtn = qs('#users-prev-btn', root);
  const nextBtn = qs('#users-next-btn', root);

  const auditBody = qs('#audit-table-body', root);
  const auditPrev = qs('#audit-prev-btn', root);
  const auditNext = qs('#audit-next-btn', root);
  const auditPageNum = qs('#audit-current-page', root);

  // Tab switching
  tabUsersBtn.addEventListener('click', () => {
    currentTab = 'users';
    tabUsersBtn.classList.add('active');
    tabAuditBtn.classList.remove('active');
    usersView.style.display = 'block';
    auditView.style.display = 'none';
  });

  tabAuditBtn.addEventListener('click', () => {
    currentTab = 'audit';
    tabAuditBtn.classList.add('active');
    tabUsersBtn.classList.remove('active');
    usersView.style.display = 'none';
    auditView.style.display = 'block';
    loadAuditLogs();
  });

  // Load Users Data
  async function loadUsers() {
    usersBody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px" class="muted">${icon('bx-loader-alt', 'bx-spin')} Loading users...</td></tr>`;
    try {
      const data = await api.users({
        query,
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        page: currentPage,
        size: pageSize,
        sort: 'id',
        direction: 'desc',
      });

      usersList = data.content || [];
      totalUsers = data.totalElements || 0;
      totalPages = data.totalPages || 1;

      renderUsersTable();
      updatePaginationControls();
    } catch (err) {
      usersBody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:30px" class="text-danger">${icon('bx-error-circle')} Failed to load users: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderUsersTable() {
    selectedUserIds.clear();
    updateBulkBar();
    selectAll.checked = false;

    if (!usersList.length) {
      usersBody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px" class="muted">No users found matching your filters.</td></tr>`;
      return;
    }

    usersBody.innerHTML = usersList.map((u) => {
      const initials = (u.fullName || u.username).substring(0, 2).toUpperCase();
      const statusBadge = getStatusBadge(u.status);
      const roleBadge = u.role === 'ADMIN'
        ? `<span class="badge badge-admin">${icon('bx-shield')} Admin</span>`
        : `<span class="badge badge-staff">${icon('bx-user')} Staff</span>`;

      return `
        <tr data-user-id="${u.id}">
          <td><input type="checkbox" class="user-row-checkbox" data-id="${u.id}" aria-label="Select user ${escapeHtml(u.username)}"></td>
          <td>
            <div style="display:flex;align-items:center;gap:12px">
              <div class="user-avatar">${escapeHtml(initials)}</div>
              <div>
                <div style="font-weight:600;font-size:14px">${escapeHtml(u.fullName || u.username)}</div>
                <div class="muted" style="font-size:12px">@${escapeHtml(u.username)}</div>
              </div>
            </div>
          </td>
          <td>${roleBadge}</td>
          <td>${statusBadge}</td>
          <td>
            <div style="font-size:13px">${escapeHtml(u.email || '—')}</div>
            <div class="muted" style="font-size:11px">${escapeHtml(u.phone || '')}</div>
          </td>
          <td style="font-size:12px;white-space:nowrap">${formatDate(u.dateJoined)}</td>
          <td style="font-size:12px;white-space:nowrap">${formatDate(u.lastLogin)}</td>
          <td style="text-align:right">
            <div class="row-actions">
              <button type="button" class="icon-btn btn-sm" data-action="detail" data-id="${u.id}" title="View Details">${icon('bx-show')}</button>
              <button type="button" class="icon-btn btn-sm" data-action="edit" data-id="${u.id}" title="Edit Profile">${icon('bx-edit')}</button>
              <button type="button" class="icon-btn btn-sm" data-action="reset-pass" data-id="${u.id}" title="Reset Password">${icon('bx-key')}</button>
              ${u.status === 'LOCKED' ? `<button type="button" class="icon-btn btn-sm" data-action="unlock" data-id="${u.id}" title="Unlock Account">${icon('bx-lock-open')}</button>` : ''}
              ${u.enabled ? `<button type="button" class="icon-btn btn-sm danger" data-action="disable" data-id="${u.id}" title="Disable User">${icon('bx-block')}</button>` : `<button type="button" class="icon-btn btn-sm" data-action="enable" data-id="${u.id}" title="Enable User">${icon('bx-check-circle')}</button>`}
              ${u.status === 'ARCHIVED' ? `<button type="button" class="icon-btn btn-sm" data-action="restore" data-id="${u.id}" title="Restore User">${icon('bx-undo')}</button>` : `<button type="button" class="icon-btn btn-sm danger" data-action="archive" data-id="${u.id}" title="Archive User">${icon('bx-trash')}</button>`}
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  function getStatusBadge(status) {
    switch (status) {
      case 'ACTIVE': return `<span class="status-pill status-active">${icon('bx-check-circle')} Active</span>`;
      case 'DISABLED': return `<span class="status-pill status-disabled">${icon('bx-block')} Disabled</span>`;
      case 'LOCKED': return `<span class="status-pill status-locked">${icon('bx-lock')} Locked</span>`;
      case 'EXPIRED': return `<span class="status-pill status-expired">${icon('bx-time')} Expired</span>`;
      case 'ARCHIVED': return `<span class="status-pill status-archived">${icon('bx-archive')} Archived</span>`;
      default: return `<span class="status-pill">${escapeHtml(status)}</span>`;
    }
  }

  function formatDate(iso) {
    if (!iso) return 'Never';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return iso;
    }
  }

  function updatePaginationControls() {
    const start = totalUsers === 0 ? 0 : currentPage * pageSize + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalUsers);
    pageInfo.textContent = `Showing ${start}–${end} of ${totalUsers}`;
    pageNum.textContent = `${currentPage + 1} / ${Math.max(1, totalPages)}`;
    prevBtn.disabled = currentPage <= 0;
    nextBtn.disabled = currentPage >= totalPages - 1;
  }

  function updateBulkBar() {
    const count = selectedUserIds.size;
    if (count > 0) {
      bulkBar.style.display = 'flex';
      bulkCount.textContent = `${count} user${count > 1 ? 's' : ''} selected`;
    } else {
      bulkBar.style.display = 'none';
    }
  }

  // Filter event listeners
  searchInput.addEventListener('input', debounce(() => {
    query = searchInput.value.trim();
    currentPage = 0;
    loadUsers();
  }, 300));

  roleSelect.addEventListener('change', () => {
    roleFilter = roleSelect.value;
    currentPage = 0;
    loadUsers();
  });

  statusSelect.addEventListener('change', () => {
    statusFilter = statusSelect.value;
    currentPage = 0;
    loadUsers();
  });

  prevBtn.addEventListener('click', () => {
    if (currentPage > 0) {
      currentPage--;
      loadUsers();
    }
  });

  nextBtn.addEventListener('click', () => {
    if (currentPage < totalPages - 1) {
      currentPage++;
      loadUsers();
    }
  });

  // Table checkbox selection
  selectAll.addEventListener('change', () => {
    const checked = selectAll.checked;
    qsa('.user-row-checkbox', usersBody).forEach((cb) => {
      cb.checked = checked;
      const id = Number(cb.dataset.id);
      if (checked) selectedUserIds.add(id); else selectedUserIds.delete(id);
    });
    updateBulkBar();
  });

  usersBody.addEventListener('change', (e) => {
    if (e.target.classList.contains('user-row-checkbox')) {
      const id = Number(e.target.dataset.id);
      if (e.target.checked) selectedUserIds.add(id); else selectedUserIds.delete(id);
      selectAll.checked = selectedUserIds.size === usersList.length && usersList.length > 0;
      updateBulkBar();
    }
  });

  // Table row actions
  usersBody.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const action = btn.dataset.action;
    const id = Number(btn.dataset.id);
    const user = usersList.find((u) => u.id === id);
    if (!user) return;

    if (action === 'detail') openUserDetailModal(id);
    else if (action === 'edit') openEditUserModal(user);
    else if (action === 'reset-pass') openResetPasswordModal(user);
    else if (action === 'unlock') {
      const ok = await confirmDialog({
        title: 'Unlock Account',
        message: `Clear the failed login lockout for '${user.username}'?`,
        confirmLabel: 'Unlock',
      });
      if (ok) {
        try {
          await api.unlockUser(id);
          toast(`Account for '${user.username}' unlocked.`, 'success');
          loadUsers();
        } catch (err) { toast(err.message, 'error'); }
      }
    } else if (action === 'disable') {
      const ok = await confirmDialog({
        title: 'Disable User',
        message: `Disable user '${user.username}'? They will immediately be logged out and cannot sign in.`,
        confirmLabel: 'Disable',
        danger: true,
      });
      if (ok) {
        try {
          await api.disableUser(id);
          toast(`User '${user.username}' disabled.`, 'success');
          loadUsers();
        } catch (err) { toast(err.message, 'error'); }
      }
    } else if (action === 'enable') {
      try {
        await api.enableUser(id);
        toast(`User '${user.username}' enabled.`, 'success');
        loadUsers();
      } catch (err) { toast(err.message, 'error'); }
    } else if (action === 'archive') {
      const ok = await confirmDialog({
        title: 'Archive User',
        message: `Archive user '${user.username}'? Historical transactions and ledger entries will remain preserved.`,
        confirmLabel: 'Archive',
        danger: true,
      });
      if (ok) {
        try {
          await api.archiveUser(id);
          toast(`User '${user.username}' archived.`, 'success');
          loadUsers();
        } catch (err) { toast(err.message, 'error'); }
      }
    } else if (action === 'restore') {
      try {
        await api.restoreUser(id);
        toast(`User '${user.username}' restored.`, 'success');
        loadUsers();
      } catch (err) { toast(err.message, 'error'); }
    }
  });

  // Bulk actions
  async function handleBulk(action, role) {
    const count = selectedUserIds.size;
    const ok = await confirmDialog({
      title: `Bulk ${action}`,
      message: `Apply ${action.toLowerCase()} to ${count} selected user(s)?`,
      confirmLabel: 'Apply',
      danger: action === 'ARCHIVE' || action === 'DISABLE',
    });
    if (!ok) return;

    try {
      const res = await api.bulkUsers({
        userIds: Array.from(selectedUserIds),
        action,
        role,
      });
      toast(`Processed ${res.succeeded} of ${res.total} users successfully.`, res.failed > 0 ? 'info' : 'success');
      loadUsers();
    } catch (err) { toast(err.message, 'error'); }
  }

  qs('#bulk-enable-btn', root).addEventListener('click', () => handleBulk('ENABLE'));
  qs('#bulk-disable-btn', root).addEventListener('click', () => handleBulk('DISABLE'));
  qs('#bulk-staff-btn', root).addEventListener('click', () => handleBulk('SET_ROLE', 'STAFF'));
  qs('#bulk-admin-btn', root).addEventListener('click', () => handleBulk('SET_ROLE', 'ADMIN'));
  qs('#bulk-archive-btn', root).addEventListener('click', () => handleBulk('ARCHIVE'));

  // CSV Export
  qs('#btn-export-users', root).addEventListener('click', () => {
    const q = new URLSearchParams();
    if (query) q.set('query', query);
    if (roleFilter) q.set('role', roleFilter);
    if (statusFilter) q.set('status', statusFilter);
    window.location.href = `/api/users/export?${q.toString()}`;
  });

  // CSV Import Modal
  qs('#btn-import-users', root).addEventListener('click', openImportModal);

  // Create User Modal
  qs('#btn-create-user', root).addEventListener('click', openCreateUserModal);

  // ==========================================
  // MODAL: CREATE USER
  // ==========================================
  function openCreateUserModal() {
    openModal({
      title: 'Create New User',
      subtitle: 'Add a new staff member or administrator account.',
      size: 'medium',
      body: `
        <form id="create-user-form" autocomplete="off">
          <div id="cu-error" style="display:none;margin-bottom:16px;padding:10px 14px;border-radius:10px;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.3);color:#ef4444;font-size:13px"></div>

          <div class="form-grid">
            <div class="form-group">
              <label for="cu-username">Username *</label>
              <input class="form-input" id="cu-username" maxlength="32" required placeholder="e.g. cashier2">
              <small class="muted" style="font-size:11px">3-32 characters: letters, numbers, dot, underscore, hyphen</small>
            </div>
            <div class="form-group">
              <label for="cu-role">Role *</label>
              <select class="form-input" id="cu-role" required>
                <option value="STAFF" selected>Staff (Check-in, Recharge, Customers)</option>
                <option value="ADMIN">Administrator (Full Access)</option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label for="cu-fullname">Full Name</label>
            <input class="form-input" id="cu-fullname" maxlength="255" placeholder="e.g. Alice Smith">
          </div>

          <div class="form-grid">
            <div class="form-group">
              <label for="cu-email">Email (optional)</label>
              <input class="form-input" id="cu-email" type="email" maxlength="254" placeholder="alice@playarea.com">
            </div>
            <div class="form-group">
              <label for="cu-phone">Phone (optional)</label>
              <input class="form-input" id="cu-phone" maxlength="30" placeholder="+91 98765 43210">
            </div>
          </div>

          <div class="form-group">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" id="cu-auto-pass" checked>
              <span>Generate strong temporary password automatically</span>
            </label>
          </div>

          <div id="cu-manual-pass-group" style="display:none">
            <div class="form-group">
              <label for="cu-password">Password *</label>
              <input class="form-input" id="cu-password" type="password" placeholder="At least 8 chars, 1 letter, 1 number">
              <div class="pass-strength-bar" style="margin-top:6px;height:4px;border-radius:2px;background:rgba(255,255,255,0.1)">
                <div id="cu-strength-meter" style="height:100%;width:0;transition:all 0.2s"></div>
              </div>
              <small class="muted" id="cu-strength-text" style="font-size:11px">Password strength: none</small>
            </div>
          </div>

          <div class="form-group">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" id="cu-must-change" checked>
              <span>Require password change on first login</span>
            </label>
          </div>

          <div class="form-group">
            <label for="cu-notes">Notes / Department</label>
            <textarea class="form-input" id="cu-notes" rows="2" placeholder="Optional notes..."></textarea>
          </div>
        </form>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="submit" form="create-user-form" class="btn btn-primary" id="cu-submit">
          ${icon('bx-user-plus')} Create User
        </button>
      `,
      onMount(modalRoot, close) {
        const form = qs('#create-user-form', modalRoot);
        const autoPass = qs('#cu-auto-pass', modalRoot);
        const manualGroup = qs('#cu-manual-pass-group', modalRoot);
        const passInput = qs('#cu-password', modalRoot);
        const strengthMeter = qs('#cu-strength-meter', modalRoot);
        const strengthText = qs('#cu-strength-text', modalRoot);
        const errBox = qs('#cu-error', modalRoot);
        const submitBtn = qs('#cu-submit', modalRoot);

        autoPass.addEventListener('change', () => {
          manualGroup.style.display = autoPass.checked ? 'none' : 'block';
          if (!autoPass.checked) passInput.focus();
        });

        passInput.addEventListener('input', () => {
          const val = passInput.value;
          const score = calculatePasswordStrength(val);
          updateStrengthUI(score, strengthMeter, strengthText);
        });

        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          errBox.style.display = 'none';

          const uname = qs('#cu-username', modalRoot).value.trim();
          const role = qs('#cu-role', modalRoot).value;
          const fullName = qs('#cu-fullname', modalRoot).value.trim();
          const email = qs('#cu-email', modalRoot).value.trim();
          const phone = qs('#cu-phone', modalRoot).value.trim();
          const notes = qs('#cu-notes', modalRoot).value.trim();
          const isAuto = autoPass.checked;
          const pass = passInput.value;
          const mustChange = qs('#cu-must-change', modalRoot).checked;

          if (!isAuto && (!pass || pass.length < 8)) {
            errBox.textContent = 'Password must be at least 8 characters long.';
            errBox.style.display = 'block';
            return;
          }

          busyButton(submitBtn, true, 'Creating user...');
          try {
            const res = await api.createUser({
              username: uname,
              role,
              fullName: fullName || undefined,
              email: email || undefined,
              phone: phone || undefined,
              notes: notes || undefined,
              generatePassword: isAuto,
              password: !isAuto ? pass : undefined,
              mustChangePassword: mustChange,
            });

            close();
            loadUsers();

            if (res.temporaryPassword) {
              showCreatedCredentialsModal(res.user.username, res.temporaryPassword);
            } else {
              toast(`User '${res.user.username}' created successfully!`, 'success');
            }
          } catch (err) {
            errBox.textContent = err.message || 'Failed to create user.';
            errBox.style.display = 'block';
          } finally {
            busyButton(submitBtn, false);
          }
        });
      },
    });
  }

  function showCreatedCredentialsModal(username, tempPassword) {
    openModal({
      title: 'User Created Successfully',
      subtitle: 'Temporary credentials generated for this account.',
      size: 'narrow',
      body: `
        <div style="background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);padding:12px;border-radius:8px;margin-bottom:16px">
          ${icon('bx-alarm-exclamation')} <strong>Copy this password now!</strong> It will not be shown again.
        </div>
        <div class="form-group">
          <label>Username</label>
          <input class="form-input" value="${escapeHtml(username)}" readonly>
        </div>
        <div class="form-group">
          <label>Temporary Password</label>
          <div style="display:flex;gap:8px">
            <input class="form-input" id="copy-temp-pass" value="${escapeHtml(tempPassword)}" readonly style="font-family:monospace">
            <button type="button" class="btn btn-secondary" id="btn-copy-temp">${icon('bx-copy')} Copy</button>
          </div>
        </div>
      `,
      footer: `<button type="button" class="btn btn-primary" data-modal-close>Done</button>`,
      onMount(root, close) {
        qs('#btn-copy-temp', root).addEventListener('click', () => {
          navigator.clipboard.writeText(tempPassword);
          toast('Password copied to clipboard!', 'success');
        });
      },
    });
  }

  // ==========================================
  // MODAL: EDIT USER
  // ==========================================
  function openEditUserModal(user) {
    openModal({
      title: `Edit User: @${escapeHtml(user.username)}`,
      size: 'medium',
      body: `
        <form id="edit-user-form" autocomplete="off">
          <div id="eu-error" style="display:none;margin-bottom:16px;padding:10px 14px;border-radius:10px;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.3);color:#ef4444;font-size:13px"></div>
          <div class="form-grid">
            <div class="form-group">
              <label for="eu-fullname">Full Name</label>
              <input class="form-input" id="eu-fullname" value="${escapeHtml(user.fullName || '')}">
            </div>
            <div class="form-group">
              <label for="eu-role">Role</label>
              <select class="form-input" id="eu-role">
                <option value="STAFF" ${user.role === 'STAFF' ? 'selected' : ''}>Staff</option>
                <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>Administrator</option>
              </select>
            </div>
          </div>
          <div class="form-grid">
            <div class="form-group">
              <label for="eu-email">Email</label>
              <input class="form-input" id="eu-email" type="email" value="${escapeHtml(user.email || '')}">
            </div>
            <div class="form-group">
              <label for="eu-phone">Phone</label>
              <input class="form-input" id="eu-phone" value="${escapeHtml(user.phone || '')}">
            </div>
          </div>
          <div class="form-group">
            <label for="eu-notes">Notes</label>
            <textarea class="form-input" id="eu-notes" rows="2">${escapeHtml(user.notes || '')}</textarea>
          </div>
        </form>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="submit" form="edit-user-form" class="btn btn-primary" id="eu-submit">Save Changes</button>
      `,
      onMount(root, close) {
        const form = qs('#edit-user-form', root);
        const errBox = qs('#eu-error', root);
        const submitBtn = qs('#eu-submit', root);

        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          errBox.style.display = 'none';

          const fullName = qs('#eu-fullname', root).value.trim();
          const role = qs('#eu-role', root).value;
          const email = qs('#eu-email', root).value.trim();
          const phone = qs('#eu-phone', root).value.trim();
          const notes = qs('#eu-notes', root).value.trim();

          busyButton(submitBtn, true, 'Saving...');
          try {
            await api.updateUser(user.id, {
              fullName,
              role,
              email: email || undefined,
              phone: phone || undefined,
              notes: notes || undefined,
            });
            toast(`User '${user.username}' updated.`, 'success');
            close();
            loadUsers();
          } catch (err) {
            errBox.textContent = err.message || 'Failed to update user.';
            errBox.style.display = 'block';
          } finally {
            busyButton(submitBtn, false);
          }
        });
      },
    });
  }

  // ==========================================
  // MODAL: RESET PASSWORD
  // ==========================================
  function openResetPasswordModal(user) {
    openModal({
      title: `Reset Password: @${escapeHtml(user.username)}`,
      size: 'narrow',
      body: `
        <form id="reset-pass-form" autocomplete="off">
          <p class="muted" style="font-size:13px;line-height:1.5;margin-bottom:16px">
            Resetting password will immediately invalidate all existing sessions and require the user to change their password upon their next login.
          </p>
          <div class="form-group">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" id="rp-auto" checked>
              <span>Generate secure temporary password</span>
            </label>
          </div>
          <div id="rp-custom-group" style="display:none" class="form-group">
            <label for="rp-custom-pass">Custom Password</label>
            <input class="form-input" id="rp-custom-pass" type="password" placeholder="At least 8 chars, 1 letter, 1 number">
          </div>
        </form>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="submit" form="reset-pass-form" class="btn btn-danger" id="rp-submit">Reset Password</button>
      `,
      onMount(root, close) {
        const form = qs('#reset-pass-form', root);
        const autoCheck = qs('#rp-auto', root);
        const customGroup = qs('#rp-custom-group', root);
        const customPass = qs('#rp-custom-pass', root);
        const submitBtn = qs('#rp-submit', root);

        autoCheck.addEventListener('change', () => {
          customGroup.style.display = autoCheck.checked ? 'none' : 'block';
          if (!autoCheck.checked) customPass.focus();
        });

        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          const isAuto = autoCheck.checked;
          const pass = customPass.value;

          busyButton(submitBtn, true, 'Resetting...');
          try {
            const res = await api.resetPassword(user.id, {
              generatePassword: isAuto,
              password: !isAuto ? pass : undefined,
            });
            close();
            showCreatedCredentialsModal(user.username, res.temporaryPassword);
          } catch (err) {
            toast(err.message, 'error');
          } finally {
            busyButton(submitBtn, false);
          }
        });
      },
    });
  }

  // ==========================================
  // MODAL: USER DETAILS & ACTIVITY DRAWER
  // ==========================================
  async function openUserDetailModal(userId) {
    openModal({
      title: 'User Details',
      size: 'large',
      body: `<div style="text-align:center;padding:40px" class="muted">${icon('bx-loader-alt', 'bx-spin')} Loading details...</div>`,
      footer: `<button type="button" class="btn btn-secondary" data-modal-close>Close</button>`,
      async onMount(root, close) {
        try {
          const detail = await api.user(userId);
          const u = detail.user;
          const act = detail.activity || {};
          const statusBadge = getStatusBadge(u.status);

          qs('.modal-body', root).innerHTML = `
            <div class="user-detail-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:16px;padding-bottom:16px;border-bottom:1px solid rgba(255,255,255,0.08)">
              <div style="display:flex;align-items:center;gap:14px">
                <div class="user-avatar" style="width:48px;height:48px;font-size:18px">${escapeHtml((u.fullName || u.username).substring(0, 2).toUpperCase())}</div>
                <div>
                  <h3 style="margin:0;font-size:18px">${escapeHtml(u.fullName || u.username)}</h3>
                  <div class="muted" style="font-size:13px">@${escapeHtml(u.username)} &bull; ${escapeHtml(u.role)} &bull; ${statusBadge}</div>
                </div>
              </div>
              <div style="display:flex;gap:8px">
                <button type="button" class="btn btn-secondary btn-sm" id="ud-force-logout-btn">${icon('bx-log-out-circle')} Force Logout</button>
              </div>
            </div>

            <div class="stats-grid" style="display:grid;grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));gap:14px;margin:20px 0">
              <div class="stat-card" style="padding:14px">
                <div class="stat-label">Active Sessions</div>
                <div class="stat-value" style="font-size:22px">${detail.activeSessionsCount || 0}</div>
              </div>
              <div class="stat-card" style="padding:14px">
                <div class="stat-label">Recharges Handled</div>
                <div class="stat-value" style="font-size:22px">${act.rechargesCount || 0}</div>
                <div class="muted" style="font-size:11px">₹ ${(act.rechargesTotal || 0).toLocaleString()}</div>
              </div>
              <div class="stat-card" style="padding:14px">
                <div class="stat-label">Check-ins Processed</div>
                <div class="stat-value" style="font-size:22px">${act.checkinsCount || 0}</div>
                <div class="muted" style="font-size:11px">₹ ${(act.checkinsTotal || 0).toLocaleString()}</div>
              </div>
            </div>

            <div class="drawer-tabs" style="margin-top:20px">
              <h4 style="font-size:15px;margin-bottom:10px">${icon('bx-devices')} Active Sessions</h4>
              <div id="ud-sessions-list" style="margin-bottom:24px">Loading sessions...</div>

              <h4 style="font-size:15px;margin-bottom:10px">${icon('bx-list-ul')} Recent Transactions Processed</h4>
              <div id="ud-transactions-list" style="margin-bottom:24px">Loading activity...</div>
            </div>
          `;

          // Load sessions
          loadUserSessions(userId, qs('#ud-sessions-list', root));
          // Load transactions
          renderUserTransactions(act.recentTransactions || [], qs('#ud-transactions-list', root));

          qs('#ud-force-logout-btn', root).addEventListener('click', async () => {
            const ok = await confirmDialog({
              title: 'Force Logout',
              message: `Revoke all active sessions for '${u.username}'?`,
              confirmLabel: 'Force Logout',
            });
            if (ok) {
              await api.forceLogoutUser(userId);
              toast('User logged out from all devices.', 'success');
              loadUserSessions(userId, qs('#ud-sessions-list', root));
            }
          });
        } catch (err) {
          qs('.modal-body', root).innerHTML = `<div class="text-danger" style="padding:30px;text-align:center">${escapeHtml(err.message)}</div>`;
        }
      },
    });
  }

  async function loadUserSessions(userId, container) {
    try {
      const sessions = await api.userSessions(userId);
      if (!sessions.length) {
        container.innerHTML = `<p class="muted" style="font-size:13px">No active sessions.</p>`;
        return;
      }
      container.innerHTML = `
        <table class="data-table" style="font-size:12px">
          <thead><tr><th>IP</th><th>Device / Agent</th><th>Last Active</th><th style="text-align:right">Action</th></tr></thead>
          <tbody>
            ${sessions.map((s) => `
              <tr>
                <td>${escapeHtml(s.ip || '—')}</td>
                <td style="max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escapeHtml(s.userAgent || 'Web browser')}</td>
                <td>${formatDate(s.lastSeenAt)}</td>
                <td style="text-align:right">
                  <button type="button" class="btn btn-sm btn-secondary revoke-sess-btn" data-sid="${s.id}">Revoke</button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;

      qsa('.revoke-sess-btn', container).forEach((btn) => {
        btn.addEventListener('click', async () => {
          const sid = btn.dataset.sid;
          await api.revokeUserSession(userId, sid);
          toast('Session revoked.', 'success');
          loadUserSessions(userId, container);
        });
      });
    } catch (err) {
      container.innerHTML = `<p class="text-danger" style="font-size:12px">${escapeHtml(err.message)}</p>`;
    }
  }

  function renderUserTransactions(txs, container) {
    if (!txs.length) {
      container.innerHTML = `<p class="muted" style="font-size:13px">No recent transactions recorded for this user.</p>`;
      return;
    }
    container.innerHTML = `
      <table class="data-table" style="font-size:12px">
        <thead><tr><th>Time</th><th>Customer</th><th>Type</th><th>Amount</th></tr></thead>
        <tbody>
          ${txs.map((t) => `
            <tr>
              <td>${formatDate(t.createdAt)}</td>
              <td>${escapeHtml(t.customerName)}</td>
              <td><span class="status-pill">${escapeHtml(t.type)}</span></td>
              <td style="font-weight:600">₹ ${Number(t.amount).toFixed(2)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;
  }

  // ==========================================
  // MODAL: CSV IMPORT
  // ==========================================
  function openImportModal() {
    let selectedFile = null;

    openModal({
      title: 'Import Users via CSV',
      subtitle: 'Upload a CSV file with columns: username, full_name, role, email, phone',
      size: 'large',
      body: `
        <div id="import-step-upload">
          <div class="drop-zone" id="csv-drop-zone" style="border:2px dashed rgba(255,255,255,0.2);border-radius:12px;padding:36px;text-align:center;cursor:pointer">
            ${icon('bx-file', 'upload-icon')}
            <div style="font-weight:600;margin-top:8px">Click to select or drag &amp; drop CSV file</div>
            <div class="muted" style="font-size:12px">Supports .csv files with header row</div>
            <input type="file" id="csv-file-input" accept=".csv" style="display:none">
          </div>
          <div id="import-file-name" class="muted" style="font-size:13px;margin-top:10px;text-align:center"></div>
        </div>

        <div id="import-step-preview" style="display:none;margin-top:16px">
          <div class="preview-stats" id="import-preview-stats" style="margin-bottom:12px;font-weight:600;font-size:13px"></div>
          <div style="max-height:280px;overflow-y:auto;border:1px solid rgba(255,255,255,0.08);border-radius:8px">
            <table class="data-table" style="font-size:12px">
              <thead><tr><th>Line</th><th>Username</th><th>Name</th><th>Role</th><th>Email</th><th>Validation</th></tr></thead>
              <tbody id="import-preview-body"></tbody>
            </table>
          </div>
        </div>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="button" class="btn btn-primary" id="btn-import-preview-next" disabled>Next: Preview</button>
        <button type="button" class="btn btn-primary" id="btn-import-commit" style="display:none">Confirm Import</button>
      `,
      onMount(root, close) {
        const dropZone = qs('#csv-drop-zone', root);
        const fileInput = qs('#csv-file-input', root);
        const fileNameDisplay = qs('#import-file-name', root);
        const nextBtn = qs('#btn-import-preview-next', root);
        const commitBtn = qs('#btn-import-commit', root);
        const uploadStep = qs('#import-step-upload', root);
        const previewStep = qs('#import-step-preview', root);
        const previewStats = qs('#import-preview-stats', root);
        const previewBody = qs('#import-preview-body', root);

        dropZone.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', () => {
          if (fileInput.files.length) {
            selectedFile = fileInput.files[0];
            fileNameDisplay.textContent = `Selected: ${selectedFile.name} (${(selectedFile.size / 1024).toFixed(1)} KB)`;
            nextBtn.disabled = false;
          }
        });

        nextBtn.addEventListener('click', async () => {
          if (!selectedFile) return;
          busyButton(nextBtn, true, 'Validating CSV...');
          try {
            const preview = await api.importUsersPreview(selectedFile);
            uploadStep.style.display = 'none';
            previewStep.style.display = 'block';
            nextBtn.style.display = 'none';
            commitBtn.style.display = 'inline-flex';
            commitBtn.disabled = preview.validRows === 0;

            previewStats.innerHTML = `Found ${preview.totalRows} row(s): <span class="text-success">${preview.validRows} valid</span>, <span class="text-danger">${preview.invalidRows} invalid</span>`;

            previewBody.innerHTML = preview.rows.map((r) => `
              <tr style="${r.valid ? '' : 'background:rgba(239,68,68,0.08)'}">
                <td>${r.lineNumber}</td>
                <td><strong>${escapeHtml(r.username)}</strong></td>
                <td>${escapeHtml(r.fullName || '—')}</td>
                <td>${escapeHtml(r.role)}</td>
                <td>${escapeHtml(r.email || '—')}</td>
                <td>${r.valid ? `<span class="text-success">${icon('bx-check')} Ready</span>` : `<span class="text-danger">${icon('bx-x')} ${escapeHtml(r.errorReason)}</span>`}</td>
              </tr>
            `).join('');
          } catch (err) {
            toast(err.message, 'error');
          } finally {
            busyButton(nextBtn, false);
          }
        });

        commitBtn.addEventListener('click', async () => {
          busyButton(commitBtn, true, 'Importing users...');
          try {
            const result = await api.importUsers(selectedFile);
            close();
            loadUsers();
            showImportResultsModal(result);
          } catch (err) {
            toast(err.message, 'error');
          } finally {
            busyButton(commitBtn, false);
          }
        });
      },
    });
  }

  function showImportResultsModal(result) {
    openModal({
      title: 'Import Completed',
      subtitle: `Successfully imported ${result.totalImported} users with temporary passwords.`,
      size: 'medium',
      body: `
        <div style="background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);padding:12px;border-radius:8px;margin-bottom:16px">
          ${icon('bx-alarm-exclamation')} <strong>Download or copy credentials now!</strong> Passwords will not be shown again.
        </div>
        <div style="max-height:260px;overflow-y:auto;border:1px solid rgba(255,255,255,0.08);border-radius:8px;margin-bottom:16px">
          <table class="data-table" style="font-size:12px">
            <thead><tr><th>Username</th><th>Name</th><th>Role</th><th>Temporary Password</th></tr></thead>
            <tbody>
              ${result.createdUsers.map((u) => `
                <tr>
                  <td><strong>${escapeHtml(u.username)}</strong></td>
                  <td>${escapeHtml(u.fullName || '—')}</td>
                  <td>${escapeHtml(u.role)}</td>
                  <td><code style="font-family:monospace">${escapeHtml(u.temporaryPassword)}</code></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" id="btn-download-import-csv">${icon('bx-download')} Download as CSV</button>
        <button type="button" class="btn btn-primary" data-modal-close>Done</button>
      `,
      onMount(root, close) {
        qs('#btn-download-import-csv', root).addEventListener('click', () => {
          let csv = 'Username,Full Name,Role,Temporary Password\n';
          result.createdUsers.forEach((u) => {
            csv += `"${u.username}","${u.fullName || ''}","${u.role}","${u.temporaryPassword}"\n`;
          });
          const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'imported_user_credentials.csv';
          a.click();
          URL.revokeObjectURL(url);
        });
      },
    });
  }

  // ==========================================
  // AUDIT LOGS VIEW
  // ==========================================
  async function loadAuditLogs() {
    auditBody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:40px" class="muted">${icon('bx-loader-alt', 'bx-spin')} Loading audit entries...</td></tr>`;
    try {
      const data = await api.auditLogs({
        actor: auditActor || undefined,
        action: auditAction || undefined,
        target: auditTarget || undefined,
        page: auditPage,
        size: 20,
      });

      const logs = data.content || [];
      auditTotalPages = data.totalPages || 1;

      if (!logs.length) {
        auditBody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:40px" class="muted">No audit entries found.</td></tr>`;
      } else {
        auditBody.innerHTML = logs.map((a) => `
          <tr>
            <td style="font-size:12px;white-space:nowrap">${formatDate(a.occurredAt)}</td>
            <td><strong>${escapeHtml(a.actorUsername)}</strong></td>
            <td><span class="status-pill">${escapeHtml(a.action)}</span></td>
            <td>${escapeHtml(a.targetUsername || '—')}</td>
            <td style="font-size:12px">${escapeHtml(a.ip || '—')}</td>
            <td>${a.success ? `<span class="text-success">${icon('bx-check')} OK</span>` : `<span class="text-danger">${icon('bx-x')} Failed</span>`}</td>
            <td style="font-size:12px;max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escapeHtml(a.details || '')}</td>
          </tr>
        `).join('');
      }

      auditPageNum.textContent = `${auditPage + 1} / ${Math.max(1, auditTotalPages)}`;
      auditPrev.disabled = auditPage <= 0;
      auditNext.disabled = auditPage >= auditTotalPages - 1;
    } catch (err) {
      auditBody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:30px" class="text-danger">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  qs('#audit-actor-filter', root).addEventListener('input', debounce((e) => {
    auditActor = e.target.value.trim();
    auditPage = 0;
    loadAuditLogs();
  }, 300));

  qs('#audit-action-filter', root).addEventListener('input', debounce((e) => {
    auditAction = e.target.value.trim();
    auditPage = 0;
    loadAuditLogs();
  }, 300));

  qs('#audit-target-filter', root).addEventListener('input', debounce((e) => {
    auditTarget = e.target.value.trim();
    auditPage = 0;
    loadAuditLogs();
  }, 300));

  auditPrev.addEventListener('click', () => {
    if (auditPage > 0) { auditPage--; loadAuditLogs(); }
  });

  auditNext.addEventListener('click', () => {
    if (auditPage < auditTotalPages - 1) { auditPage++; loadAuditLogs(); }
  });

  qs('#btn-export-audit', root).addEventListener('click', () => {
    const q = new URLSearchParams();
    if (auditActor) q.set('actor', auditActor);
    if (auditAction) q.set('action', auditAction);
    if (auditTarget) q.set('target', auditTarget);
    window.location.href = `/api/audit-log/export?${q.toString()}`;
  });

  // Helpers
  function calculatePasswordStrength(pass) {
    if (!pass) return 0;
    let score = 0;
    if (pass.length >= 8) score += 25;
    if (pass.length >= 12) score += 15;
    if (/[a-z]/.test(pass) && /[A-Z]/.test(pass)) score += 20;
    if (/\d/.test(pass)) score += 20;
    if (/[^a-zA-Z0-9]/.test(pass)) score += 20;
    return Math.min(100, score);
  }

  function updateStrengthUI(score, meter, text) {
    meter.style.width = `${score}%`;
    if (score < 40) {
      meter.style.background = '#ef4444';
      text.textContent = 'Weak';
      text.style.color = '#ef4444';
    } else if (score < 70) {
      meter.style.background = '#f59e0b';
      text.textContent = 'Moderate';
      text.style.color = '#f59e0b';
    } else {
      meter.style.background = '#10b981';
      text.textContent = 'Strong';
      text.style.color = '#10b981';
    }
  }

  // Initial load
  loadUsers();
}
