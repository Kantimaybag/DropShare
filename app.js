/**
 * Precision Transfer / DropShare Frontend Application
 * Built following DESIGN.md specifications
 */

const API_BASE = '/api/v1';

// Kept in memory only. Never save the sender credential in localStorage.
let activeResendContext = null;
let resendCountdownInterval = null;

function getShareTokenFromUrl() {
  const path = window.location.pathname;
  const match = path.match(/^\/(?:d|share)\/([A-Za-z0-9_-]+)/);
  if (match) return match[1];

  const urlParams = new URLSearchParams(window.location.search);
  return urlParams.get('token') || urlParams.get('d');
}

// Render a real QR code using QRCode.js (loaded from index.html).
// If the library is unavailable, show a clear fallback instead of a fake QR pattern.
function renderShareQRCode(container, url) {
  if (!container) return;
  container.replaceChildren();

  if (typeof QRCode === 'undefined') {
    const note = document.createElement('p');
    note.className = 'qr-unavailable-message';
    note.textContent = 'QR code unavailable. Copy the share link instead.';
    container.appendChild(note);
    return;
  }

  new QRCode(container, {
    text: url,
    width: 180,
    height: 180,
    colorDark: '#0f172a',
    colorLight: '#ffffff',
    correctLevel: QRCode.CorrectLevel.M
  });
}

// Helpers
function formatBytes(bytes, decimals = 1) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function getFileExtension(filename) {
  const parts = filename.split('.');
  return parts.length > 1 ? parts.pop().toUpperCase() : 'FILE';
}

function truncateString(str, num = 24) {
  if (str.length <= num) return str;
  return str.slice(0, num - 3) + '...';
}

// Escape values before placing them in HTML attributes or text templates.
function escapeHTML(value) {
  return String(value ?? '').replace(/[&<>\"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '\"': '&quot;',
    "'": '&#39;'
  })[character]);
}

// SHA-256 fingerprint generator
async function computeHash(content) {
  try {
    let buffer;
    if (typeof content === 'string') {
      buffer = new TextEncoder().encode(content);
    } else if (content instanceof Blob) {
      buffer = await content.arrayBuffer();
    } else {
      buffer = new Uint8Array([1, 2, 3, 4]);
    }
    const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 12);
  } catch (e) {
    return Math.random().toString(16).substring(2, 14);
  }
}

// Toast System
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  let iconSvg = '';
  if (type === 'success') {
    iconSvg = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>`;
  } else if (type === 'error') {
    iconSvg = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`;
  } else {
    iconSvg = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;
  }

  // The icon is application-owned SVG. Message text is always inserted safely.
  toast.innerHTML = iconSvg;
  const messageSpan = document.createElement('span');
  messageSpan.textContent = String(message ?? '');
  toast.appendChild(messageSpan);

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(12px) scale(0.96)';
    setTimeout(() => toast.remove(), 250);
  }, 3200);
}

// App State
const state = {
  activeTab: 'files', // 'files' | 'text'
  selectedFiles: [],
  snippetText: '',
  snippetFormat: 'text',
  expiration: '24h',
  isPasswordProtected: false,
  password: '',
  isE2EE: false,
  isTransferring: false,
  recentDrops: [],
  activeSession: null
};

// LocalStorage Persistence for Audit Logs
// Load Transfer History
function loadRecentDrops() {
    try {
        const saved = localStorage.getItem('precision_transfers');

        if (saved) {
            const parsed = JSON.parse(saved);
            state.recentDrops = Array.isArray(parsed) ? parsed : [];

            // Remove demo rows and any plaintext OTP accidentally stored by older versions.
            state.recentDrops = state.recentDrops
              .filter(drop => drop && drop.id !== 'dx_89c2fa' && drop.id !== 'dx_31e90b')
              .map(({ otp, ...drop }) => drop);

            saveRecentDrops();

        } else {
            // Start with empty history
            state.recentDrops = [];
        }

    } catch (error) {
        state.recentDrops = [];
    }
}

function saveRecentDrops() {
  try { localStorage.setItem('precision_transfers', JSON.stringify(state.recentDrops)); }
  catch (e) { console.error('Failed to save transfers to local storage', e); }
}

function saveActiveSession() {
  try {
    if (state.activeSession) sessionStorage.setItem('dropshare_active_session', JSON.stringify(state.activeSession));
    else sessionStorage.removeItem('dropshare_active_session');
  } catch (error) { console.warn('Could not persist sender session in this tab.', error); }
}

function loadActiveSession() {
  try {
    const value = sessionStorage.getItem('dropshare_active_session');
    state.activeSession = value ? JSON.parse(value) : null;
    if (state.activeSession?.token && state.activeSession?.resendSecret) {
      activeResendContext = {
        token: state.activeSession.token,
        secret: state.activeSession.resendSecret,
        availableAt: Number(state.activeSession.otpAvailableAt) || Date.now()
      };
    }
  } catch (error) {
    state.activeSession = null;
  }
}

