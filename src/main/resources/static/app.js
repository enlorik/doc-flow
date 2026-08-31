const state = {
  token: sessionStorage.getItem("docflow_token"),
  email: sessionStorage.getItem("docflow_email"),
  projectId: sessionStorage.getItem("docflow_project") || "all",
  projects: [],
  jobs: [],
  portfolioJobs: [],
  keys: [],
  authMode: "login",
  activeView: "jobs",
  pollTimer: null
};

if (sessionStorage.getItem("docflow_overview_enabled") !== "true") {
  state.projectId = "all";
  sessionStorage.setItem("docflow_project", "all");
  sessionStorage.setItem("docflow_overview_enabled", "true");
}

const elements = {
  authView: document.querySelector("#auth-view"),
  appView: document.querySelector("#app-view"),
  authForm: document.querySelector("#auth-form"),
  authEmail: document.querySelector("#auth-email"),
  authPassword: document.querySelector("#auth-password"),
  authSubmit: document.querySelector("#auth-submit"),
  authTitle: document.querySelector("#auth-title"),
  authSubtitle: document.querySelector("#auth-subtitle"),
  loginTab: document.querySelector("#login-tab"),
  registerTab: document.querySelector("#register-tab"),
  authSwitch: document.querySelector("#auth-switch"),
  authSwitchCopy: document.querySelector("#auth-switch-copy"),
  userEmail: document.querySelector("#user-email"),
  workspace: document.querySelector(".workspace"),
  sidebarToggle: document.querySelector("#sidebar-toggle"),
  sidebarScrim: document.querySelector("#sidebar-scrim"),
  allProjectsButton: document.querySelector("#all-projects-button"),
  projectList: document.querySelector("#project-list"),
  emptyState: document.querySelector("#empty-state"),
  overviewView: document.querySelector("#overview-view"),
  projectView: document.querySelector("#project-view"),
  projectName: document.querySelector("#project-name"),
  projectDescription: document.querySelector("#project-description"),
  jobsTab: document.querySelector("#jobs-tab"),
  keysTab: document.querySelector("#keys-tab"),
  jobsPanel: document.querySelector("#jobs-panel"),
  keysPanel: document.querySelector("#keys-panel"),
  pipelineBoard: document.querySelector("#pipeline-board"),
  pipelineStatus: document.querySelector("#pipeline-status"),
  queueList: document.querySelector("#queue-list"),
  runningList: document.querySelector("#running-list"),
  completedList: document.querySelector("#completed-list"),
  queueCount: document.querySelector("#queue-count"),
  runningCount: document.querySelector("#running-count"),
  completedCount: document.querySelector("#completed-count"),
  keyList: document.querySelector("#key-list"),
  jobForm: document.querySelector("#job-form"),
  documentText: document.querySelector("#document-text"),
  textCounter: document.querySelector("#text-counter"),
  submitJobButton: document.querySelector("#submit-job-button"),
  projectDialog: document.querySelector("#project-dialog"),
  projectForm: document.querySelector("#project-form"),
  projectNameInput: document.querySelector("#project-name-input"),
  projectDescriptionInput: document.querySelector("#project-description-input"),
  createProjectSubmit: document.querySelector("#create-project-submit"),
  keyDialog: document.querySelector("#key-dialog"),
  keyForm: document.querySelector("#key-form"),
  keyNameInput: document.querySelector("#key-name-input"),
  keyCreateFields: document.querySelector("#key-create-fields"),
  keyDialogTitle: document.querySelector("#key-dialog-title"),
  keyDialogActions: document.querySelector("#key-dialog-actions"),
  createKeySubmit: document.querySelector("#create-key-submit"),
  secretResult: document.querySelector("#secret-result"),
  rawKey: document.querySelector("#raw-key"),
  copyKeyButton: document.querySelector("#copy-key-button"),
  statTotal: document.querySelector("#stat-total"),
  statActive: document.querySelector("#stat-active"),
  statSucceeded: document.querySelector("#stat-succeeded"),
  statFailed: document.querySelector("#stat-failed"),
  overviewProjects: document.querySelector("#overview-projects"),
  overviewActive: document.querySelector("#overview-active"),
  overviewCompleted: document.querySelector("#overview-completed"),
  overviewTotal: document.querySelector("#overview-total"),
  projectFlowList: document.querySelector("#project-flow-list")
};

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function formatDate(value) {
  if (!value) return "Unknown time";
  return new Intl.DateTimeFormat(undefined, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit"
  }).format(new Date(value));
}