async function refreshSessionManagement() {
  if (!state.activeSession) return;
  const { token, ownerSecret } = state.activeSession;
  try {
    const response = await fetch(`${API_BASE}/shares/${encodeURIComponent(token)}/manage`, {
      headers: { 'X-Share-Owner': ownerSecret }
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success || !result.data) throw new Error(result.message || 'Session details unavailable.');
    const data = result.data;
    const drop = state.recentDrops.find(item => item.token === token);
    if (drop) {
      const files = Array.isArray(data.files) ? data.files : [];
      const texts = Array.isArray(data.textEntries) ? data.textEntries : [];
      drop.title = `${files.length} file(s), ${texts.length} text entr(y/ies)`;
      drop.type = 'MIXED';
      drop.size = formatBytes(files.reduce((sum, file) => sum + (Number(file.fileSize) || 0), 0)) + ` • ${texts.length} text entr(y/ies)`;
      drop.downloads = `${data.downloadCount}/${data.maxDownloads}`;
      drop.status = data.status;
      drop.contents = [...files.map(file => file.originalFilename), ...texts.map(entry => entry.title)];
      saveRecentDrops();
      renderAuditTable();
    }
  } catch (error) { console.info('Session management is unavailable:', error.message); }
  await refreshAuditLog();
}

async function refreshAuditLog() {
  if (!state.activeSession) return;
  const { token, ownerSecret } = state.activeSession;
  try {
    const response = await fetch(`${API_BASE}/shares/${encodeURIComponent(token)}/audit`, {
      headers: { 'X-Share-Owner': ownerSecret }
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success || !Array.isArray(result.data)) return;
    renderAuditEvents(result.data);
  } catch (error) { console.info('Audit log is unavailable:', error.message); }
}

function renderAuditEvents(events) {
  let section = document.getElementById('real-audit-log');
  if (!section) {
    section = document.createElement('section');
    section.id = 'real-audit-log';
    section.className = 'section-card';
    const anchor = document.querySelector('.recent-transfers-section');
    if (anchor) anchor.appendChild(section);
  }
  if (!section) return;
  section.innerHTML = `
    <div class="section-header"><div class="section-title">Access Log <span class="section-badge">${events.length} events</span></div><button type="button" id="refresh-audit-log" class="btn btn-ghost btn-sm">Refresh</button></div>
    <div>
      ${events.length ? events.map(event => `
        <div style="display:flex;justify-content:space-between;gap:16px;padding:12px 0;border-bottom:1px solid var(--border-subtle);">
          <div><strong>${escapeHTML(event.eventType)}</strong><div style="font-size:12px;color:var(--slate-500);">${escapeHTML(event.description)}</div></div>
          <time style="font-size:11px;color:var(--slate-500);white-space:nowrap;">${escapeHTML(new Date(event.createdAt).toLocaleString())}</time>
        </div>`).join('') : '<p style="color:var(--slate-500);padding:12px 0;">No activity recorded yet.</p>'}
    </div>`;
  section.querySelector('#refresh-audit-log')?.addEventListener('click', refreshAuditLog);
}

// DOM Elements & Initialization
document.addEventListener('DOMContentLoaded', () => {
  loadRecentDrops();
  loadActiveSession();
  initTabs();
  initDropzone();
  initSnippetArea();
  initOptionsAccordion();
  initActionButtons();
  initModal();
  initReceiverMode();
  updateSecurityLabels();
  renderQueue();
  renderAuditTable();
  if (state.activeSession) refreshSessionManagement();

  // A /d/{token}, /share/{token}, or ?token=... URL opens Receiver mode.
  if (getShareTokenFromUrl()) {
    switchToReceiverView();
  }
});

// The current upload flow does not encrypt files client-side. Keep the UI honest.
function updateSecurityLabels() {
  const encryptionLabel = document.querySelector('#enable-e2ee')?.parentElement?.querySelector('span');
  if (encryptionLabel) encryptionLabel.textContent = 'Client-side encryption is not implemented yet';

  document.querySelectorAll('.dropzone-limits .format-tag').forEach(tag => {
    if (tag.textContent.trim().toUpperCase() === 'CLIENT ENCRYPTED') {
      tag.textContent = 'OTP PROTECTED';
    }
  });

  const dropzoneHint = document.querySelector('.dropzone-hint');
  if (dropzoneHint) {
    dropzoneHint.textContent = 'Drag and drop files or browse your system. Files are uploaded to the ShareDrop backend.';
  }
}

// Tab Switcher
function initTabs() {
  const tabFiles = document.getElementById('tab-files-btn');
  const tabText = document.getElementById('tab-text-btn');
  const viewFiles = document.getElementById('view-files');
  const viewText = document.getElementById('view-text');

  tabFiles.addEventListener('click', () => {
    state.activeTab = 'files';
    tabFiles.classList.add('active');
    tabText.classList.remove('active');
    viewFiles.style.display = 'block';
    viewText.style.display = 'none';
    updateActionState();
  });

  tabText.addEventListener('click', () => {
    state.activeTab = 'text';
    tabText.classList.add('active');
    tabFiles.classList.remove('active');
    viewFiles.style.display = 'none';
    viewText.style.display = 'block';
    updateActionState();
  });
}

// Dropzone & File Handling
function initDropzone() {
  const dropzone = document.getElementById('dropzone');
  const fileInput = document.getElementById('file-input');

  dropzone.addEventListener('click', () => {
    fileInput.click();
  });

  fileInput.addEventListener('change', (e) => {
    handleSelectedFiles(Array.from(e.target.files));
    fileInput.value = '';
  });

  ['dragenter', 'dragover'].forEach(evtName => {
    dropzone.addEventListener(evtName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropzone.classList.add('drag-active');
    });
  });

  ['dragleave', 'drop'].forEach(evtName => {
    dropzone.addEventListener(evtName, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropzone.classList.remove('drag-active');
    });
  });

  dropzone.addEventListener('drop', (e) => {
    if (e.dataTransfer && e.dataTransfer.files) {
      handleSelectedFiles(Array.from(e.dataTransfer.files));
    }
  });
}

async function handleSelectedFiles(files) {
  for (const file of files) {
    const hash = await computeHash(file);
    state.selectedFiles.push({
      id: Math.random().toString(36).substring(2, 9),
      name: file.name,
      size: file.size,
      formattedSize: formatBytes(file.size),
      ext: getFileExtension(file.name),
      hash: hash,
      progress: 0,
      fileRef: file
    });
  }
  renderQueue();
  updateActionState();
}

function removeFile(fileId) {
  state.selectedFiles = state.selectedFiles.filter(f => f.id !== fileId);
  renderQueue();
  updateActionState();
}

// Render File Queue
function renderQueue() {
  const queueSection = document.getElementById('queue-section');
  const queueList = document.getElementById('queue-list');
  const queueCount = document.getElementById('queue-count');
  if (!queueSection || !queueList || !queueCount) return;

  if (state.selectedFiles.length === 0) {
    queueSection.style.display = 'none';
    queueList.replaceChildren();
    return;
  }

  queueSection.style.display = 'flex';
  queueCount.textContent = `${state.selectedFiles.length} ${state.selectedFiles.length === 1 ? 'FILE' : 'FILES'}`;

  queueList.innerHTML = state.selectedFiles.map(file => `
    <div class="file-row" id="row-${escapeHTML(file.id)}">
      <div class="file-badge" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
          <polyline points="13 2 13 9 20 9"></polyline>
        </svg>
      </div>
      <div class="file-details">
        <div class="file-top-row">
          <span class="file-name" title="${escapeHTML(file.name)}">${escapeHTML(file.name)}</span>
          <span class="file-progress-label" id="prog-label-${escapeHTML(file.id)}">${file.progress > 0 ? `${file.progress}%` : ''}</span>
        </div>
        <div class="file-bottom-row">
          <span class="format-tag">${escapeHTML(file.ext)}</span>
          <span class="file-size">${escapeHTML(file.formattedSize)}</span>
          <span class="file-hash">#${escapeHTML(file.hash)}</span>
        </div>
      </div>
      <button type="button" class="file-remove-btn" data-remove-file="${escapeHTML(file.id)}" title="Remove file" aria-label="Remove ${escapeHTML(file.name)}">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>
      <div class="file-progress-track">
        <div class="file-progress-bar" id="prog-bar-${escapeHTML(file.id)}" style="width: ${Number(file.progress) || 0}%"></div>
      </div>
    </div>
  `).join('');

  queueList.querySelectorAll('[data-remove-file]').forEach(button => {
    button.addEventListener('click', () => removeFile(button.dataset.removeFile));
  });
}

// Secret Text / Snippet Area
function initSnippetArea() {
  const textarea = document.getElementById('snippet-input');
  const formatSelect = document.getElementById('snippet-format');
  const lineCount = document.getElementById('snippet-line-count');
  const charCount = document.getElementById('snippet-char-count');
  const copyBtn = document.getElementById('snippet-copy-quick');

  textarea.addEventListener('input', () => {
    state.snippetText = textarea.value;
    const lines = state.snippetText ? state.snippetText.split('\n').length : 0;
    const chars = state.snippetText.length;
    lineCount.textContent = `${lines} ${lines === 1 ? 'line' : 'lines'}`;
    charCount.textContent = `${chars} chars`;
    updateActionState();
  });

  formatSelect.addEventListener('change', (e) => {
    state.snippetFormat = e.target.value;
  });

  copyBtn.addEventListener('click', async () => {
    if (!state.snippetText) {
      showToast('Snippet is empty', 'warning');
      return;
    }
    try {
      await navigator.clipboard.writeText(state.snippetText);
      showToast('Snippet copied to clipboard', 'success');
    } catch (e) {
      showToast('Failed to copy', 'error');
    }
  });
}

// Options Accordion & Controls
function initOptionsAccordion() {
  const header = document.getElementById('options-toggle');
  const content = document.getElementById('options-content');
  const arrow = document.getElementById('options-arrow');

  header.addEventListener('click', () => {
    const isClosed = content.style.display === 'none';
    content.style.display = isClosed ? 'flex' : 'none';
    arrow.style.transform = isClosed ? 'rotate(180deg)' : 'rotate(0deg)';
  });

  // Expiration Radios
  document.querySelectorAll('input[name="expiration"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      state.expiration = e.target.value;
    });
  });

  // Password Protection
  const passCheckbox = document.getElementById('enable-password');
  const passInput = document.getElementById('password-input');

  passCheckbox.addEventListener('change', (e) => {
    state.isPasswordProtected = e.target.checked;
    passInput.style.display = e.target.checked ? 'block' : 'none';
    if (e.target.checked) passInput.focus();
  });

  passInput.addEventListener('input', (e) => {
    state.password = e.target.value;
  });

  // E2EE is not implemented in this frontend. Do not let the UI claim otherwise.
  const e2eCheckbox = document.getElementById('enable-e2ee');
  if (e2eCheckbox) {
    e2eCheckbox.checked = false;
    e2eCheckbox.disabled = true;
    state.isE2EE = false;
  }
}

// Update Primary Action button state
function updateActionState() {
  const createBtn = document.getElementById('create-transfer-btn');
  let hasContent = false;

  if (state.activeTab === 'files') {
    hasContent = state.selectedFiles.length > 0;
  } else {
    hasContent = state.snippetText.trim().length > 0;
  }

  createBtn.disabled = !hasContent || state.isTransferring;
  const actionLabel = createBtn.querySelector('span');
  if (actionLabel) actionLabel.textContent = state.activeSession ? 'Add to Existing Session' : 'Create Sharing Session';
  if (!hasContent) {
    createBtn.style.opacity = '0.6';
    createBtn.style.cursor = 'not-allowed';
  } else {
    createBtn.style.opacity = '1';
    createBtn.style.cursor = 'pointer';
  }
}