function formatDuration(seconds) {
  if (!seconds) return "Under 1 sec";
  if (seconds < 60) return `${seconds} sec`;
  return `${Math.ceil(seconds / 60)} min`;
}

function setButtonBusy(button, busy, busyLabel) {
  if (!button.dataset.defaultLabel) button.dataset.defaultLabel = button.textContent.trim();
  button.disabled = busy;
  button.textContent = busy ? busyLabel : button.dataset.defaultLabel;
}

function toast(message, type = "success") {
  const item = document.createElement("div");
  item.className = `toast ${type}`;
  item.textContent = message;
  document.querySelector("#toast-region").append(item);
  window.setTimeout(() => item.remove(), 4200);
}

function errorMessage(payload, fallback) {
  if (payload?.fieldErrors) return Object.values(payload.fieldErrors).join(" ");
  return payload?.message || fallback;
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (state.token) headers.Authorization = `Bearer ${state.token}`;

  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json() : null;

  if (!response.ok) {
    if (state.token && (response.status === 401 || response.status === 403)) {
      clearSession();
      showAuth();
    }
    throw new Error(errorMessage(payload, `Request failed (${response.status})`));
  }
  return payload;
}

function saveSession(auth) {
  state.token = auth.token;
  state.email = auth.email;
  sessionStorage.setItem("docflow_token", auth.token);
  sessionStorage.setItem("docflow_email", auth.email);
}

function clearSession() {
  state.token = null;
  state.email = null;
  state.projectId = "all";
  state.projects = [];
  state.jobs = [];
  state.portfolioJobs = [];
  state.keys = [];
  sessionStorage.removeItem("docflow_token");
  sessionStorage.removeItem("docflow_email");
  sessionStorage.removeItem("docflow_project");
  stopPolling();
}

function showAuth() {
  elements.appView.hidden = true;
  elements.authView.hidden = false;
  elements.authPassword.value = "";
}

async function showApp() {
  elements.authView.hidden = true;
  elements.appView.hidden = false;
  elements.userEmail.textContent = state.email || "";
  applySidebarState();
  await loadProjects();
}

function setAuthMode(mode) {
  state.authMode = mode;
  const isLogin = mode === "login";
  elements.loginTab.classList.toggle("active", isLogin);
  elements.registerTab.classList.toggle("active", !isLogin);
  elements.loginTab.setAttribute("aria-selected", String(isLogin));
  elements.registerTab.setAttribute("aria-selected", String(!isLogin));
  elements.authTitle.textContent = isLogin ? "Welcome back" : "Create your workspace";
  elements.authSubtitle.textContent = isLogin
    ? "Use your DocFlow account to continue."
    : "Create an account and run your first document job.";
  elements.authSubmit.querySelector("span").textContent = isLogin ? "Log in" : "Create account";
  elements.authPassword.autocomplete = isLogin ? "current-password" : "new-password";
  elements.authSwitchCopy.firstChild.textContent = isLogin ? "New to DocFlow? " : "Already have an account? ";
  elements.authSwitch.textContent = isLogin ? "Create an account" : "Log in";
}