// Action Execution
function initActionButtons() {
  const createBtn = document.getElementById('create-transfer-btn');
  createBtn.addEventListener('click', executeTransfer);
}

async function executeTransfer() {
  if (state.isTransferring) return;
  const createBtn = document.getElementById('create-transfer-btn');
  if (!createBtn) return;
  const hasFiles = state.activeTab === 'files' && state.selectedFiles.length > 0;
  const hasText = state.activeTab === 'text' && state.snippetText.trim().length > 0;
  if (!hasFiles && !hasText) {
    showToast(state.activeTab === 'files' ? 'Select at least one file.' : 'Enter some text first.', 'error');
    return;
  }

  state.isTransferring = true;
  updateActionState();
  const originalBtnHTML = createBtn.innerHTML;
  createBtn.disabled = true;
  createBtn.textContent = state.activeSession ? 'Adding to session...' : 'Creating session...';

  try {
    let response;
    const session = state.activeSession;
    if (session) {
      if (hasFiles) {
        const formData = new FormData();
        state.selectedFiles.forEach(file => { if (file.fileRef) formData.append('files', file.fileRef, file.name); });
        response = await fetch(`${API_BASE}/shares/${encodeURIComponent(session.token)}/files`, {
          method: 'POST', headers: { 'X-Share-Owner': session.ownerSecret }, body: formData
        });
      } else {
        response = await fetch(`${API_BASE}/shares/${encodeURIComponent(session.token)}/texts`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Share-Owner': session.ownerSecret },
          body: JSON.stringify({
            title: `Text entry ${Date.now()}`, textContent: state.snippetText,
            expiration: state.expiration, snippetFormat: state.snippetFormat
          })
        });
      }
      const appendResult = await response.json().catch(() => ({}));
      if (!response.ok || !appendResult.success) throw new Error(appendResult.message || `Could not add content (HTTP ${response.status}).`);
      await refreshSessionManagement();
      showToast('Content added. The session link and OTP remain unchanged.', 'success');
    } else {
      if (hasFiles) {
        const formData = new FormData();
        state.selectedFiles.forEach(file => { if (file.fileRef) formData.append('files', file.fileRef, file.name); });
        formData.append('expiration', state.expiration);
        if (state.isPasswordProtected && state.password.trim()) formData.append('password', state.password);
        response = await fetch(`${API_BASE}/shares/file`, { method: 'POST', body: formData });
      } else {
        response = await fetch(`${API_BASE}/shares/text`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: 'Text entry',
            textContent: state.snippetText,
            expiration: state.expiration,
            snippetFormat: state.snippetFormat,
            password: state.isPasswordProtected && state.password.trim() ? state.password : null
          })
        });
      }
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success || !result.data) throw new Error(result.message || `Transfer failed (HTTP ${response.status}).`);
      const data = result.data;
      if (!data.shareToken || !data.otp || !data.ownerSecret || !data.resendSecret) {
        throw new Error('The server response is missing sender session credentials.');
      }
      // Prefer the backend-generated receiver URL so links work when the UI
      // and API are deployed on different paths or hosts.
      let shareUrl = data.shareUrl;
      if (!shareUrl) {
        const frontendShareUrl = new URL(window.location.href);
        frontendShareUrl.searchParams.set('token', data.shareToken);
        frontendShareUrl.hash = '';
        shareUrl = frontendShareUrl.toString();
      }
      state.activeSession = {
        token: data.shareToken, ownerSecret: data.ownerSecret, resendSecret: data.resendSecret,
        createdAt: Date.now(), otpAvailableAt: Date.now() + 10 * 60 * 1000
      };
      saveActiveSession();
      const newDrop = {
        id: data.shareToken.substring(0, 10),
        title: hasFiles ? `${state.selectedFiles.length} file(s)` : '1 text entry',
        type: 'MIXED',
        size: hasFiles ? formatBytes(state.selectedFiles.reduce((sum, file) => sum + file.size, 0)) : `${state.snippetText.length} chars`,
        timestamp: 'Just now',
        expiresIn: state.expiration === 'burn' ? 'Burn after 1 download' : state.expiration,
        hasPassword: state.isPasswordProtected, downloads: state.expiration === 'burn' ? '0/1' : '0/100',
        url: shareUrl, token: data.shareToken, status: 'ACTIVE'
      };
      state.recentDrops = state.recentDrops.filter(item => item.token !== data.shareToken);
      state.recentDrops.unshift(newDrop);
      saveRecentDrops();
      activeResendContext = { token: data.shareToken, secret: data.resendSecret, availableAt: Date.now() + 10 * 60 * 1000 };
      openShareModal({ ...newDrop, otp: data.otp });
      await refreshSessionManagement();
      showToast('Session created. Add more files or text using the same link and OTP.', 'success');
    }

    state.selectedFiles = [];
    renderQueue();
    const textarea = document.getElementById('snippet-input');
    if (textarea) textarea.value = '';
    state.snippetText = '';
    const passwordInput = document.getElementById('password-input');
    if (passwordInput) passwordInput.value = '';
    state.password = '';
    updateActionState();
  } catch (error) {
    console.error('ShareDrop session error:', error);
    if (state.activeSession && /no longer active|expired|download limit|revoked/i.test(String(error.message))) {
      state.activeSession = null;
      activeResendContext = null;
      saveActiveSession();
      updateActionState();
      showToast('This session has ended. Your next upload will create a new session.', 'warning');
    } else {
      showToast(error.message || 'Could not connect to the ShareDrop backend.', 'error');
    }
  } finally {
    state.isTransferring = false;
    createBtn.innerHTML = originalBtnHTML;
    updateActionState();
  }
}