async function handleAuth(event) {
  event.preventDefault();
  const endpoint = state.authMode === "login" ? "/api/auth/login" : "/api/auth/register";
  setButtonBusy(elements.authSubmit, true, state.authMode === "login" ? "Logging in…" : "Creating account…");
  try {
    const auth = await api(endpoint, {
      method: "POST",
      body: { email: elements.authEmail.value.trim(), password: elements.authPassword.value }
    });
    saveSession(auth);
    toast(state.authMode === "login" ? "Welcome back." : "Account created. You’re ready to go.");
    await showApp();
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setButtonBusy(elements.authSubmit, false);
  }
}

async function loadProjects() {
  try {
    state.projects = await api("/api/projects");
    renderProjects();
    if (!state.projects.length) {
      elements.emptyState.hidden = false;
      elements.overviewView.hidden = true;
      elements.projectView.hidden = true;
      return;
    }
    const existing = state.projects.find(project => project.id === state.projectId);
    if (state.projectId === "all" || !existing) await selectAllProjects();
    else await selectProject(existing.id);
  } catch (error) {
    toast(error.message, "error");
  }
}

function renderProjects() {
  elements.allProjectsButton.classList.toggle("active", state.projectId === "all");
  if (!state.projects.length) {
    elements.projectList.innerHTML = "";
    return;
  }
  elements.projectList.innerHTML = state.projects.map(project => `
    <button class="project-item ${project.id === state.projectId ? "active" : ""}" type="button" data-project-id="${project.id}">
      <span class="project-icon" aria-hidden="true">${escapeHtml(project.name.slice(0, 2).toUpperCase())}</span>
      <span class="project-copy"><strong>${escapeHtml(project.name)}</strong><span>${escapeHtml(project.description || "Document workspace")}</span></span>
    </button>
  `).join("");
}

async function selectProject(projectId) {
  stopPolling();
  state.projectId = projectId;
  sessionStorage.setItem("docflow_project", projectId);
  const project = state.projects.find(item => item.id === projectId);
  if (!project) return;
  elements.emptyState.hidden = true;
  elements.overviewView.hidden = true;
  elements.projectView.hidden = false;
  elements.projectName.textContent = project.name;
  elements.projectDescription.textContent = project.description || "Document processing workspace";
  renderProjects();
  closeSidebarOnMobile();
  await loadJobs();
  if (state.activeView === "keys") await loadKeys();
}

async function selectAllProjects() {
  stopPolling();
  state.projectId = "all";
  sessionStorage.setItem("docflow_project", "all");
  if (!state.projects.length) {
    elements.emptyState.hidden = false;
    elements.projectView.hidden = true;
    elements.overviewView.hidden = true;
    renderProjects();
    closeSidebarOnMobile();
    return;
  }
  elements.emptyState.hidden = true;
  elements.projectView.hidden = true;
  elements.overviewView.hidden = false;
  renderProjects();
  closeSidebarOnMobile();
  await loadPortfolioJobs();
}

function openProjectDialog() {
  elements.projectForm.reset();
  elements.projectDialog.showModal();
  window.setTimeout(() => elements.projectNameInput.focus(), 50);
}

async function createProject(event) {
  event.preventDefault();
  setButtonBusy(elements.createProjectSubmit, true, "Creating…");
  try {
    const project = await api("/api/projects", {
      method: "POST",
      body: {
        name: elements.projectNameInput.value.trim(),
        description: elements.projectDescriptionInput.value.trim() || null
      }
    });
    state.projects.unshift(project);
    elements.projectDialog.close();
    toast("Project created.");
    await selectProject(project.id);
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setButtonBusy(elements.createProjectSubmit, false);
  }
}

function isMobileNavigation() {
  return window.matchMedia("(max-width: 720px)").matches;
}

function applySidebarState() {
  const collapsed = localStorage.getItem("docflow_sidebar_collapsed") === "true";
  elements.workspace.classList.toggle("sidebar-collapsed", !isMobileNavigation() && collapsed);
  if (!isMobileNavigation()) elements.appView.classList.remove("sidebar-open");
  const expanded = isMobileNavigation()
    ? elements.appView.classList.contains("sidebar-open")
    : !elements.workspace.classList.contains("sidebar-collapsed");
  elements.sidebarToggle.setAttribute("aria-expanded", String(expanded));
}