// Modal handling
function initModal() {
  const modalBackdrop = document.getElementById('share-modal');
  const closeBtn = document.getElementById('modal-close-btn');
  const doneBtn = document.getElementById('modal-done-btn');

  closeBtn.addEventListener('click', closeShareModal);
  doneBtn.addEventListener('click', closeShareModal);

  modalBackdrop.addEventListener('click', (e) => {
    if (e.target === modalBackdrop) closeShareModal();
  });

  const copyUrlBtn = document.getElementById('modal-copy-url');
  const copyCurlBtn = document.getElementById('modal-copy-curl');
  const copyOtpBtn = document.getElementById('modal-copy-otp');

  if (copyOtpBtn) {
    copyOtpBtn.addEventListener('click', async () => {
      const otpInput = document.getElementById('modal-otp-value');
      try {
        await navigator.clipboard.writeText(otpInput.value);
        showToast('OTP copied to clipboard', 'success');
      } catch (e) {
        showToast('Could not copy OTP', 'error');
      }
    });
  }

  copyUrlBtn.addEventListener('click', async () => {
    const shareInput = document.getElementById('modal-share-url');
    try {
      await navigator.clipboard.writeText(shareInput.value);
      showToast('Link copied to clipboard', 'success');
    } catch (e) {
      showToast('Could not copy link', 'error');
    }
  });

  copyCurlBtn.addEventListener('click', async () => {
    const curlEl = document.getElementById('modal-curl-command');
    try {
      await navigator.clipboard.writeText(curlEl.innerText);
      showToast('cURL command copied', 'success');
    } catch (e) {
      showToast('Could not copy command', 'error');
    }
  });
}

function ensureResendOtpControls(otpSection) {
  if (!otpSection) return;
  let status = document.getElementById('modal-resend-status');
  if (!status) {
    status = document.createElement('p');
    status.id = 'modal-resend-status';
    status.style.cssText = 'margin:8px 0 0;font-size:12px;color:var(--slate-500);';
    otpSection.appendChild(status);
  }
  let button = document.getElementById('modal-resend-otp');
  if (!button) {
    button = document.createElement('button');
    button.type = 'button';
    button.id = 'modal-resend-otp';
    button.className = 'btn btn-secondary';
    button.style.cssText = 'margin-top:10px;width:100%;';
    button.textContent = 'Resend OTP';
    otpSection.appendChild(button);
    button.addEventListener('click', resendCurrentOtp);
  }
  updateResendOtpControls();
}

function updateResendOtpControls() {
  const button = document.getElementById('modal-resend-otp');
  const status = document.getElementById('modal-resend-status');
  if (!button || !status) return;

  if (!activeResendContext || !activeResendContext.secret) {
    button.disabled = true;
    status.textContent = 'OTP resend is available only in the original sender session.';
    return;
  }

  const remaining = Math.ceil((activeResendContext.availableAt - Date.now()) / 1000);
  if (remaining > 0) {
    button.disabled = true;
    const minutes = Math.floor(remaining / 60);
    const seconds = remaining % 60;
    status.textContent = `You can resend a new OTP in ${minutes}:${String(seconds).padStart(2, '0')}.`;
  } else {
    button.disabled = false;
    status.textContent = 'The OTP has expired. You can now generate a replacement.';
  }
}