function toggleSidebar() {
  if (isMobileNavigation()) {
    elements.appView.classList.toggle("sidebar-open");
  } else {
    const collapsed = elements.workspace.classList.toggle("sidebar-collapsed");
    localStorage.setItem("docflow_sidebar_collapsed", String(collapsed));
  }
  applySidebarState();
}

function closeSidebarOnMobile() {
  if (!isMobileNavigation()) return;
  elements.appView.classList.remove("sidebar-open");
  elements.sidebarToggle.setAttribute("aria-expanded", "false");
}

async function loadPortfolioJobs({ quiet = false } = {}) {
  if (!state.projects.length || state.projectId !== "all") return;
  try {
    const groups = await Promise.all(state.projects.map(async project => ({
      project,
      jobs: await api(`/api/projects/${project.id}/jobs`)
    })));
    state.portfolioJobs = groups.flatMap(group => group.jobs.map(job => ({
      ...job,
      projectName: group.project.name
    })));
    renderPortfolio(groups);
    updatePolling();
  } catch (error) {
    if (!quiet) toast(error.message, "error");
  }
}

function renderPortfolio(groups) {
  const activeJobs = state.portfolioJobs.filter(job => ["QUEUED", "RETRY_SCHEDULED", "RUNNING"].includes(job.status));
  const completedJobs = state.portfolioJobs.filter(job => job.status === "SUCCEEDED");
  elements.overviewProjects.textContent = state.projects.length;
  elements.overviewActive.textContent = activeJobs.length;
  elements.overviewCompleted.textContent = completedJobs.length;
  elements.overviewTotal.textContent = state.portfolioJobs.length;

  elements.projectFlowList.innerHTML = groups.map(({ project, jobs }) => {
    const queued = jobs.filter(job => ["QUEUED", "RETRY_SCHEDULED"].includes(job.status)).length;
    const running = jobs.filter(job => job.status === "RUNNING").length;
    const completed = jobs.filter(job => !["QUEUED", "RETRY_SCHEDULED", "RUNNING"].includes(job.status)).length;
    const status = running ? "Processing now" : queued ? "Waiting in queue" : jobs.length ? "Up to date" : "Ready for work";
    const statusClass = running ? "moving" : queued ? "waiting" : "calm";
    return `
      <button class="project-flow-row" type="button" data-project-flow-id="${project.id}">
        <span class="portfolio-project">
          <span class="project-avatar">${escapeHtml(project.name.slice(0, 2).toUpperCase())}</span>
          <span><strong>${escapeHtml(project.name)}</strong><small>${jobs.length} job${jobs.length === 1 ? "" : "s"} · ${escapeHtml(status)}</small></span>
        </span>
        <span class="portfolio-link ${queued || running ? "active" : ""}" aria-hidden="true"></span>
        <span class="portfolio-node queue-node"><small>Queued</small><strong>${queued}</strong></span>
        <span class="portfolio-link ${running ? "active" : ""}" aria-hidden="true"></span>
        <span class="portfolio-node worker-node ${running ? "active" : ""}"><small>Processing</small><strong>${running}</strong></span>
        <span class="portfolio-link ${running || completed ? "complete" : ""}" aria-hidden="true"></span>
        <span class="portfolio-node result-node"><small>Completed</small><strong>${completed}</strong></span>
        <span class="flow-health ${statusClass}">${escapeHtml(status)}</span>
      </button>
    `;
  }).join("");
}

async function loadJobs({ quiet = false } = {}) {
  if (!state.projectId || state.projectId === "all") return;
  try {
    state.jobs = await api(`/api/projects/${state.projectId}/jobs`);
    renderJobs();
    updatePolling();
  } catch (error) {
    if (!quiet) toast(error.message, "error");
  }
}

function safeJson(value) {
  try { return JSON.parse(value); } catch { return null; }
}