async function resendCurrentOtp() {
  const button = document.getElementById('modal-resend-otp');
  const otpInput = document.getElementById('modal-otp-value');
  const copyOtpButton = document.getElementById('modal-copy-otp');
  if (!activeResendContext || !activeResendContext.secret || !button) return;

  button.disabled = true;
  button.textContent = 'Generating...';
  try {
    const response = await fetch(`${API_BASE}/shares/${encodeURIComponent(activeResendContext.token)}/resend-otp`, {
      method: 'POST',
      headers: { 'X-Resend-Secret': activeResendContext.secret }
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success || !result.data) {
      throw new Error(result.message || 'Could not resend OTP.');
    }

    otpInput.value = result.data.otp;
    activeResendContext.secret = result.data.resendSecret;
    activeResendContext.availableAt = Date.now() + 10 * 60 * 1000;
    if (state.activeSession?.token === activeResendContext.token) {
      state.activeSession.resendSecret = result.data.resendSecret;
      state.activeSession.otpAvailableAt = activeResendContext.availableAt;
      saveActiveSession();
    }
    if (copyOtpButton) copyOtpButton.disabled = false;
    showToast('New OTP generated. Share it with the recipient.', 'success');
  } catch (error) {
    showToast(error.message || 'Could not resend OTP.', 'error');
  } finally {
    button.textContent = 'Resend OTP';
    updateResendOtpControls();
  }
}

function openShareModal(drop) {
  const modal = document.getElementById('share-modal');
  const shareInput = document.getElementById('modal-share-url');
  const qrContainer = document.getElementById('modal-qr-code');
  const curlCommand = document.getElementById('modal-curl-command');
  const modalSubtitle = document.getElementById('modal-drop-meta');
  const otpInput = document.getElementById('modal-otp-value');
  const otpSection = document.getElementById('modal-otp-section');

  if (!modal || !shareInput) return;

  shareInput.value = drop.url;
  if (modalSubtitle) {
    modalSubtitle.textContent = `${drop.title} • ${drop.size} • Expires in ${drop.expiresIn}`;
  }

  // The backend does not expose the old /raw endpoint. Avoid showing a broken cURL command.
  if (curlCommand) {
    curlCommand.textContent = 'Use the share link in a browser. Download requires OTP verification.';
  }
  const copyCurlBtn = document.getElementById('modal-copy-curl');
  if (copyCurlBtn) copyCurlBtn.disabled = true;

  if (otpInput && drop.otp) {
    otpInput.value = drop.otp;
    if (otpSection) otpSection.style.display = 'block';
  } else {
    if (otpInput) otpInput.value = '';
    if (otpSection) otpSection.style.display = activeResendContext?.token === drop.token ? 'block' : 'none';
  }

  renderShareQRCode(qrContainer, drop.url);
  ensureResendOtpControls(otpSection);
  if (resendCountdownInterval) clearInterval(resendCountdownInterval);
  resendCountdownInterval = setInterval(updateResendOtpControls, 1000);
  modal.classList.add('open');
}

function closeShareModal() {
  const modal = document.getElementById('share-modal');
  modal.classList.remove('open');
  if (resendCountdownInterval) { clearInterval(resendCountdownInterval); resendCountdownInterval = null; }
}

// Render Audit Logs / Recent Drops Table
function renderAuditTable() {
  const tbody = document.getElementById('audit-table-body');
  const badgeCount = document.getElementById('history-count');
  if (!tbody) return;

  if (badgeCount) badgeCount.textContent = `${state.recentDrops.length} drops`;

  if (state.recentDrops.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" style="text-align:center;color:var(--slate-400);padding:32px 16px;">
          No active drops in this workspace session yet.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.recentDrops.map(drop => `
    <tr>
      <td><span class="code-token">${escapeHTML(drop.id)}</span></td>
      <td>
        <div style="display:flex;align-items:center;gap:8px;">
          <span class="format-tag">${escapeHTML(drop.type)}</span>
          <span style="font-weight:500;color:var(--slate-900);">${escapeHTML(truncateString(drop.title, 28))}</span>
        </div>
        ${Array.isArray(drop.contents) && drop.contents.length ? `<div style="font-size:11px;color:var(--slate-500);margin-top:4px;">${drop.contents.map(item => escapeHTML(item)).join(" · ")}</div>` : ""}
      </td>
      <td><span style="color:var(--slate-500);font-family:var(--font-mono);font-size:12px;">${escapeHTML(drop.size)}</span></td>
      <td>
        <span class="${String(drop.expiresIn).toLowerCase().includes('burn') || String(drop.expiresIn).includes('10m') ? 'badge-expiring' : 'badge-active'}">
          <span class="status-dot"></span>${escapeHTML(drop.status && drop.status !== "ACTIVE" ? drop.status : drop.expiresIn)}
        </span>
      </td>
      <td>
        <div style="display:flex;align-items:center;gap:6px;">
          <span title="${drop.hasPassword ? 'Password protected' : 'OTP protected'}" style="color:var(--primary);">
            ${drop.hasPassword ? 'Password protected' : 'OTP protected'}
          </span>
          <span style="font-size:11px;color:var(--slate-500);font-family:var(--font-mono);">${escapeHTML(drop.downloads)}</span>
        </div>
      </td>
      <td>
        <div style="display:flex;align-items:center;gap:6px;justify-content:flex-end;">
          <button type="button" class="btn btn-ghost btn-sm" data-copy-drop="${escapeHTML(drop.id)}" title="Copy Drop Link">Copy</button>
          <button type="button" class="btn btn-secondary btn-sm" data-manage-drop="${escapeHTML(drop.id)}" ${state.activeSession?.token === drop.token ? '' : 'disabled'} title="Manage session">Manage</button>
          <button type="button" class="btn btn-destructive btn-sm" data-revoke-drop="${escapeHTML(drop.id)}" ${state.activeSession?.token === drop.token ? '' : 'disabled'} title="Revoke and destroy drop">Revoke</button>
        </div>
      </td>
    </tr>
  `).join('');

  tbody.querySelectorAll('[data-copy-drop]').forEach(button => {
    button.addEventListener('click', () => {
      const drop = state.recentDrops.find(item => item.id === button.dataset.copyDrop);
      if (drop) copyDropLink(drop.url);
    });
  });

  tbody.querySelectorAll('[data-revoke-drop]').forEach(button => {
    button.addEventListener('click', () => revokeDrop(button.dataset.revokeDrop));
  });
  tbody.querySelectorAll('[data-manage-drop]').forEach(button => {
    button.addEventListener('click', () => openExistingSession(button.dataset.manageDrop));
  });
}

function openExistingSession(dropId) {
  const drop = state.recentDrops.find(item => item.id === dropId);
  if (!drop || !state.activeSession || state.activeSession.token !== drop.token) {
    showToast('Open the original sender browser tab to manage this session.', 'error');
    return;
  }
  activeResendContext = {
    token: state.activeSession.token,
    secret: state.activeSession.resendSecret,
    availableAt: Number(state.activeSession.otpAvailableAt) || Date.now()
  };
  openShareModal(drop);
}


async function copyDropLink(url) {
  try {
    await navigator.clipboard.writeText(url);
    showToast('Drop link copied to clipboard', 'success');
  } catch (error) {
    showToast('Failed to copy the link', 'error');
  }
}

async function revokeDrop(dropId) {
  const drop = state.recentDrops.find(item => item.id === dropId);
  if (!drop) return;

  try {
    if (drop.token) {
      const response = await fetch(`${API_BASE}/shares/${encodeURIComponent(drop.token)}/revoke`, {
        method: 'POST',
        headers: { 'X-Share-Owner': state.activeSession?.ownerSecret || '' }
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) {
        throw new Error(result.message || 'The server could not revoke this share.');
      }
    }

    drop.status = 'REVOKED';
    saveRecentDrops();
    await refreshAuditLog();
    if (state.activeSession?.token === drop.token) { state.activeSession = null; saveActiveSession(); activeResendContext = null; }
    renderAuditTable();
    showToast('Session revoked. Its contents are queued for destruction.', 'success');
  } catch (error) {
    console.error('Revoke failed:', error);
    showToast(error.message || 'Could not revoke the share. Try again.', 'error');
  }
}

// Keep compatibility with any existing markup that still calls these functions.
window.removeFile = removeFile;
window.copyDropLink = copyDropLink;
window.revokeDrop = revokeDrop;

// =====================================
// RECEIVER MODE
// =====================================

let receiveModeBtn;
let receiverView;
let senderHero;
let senderStage;
let backToSenderBtn;
let otpForm;
let otpInputs;
let receiverMessage;
let verifyOtpBtn;
let receiverFilesSection;
let receiverFileList;
let receiveAnotherBtn;
let receiverPasswordInput;

function initReceiverMode() {
  receiveModeBtn = document.getElementById('receive-mode-btn');
  receiverView = document.getElementById('receiver-view');
  senderHero = document.querySelector('.hero');
  senderStage = document.querySelector('.transfer-stage');
  backToSenderBtn = document.getElementById('back-to-sender-btn');
  otpForm = document.getElementById('receiver-otp-form');
  otpInputs = Array.from(document.querySelectorAll('.receiver-otp'));
  receiverMessage = document.getElementById('receiver-message');
  verifyOtpBtn = document.getElementById('verify-otp-btn');
  receiverFilesSection = document.getElementById('receiver-files-section');
  receiverFileList = document.getElementById('receiver-file-list');
  receiveAnotherBtn = document.getElementById('receive-another-btn');

  if (!receiverView || !otpForm || !verifyOtpBtn || otpInputs.length !== 6) {
    console.error('Receiver UI is incomplete. Check receiver-view and its six OTP inputs in index.html.');
    return;
  }

  // Add the optional password field through JavaScript, so index.html does not need editing.
  receiverPasswordInput = document.getElementById('receiver-password');
  if (!receiverPasswordInput) {
    receiverPasswordInput = document.createElement('input');
    receiverPasswordInput.type = 'password';
    receiverPasswordInput.id = 'receiver-password';
    receiverPasswordInput.name = 'password';
    receiverPasswordInput.placeholder = 'Access passphrase (if required)';
    receiverPasswordInput.autocomplete = 'current-password';
    receiverPasswordInput.className = 'receiver-password-input';
    receiverPasswordInput.style.cssText = 'display:block;width:100%;margin:12px 0;padding:12px;border:1px solid var(--border-subtle);border-radius:8px;';
    const messageAnchor = document.getElementById('receiver-message');
    otpForm.insertBefore(receiverPasswordInput, messageAnchor || verifyOtpBtn);
  }

  if (receiveModeBtn) receiveModeBtn.addEventListener('click', switchToReceiverView);
  if (backToSenderBtn) {
    backToSenderBtn.hidden = true;
    backToSenderBtn.addEventListener('click', switchToSenderView);
  }
  if (receiveModeBtn) receiveModeBtn.hidden = false;
  if (receiverView) {
    receiverView.hidden = true;
    receiverView.style.display = 'none';
  }

  otpInputs.forEach((input, index) => {
    input.addEventListener('input', () => {
      input.value = input.value.replace(/\D/g, '').slice(-1);
      if (input.value && index < otpInputs.length - 1) otpInputs[index + 1].focus();
      clearReceiverMessage();
    });

    input.addEventListener('keydown', event => {
      if (event.key === 'Backspace' && !input.value && index > 0) {
        otpInputs[index - 1].focus();
      } else if (event.key === 'ArrowLeft' && index > 0) {
        otpInputs[index - 1].focus();
      } else if (event.key === 'ArrowRight' && index < otpInputs.length - 1) {
        otpInputs[index + 1].focus();
      }
    });

    input.addEventListener('paste', event => {
      event.preventDefault();
      const pasted = (event.clipboardData || window.clipboardData)
        .getData('text').replace(/\D/g, '').slice(0, 6);
      pasted.split('').forEach((digit, digitIndex) => {
        if (otpInputs[digitIndex]) otpInputs[digitIndex].value = digit;
      });
      const nextIndex = Math.min(pasted.length, otpInputs.length - 1);
      otpInputs[nextIndex].focus();
      clearReceiverMessage();
    });
  });

  otpForm.addEventListener('submit', verifyReceiverOtp);
  if (receiveAnotherBtn) {
    receiveAnotherBtn.addEventListener('click', () => {
      if (receiverFilesSection) receiverFilesSection.hidden = true;
      const card = document.querySelector('#receiver-view .receiver-card');
      if (card) card.hidden = false;
      clearReceiverOTP();
    });
  }
}

function switchToReceiverView() {
  if (senderHero) senderHero.hidden = true;
  if (senderStage) senderStage.hidden = true;
  if (receiverView) {
    receiverView.hidden = false;
    receiverView.style.display = 'flex';
  }
  if (receiverFilesSection) receiverFilesSection.hidden = true;

  const receiverCard = document.querySelector('#receiver-view .receiver-card');
  if (receiverCard) receiverCard.hidden = false;

  if (receiveModeBtn) receiveModeBtn.hidden = true;
  if (backToSenderBtn) backToSenderBtn.hidden = false;
  clearReceiverOTP();
}

function switchToSenderView() {
  if (receiverView) {
    receiverView.hidden = true;
    receiverView.style.display = 'none';
  }
  if (senderHero) senderHero.hidden = false;
  if (senderStage) senderStage.hidden = false;
  if (receiveModeBtn) receiveModeBtn.hidden = false;
  if (backToSenderBtn) backToSenderBtn.hidden = true;
}

function clearReceiverMessage() {
  if (!receiverMessage) return;
  receiverMessage.textContent = '';
  receiverMessage.className = 'receiver-message';
}

function clearReceiverOTP() {
  if (otpInputs) otpInputs.forEach(input => { input.value = ''; });
  if (receiverPasswordInput) receiverPasswordInput.value = '';
  clearReceiverMessage();
  if (otpInputs && otpInputs[0]) otpInputs[0].focus();
}

function showReceiverMessage(message, type = 'error') {
  if (!receiverMessage) return;
  receiverMessage.textContent = String(message ?? '');
  receiverMessage.className = `receiver-message ${type}`;
}

async function verifyReceiverOtp(event) {
  event.preventDefault();

  const otp = otpInputs.map(input => input.value).join('');
  if (!/^\d{6}$/.test(otp)) {
    showReceiverMessage('Please enter all 6 OTP digits.', 'error');
    return;
  }

  // The backend expects a share token from /d/{token} or /share/{token}.
  const token = getShareTokenFromUrl();
  if (!token) {
    showReceiverMessage('Open the unique share link sent by the sender before entering the OTP.', 'error');
    return;
  }

  verifyOtpBtn.disabled = true;
  verifyOtpBtn.textContent = 'Verifying...';

  try {
    const response = await fetch(`${API_BASE}/shares/${encodeURIComponent(token)}/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        otp,
        password: receiverPasswordInput?.value || null
      })
    });

    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success || !result.data) {
      throw new Error(result.message || 'Invalid OTP or share unavailable. Please try again.');
    }

    showReceiverMessage('OTP verified successfully!', 'success');
    showReceiverFiles(result.data, token);
  } catch (error) {
    console.error('OTP verification failed:', error);
    showReceiverMessage(error.message || 'Server connection error. Please try again.', 'error');
    otpInputs.forEach(input => { input.value = ''; });
    if (otpInputs[0]) otpInputs[0].focus();
  } finally {
    verifyOtpBtn.disabled = false;
    verifyOtpBtn.textContent = 'Verify OTP';
  }
}

function showReceiverFiles(data, token) {
  const receiverCard = document.querySelector('#receiver-view .receiver-card');
  if (receiverCard) receiverCard.hidden = true;
  if (receiverFilesSection) receiverFilesSection.hidden = false;
  if (!receiverFileList) return;

  receiverFileList.replaceChildren();

  const textEntries = Array.isArray(data.textEntries) ? data.textEntries : [];
  const textsToRender = textEntries.length ? textEntries : (typeof data.textContent === 'string' ? [{ title: 'Protected Text Payload', content: data.textContent }] : []);
  textsToRender.forEach((entry, entryIndex) => {
    const textCard = document.createElement('div');
    textCard.className = 'receiver-file-item';
    textCard.style.cssText = 'flex-direction:column;align-items:stretch;gap:10px;';

    const header = document.createElement('div');
    header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;gap:10px;';

    const title = document.createElement('span');
    title.className = 'receiver-file-name';
    title.textContent = entry.title || `Text entry ${entryIndex + 1}`;

    const copyButton = document.createElement('button');
    copyButton.type = 'button';
    copyButton.className = 'receiver-download-btn';
    copyButton.textContent = 'Copy Content';
    copyButton.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(entry.content || '');
        showToast('Text copied to clipboard', 'success');
      } catch (error) {
        showToast('Could not copy text', 'error');
      }
    });

    const pre = document.createElement('pre');
    pre.style.cssText = 'white-space:pre-wrap;word-break:break-word;font-family:var(--font-mono);font-size:13px;line-height:1.5;padding:12px;background:#fff;border:1px solid var(--border-subtle);border-radius:8px;max-height:360px;overflow-y:auto;color:var(--slate-900);';
    pre.textContent = entry.content || '';

    header.append(title, copyButton);
    textCard.append(header, pre);
    receiverFileList.appendChild(textCard);
  });

  const files = Array.isArray(data.files) ? data.files : [];
  files.forEach(file => {
    const item = document.createElement('div');
    item.className = 'receiver-file-item';

    const info = document.createElement('div');
    const name = document.createElement('div');
    name.className = 'receiver-file-name';
    name.textContent = file.originalFilename || 'Shared file';

    const size = document.createElement('small');
    size.textContent = file.formattedSize || formatBytes(Number(file.fileSize) || 0);
    info.append(name, size);

    const download = document.createElement('a');
    download.className = 'receiver-download-btn';
    download.textContent = 'Download';
    download.href = `${API_BASE}/shares/${encodeURIComponent(token)}/files/${encodeURIComponent(file.id)}/download`;
    download.setAttribute('download', file.originalFilename || 'download');
    download.rel = 'noopener';

    item.append(info, download);
    receiverFileList.appendChild(item);
  });

  if (!files.length && !textsToRender.length) {
    const empty = document.createElement('p');
    empty.textContent = 'The share was verified, but no downloadable content was returned.';
    receiverFileList.appendChild(empty);
  }
}