function metric(label, value) {
  return `<div class="metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function renderJobs() {
  const queuedJobs = state.jobs.filter(job => ["QUEUED", "RETRY_SCHEDULED"].includes(job.status));
  const runningJobs = state.jobs.filter(job => job.status === "RUNNING");
  const completedJobs = state.jobs.filter(job => !["QUEUED", "RETRY_SCHEDULED", "RUNNING"].includes(job.status));
  const active = queuedJobs.length + runningJobs.length;
  const succeeded = state.jobs.filter(job => job.status === "SUCCEEDED").length;
  const failed = state.jobs.filter(job => ["FAILED", "DEAD_LETTER"].includes(job.status)).length;
  elements.statTotal.textContent = state.jobs.length;
  elements.statActive.textContent = active;
  elements.statSucceeded.textContent = succeeded;
  elements.statFailed.textContent = failed;

  elements.queueCount.textContent = queuedJobs.length;
  elements.runningCount.textContent = runningJobs.length;
  elements.completedCount.textContent = completedJobs.length;

  if (runningJobs.length) {
    elements.pipelineStatus.textContent = `${runningJobs.length} job${runningJobs.length === 1 ? " is" : "s are"} inside the worker now. This view refreshes automatically.`;
  } else if (queuedJobs.length) {
    elements.pipelineStatus.textContent = `${queuedJobs.length} job${queuedJobs.length === 1 ? " is" : "s are"} safely queued and waiting to be claimed.`;
  } else if (completedJobs.length) {
    elements.pipelineStatus.textContent = "The worker is idle. All submitted jobs have reached a final state.";
  } else {
    elements.pipelineStatus.textContent = "Ready for your first document. Submit text to watch it move through the backend.";
  }

  elements.queueList.innerHTML = renderJobLane(queuedJobs, "Queue is clear");
  elements.runningList.innerHTML = renderJobLane(runningJobs, "Worker is idle");
  elements.completedList.innerHTML = renderJobLane(completedJobs.slice(0, 8), "No results yet");
}

function renderJobLane(jobs, emptyMessage) {
  if (!jobs.length) return `<div class="lane-empty"><span>✓</span>${escapeHtml(emptyMessage)}</div>`;
  return jobs.map(job => {
    const input = safeJson(job.inputJson);
    const result = safeJson(job.resultJson);
    const preview = input?.text ? input.text.replace(/\s+/g, " ").slice(0, 54) : job.type;
    const metrics = result ? `
      <div class="metrics">
        ${metric("Words", result.words)}
        ${metric("Characters", result.characters)}
        ${metric("Sentences", result.sentences)}
        ${metric("Read time", formatDuration(result.estimatedReadingTimeSeconds))}
      </div>
    ` : "";
    const cancel = ["QUEUED", "RUNNING"].includes(job.status)
      ? `<button class="cancel-job" type="button" data-cancel-job="${job.id}">Cancel</button>`
      : `<span>${job.attemptCount} attempt${job.attemptCount === 1 ? "" : "s"}</span>`;
    return `
      <article class="job-card">
        <div class="job-top">
          <div class="job-title">
            <strong>${escapeHtml(preview || "Text analysis")}</strong>
            <span>${formatDate(job.createdAt)}</span>
          </div>
          <span class="status ${escapeHtml(job.status)}">${escapeHtml(job.status)}</span>
        </div>
        ${metrics}
        ${job.errorMessage ? `<p class="job-error">${escapeHtml(job.errorMessage)}</p>` : ""}
        <div class="job-meta"><span>Attempt ${job.attemptCount}/${job.maxAttempts}</span>${cancel}</div>
      </article>
    `;
  }).join("");
}

async function submitJob(event) {
  event.preventDefault();
  const text = elements.documentText.value.trim();
  if (!text) return;
  setButtonBusy(elements.submitJobButton, true, "Submitting…");
  try {
    const idempotencyKey = globalThis.crypto?.randomUUID
      ? `web-${crypto.randomUUID()}`
      : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const job = await api(`/api/projects/${state.projectId}/jobs`, {
      method: "POST",
      body: {
        type: "TEXT_ANALYZE",
        inputJson: JSON.stringify({ text }),
        idempotencyKey,
        maxAttempts: 1
      }
    });
    state.jobs.unshift(job);
    elements.documentText.value = "";
    updateTextCounter();
    renderJobs();
    updatePolling();
    toast("Document queued. Watch it move through the pipeline.");
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setButtonBusy(elements.submitJobButton, false);
  }
}

async function cancelJob(jobId) {
  try {
    const updated = await api(`/api/projects/${state.projectId}/jobs/${jobId}/cancel`, { method: "POST" });
    state.jobs = state.jobs.map(job => job.id === updated.id ? updated : job);
    renderJobs();
    updatePolling();
    toast("Job cancelled.");
  } catch (error) {
    toast(error.message, "error");
    await loadJobs({ quiet: true });
  }
}

function updateTextCounter() {
  const count = elements.documentText.value.length;
  elements.textCounter.textContent = `${count.toLocaleString()} character${count === 1 ? "" : "s"}`;
}

function stopPolling() {
  if (state.pollTimer) window.clearInterval(state.pollTimer);
  state.pollTimer = null;
}

function updatePolling() {
  const scopedJobs = state.projectId === "all" ? state.portfolioJobs : state.jobs;
  const hasActive = scopedJobs.some(job => ["QUEUED", "RETRY_SCHEDULED", "RUNNING"].includes(job.status));
  if (hasActive && !state.pollTimer) {
    state.pollTimer = window.setInterval(() => {
      if (state.projectId === "all") loadPortfolioJobs({ quiet: true });
      else loadJobs({ quiet: true });
    }, 600);
  } else if (!hasActive) {
    stopPolling();
  }
}

function setView(view) {
  state.activeView = view;
  const jobs = view === "jobs";
  elements.jobsTab.classList.toggle("active", jobs);
  elements.keysTab.classList.toggle("active", !jobs);
  elements.jobsTab.setAttribute("aria-selected", String(jobs));
  elements.keysTab.setAttribute("aria-selected", String(!jobs));
  elements.jobsPanel.hidden = !jobs;
  elements.keysPanel.hidden = jobs;
  if (!jobs) loadKeys();
}

async function loadKeys() {
  if (!state.projectId || state.projectId === "all") return;
  try {
    state.keys = await api(`/api/projects/${state.projectId}/keys`);
    renderKeys();
  } catch (error) {
    toast(error.message, "error");
  }
}

function renderKeys() {
  if (!state.keys.length) {
    elements.keyList.innerHTML = '<div class="empty-list">No API keys yet.<br>Generate one for programmatic access.</div>';
    return;
  }
  elements.keyList.innerHTML = state.keys.map(key => `
    <article class="key-card">
      <div>
        <strong>${escapeHtml(key.name)}</strong>
        <span>${escapeHtml(key.keyPrefix)}•••••••• · Created ${formatDate(key.createdAt)}</span>
      </div>
      <div class="key-actions">
        <span class="key-state ${key.revoked ? "revoked" : ""}">${key.revoked ? "Revoked" : "Active"}</span>
        ${key.revoked ? "" : `<button class="revoke-key" type="button" data-revoke-key="${key.id}">Revoke</button>`}
      </div>
    </article>
  `).join("");
}

function openKeyDialog() {
  elements.keyForm.reset();
  elements.keyCreateFields.hidden = false;
  elements.secretResult.hidden = true;
  elements.keyDialogActions.hidden = false;
  elements.keyDialogTitle.textContent = "Generate an API key";
  elements.keyDialog.showModal();
  window.setTimeout(() => elements.keyNameInput.focus(), 50);
}

async function createKey(event) {
  event.preventDefault();
  if (elements.keyCreateFields.hidden) return;
  setButtonBusy(elements.createKeySubmit, true, "Generating…");
  try {
    const key = await api(`/api/projects/${state.projectId}/keys`, {
      method: "POST",
      body: { name: elements.keyNameInput.value.trim() }
    });
    elements.keyCreateFields.hidden = true;
    elements.keyDialogActions.hidden = true;
    elements.secretResult.hidden = false;
    elements.keyDialogTitle.textContent = "API key created";
    elements.rawKey.textContent = key.rawKey;
    toast("API key generated.");
    await loadKeys();
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setButtonBusy(elements.createKeySubmit, false);
  }
}

async function copyKey() {
  try {
    await navigator.clipboard.writeText(elements.rawKey.textContent);
    elements.copyKeyButton.textContent = "Copied";
    window.setTimeout(() => { elements.copyKeyButton.textContent = "Copy API key"; }, 1600);
  } catch {
    toast("Copy failed. Select the key and copy it manually.", "error");
  }
}

async function revokeKey(keyId) {
  if (!window.confirm("Revoke this API key? This cannot be undone.")) return;
  try {
    await api(`/api/projects/${state.projectId}/keys/${keyId}`, { method: "DELETE" });
    toast("API key revoked.");
    await loadKeys();
  } catch (error) {
    toast(error.message, "error");
  }
}

elements.authForm.addEventListener("submit", handleAuth);
elements.loginTab.addEventListener("click", () => setAuthMode("login"));
elements.registerTab.addEventListener("click", () => setAuthMode("register"));
elements.authSwitch.addEventListener("click", () => setAuthMode(state.authMode === "login" ? "register" : "login"));
elements.projectForm.addEventListener("submit", createProject);
elements.jobForm.addEventListener("submit", submitJob);
elements.keyForm.addEventListener("submit", createKey);
elements.documentText.addEventListener("input", updateTextCounter);
elements.jobsTab.addEventListener("click", () => setView("jobs"));
elements.keysTab.addEventListener("click", () => setView("keys"));
elements.copyKeyButton.addEventListener("click", copyKey);
elements.sidebarToggle.addEventListener("click", toggleSidebar);
elements.sidebarScrim.addEventListener("click", closeSidebarOnMobile);
elements.allProjectsButton.addEventListener("click", selectAllProjects);
document.querySelector("#logout-button").addEventListener("click", () => { clearSession(); showAuth(); });
document.querySelector("#refresh-button").addEventListener("click", async () => {
  await loadJobs();
  if (state.activeView === "keys") await loadKeys();
  toast("Project data refreshed.");
});
document.querySelectorAll("#new-project-button, #sidebar-new-project-button, #empty-new-project-button, #overview-new-project-button")
  .forEach(button => button.addEventListener("click", openProjectDialog));
document.querySelector("#new-key-button").addEventListener("click", openKeyDialog);
document.querySelectorAll("[data-close-dialog]").forEach(button => {
  button.addEventListener("click", () => document.querySelector(`#${button.dataset.closeDialog}`).close());
});
elements.projectList.addEventListener("click", event => {
  const button = event.target.closest("[data-project-id]");
  if (button) selectProject(button.dataset.projectId);
});
elements.projectFlowList.addEventListener("click", event => {
  const button = event.target.closest("[data-project-flow-id]");
  if (button) selectProject(button.dataset.projectFlowId);
});
elements.pipelineBoard.addEventListener("click", event => {
  const button = event.target.closest("[data-cancel-job]");
  if (button) cancelJob(button.dataset.cancelJob);
});
elements.keyList.addEventListener("click", event => {
  const button = event.target.closest("[data-revoke-key]");
  if (button) revokeKey(button.dataset.revokeKey);
});
window.addEventListener("resize", applySidebarState);
document.addEventListener("keydown", event => {
  if (event.key === "Escape") closeSidebarOnMobile();
});

setAuthMode("login");
if (state.token) showApp();
else showAuth();
