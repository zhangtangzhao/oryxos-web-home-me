/* OryxOS 知识库管理台 —— 原生 ES Module，无构建。
 * 视图：列表(#/) 与详情(#/kb/{name})，hash 路由。 */

const app = document.getElementById('app');
const crumb = document.getElementById('crumb');
const errorbar = document.getElementById('errorbar');
const errtext = document.getElementById('errtext');

/* ---- API 包装：统一 ApiResponse 信封解析与错误呈现（FR-009） ---- */

class ApiError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

let lastAction = null; // 失败后"重试"复跑的动作

export async function api(path, { method = 'GET', body } = {}) {
  let resp;
  try {
    resp = await fetch(path, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    const err = new ApiError('NETWORK', '服务不可达：无法连接后端服务，请确认服务端已启动。');
    err.network = true;
    throw err;
  }
  let payload = null;
  try { payload = await resp.json(); } catch (e) { payload = null; }
  if (!resp.ok || !payload || payload.success === false) {
    const e = payload && payload.error
      ? new ApiError(payload.error.code, payload.error.message)
      : new ApiError('HTTP_' + resp.status, '请求失败（HTTP ' + resp.status + '）');
    throw e;
  }
  return payload.data;
}

function showError(err, retry) {
  lastAction = retry || null;
  errtext.textContent = err && err.message ? err.message : String(err);
  errorbar.classList.remove('hidden');
}

function clearError() { errorbar.classList.add('hidden'); lastAction = null; }

document.getElementById('errclose').addEventListener('click', clearError);
document.getElementById('errretry').addEventListener('click', () => {
  const action = lastAction; clearError();
  if (action) action();
});

/* ---- 工具 ---- */

export function esc(s) {
  return String(s == null ? '' : s)
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

export function el(id) { return document.getElementById(id); }

/** 原生确认弹窗：resolve(true)=确认，resolve(false)=取消（取消零请求，SC-005）。 */
export function confirmModal({ title, bodyHtml, confirmText = '确认', danger = false }) {
  return new Promise((resolve) => {
    const root = document.getElementById('modal-root');
    root.innerHTML = `
      <div class="modal-mask">
        <div class="modal" role="dialog" aria-modal="true">
          <h3>${esc(title)}</h3>
          <div class="modal-body">${bodyHtml}</div>
          <div class="modal-actions">
            <button class="btn ghost" id="modal-cancel">取消</button>
            <button class="btn ${danger ? 'danger' : ''}" id="modal-ok">${esc(confirmText)}</button>
          </div>
        </div>
      </div>`;
    const close = (val) => { root.innerHTML = ''; resolve(val); };
    root.querySelector('#modal-cancel').addEventListener('click', () => close(false));
    root.querySelector('#modal-ok').addEventListener('click', () => close(true));
  });
}

function setCrumb(parts) {
  crumb.innerHTML = parts.map((p, i) =>
    i < parts.length - 1 ? `<a href="${p.href}">${esc(p.text)}</a><span class="sep">›</span>`
      : `<span>${esc(p.text)}</span>`).join('');
}

/* ---- 路由 ---- */

function route() {
  clearError();
  const hash = location.hash || '#/';
  const m = hash.match(/^#\/kb\/([^/]+)$/);
  if (m) renderDetail(decodeURIComponent(m[1]));
  else renderList();
}
window.addEventListener('hashchange', route);

/* ---- 视图挂载点（各 story 填充） ---- */

function renderList() {
  setCrumb([{ text: '知识库', href: '#/' }]);
  app.innerHTML = '<p class="loading">加载中……</p>';
  api('/api/v1/kbs').then(renderListBody).catch((e) => showError(e, renderList));
}

let currentKb = null;

function renderDetail(name) {
  currentKb = name;
  setCrumb([{ text: '知识库', href: '#/' }, { text: name, href: '#' }]);
  app.innerHTML = '<p class="loading">加载中……</p>';
  api('/api/v1/kbs/' + encodeURIComponent(name)).then(
    (kb) => renderDetailBody(name, kb)).catch((e) => {
    if (e.code === 404) { // 库不存在：回列表并提示，不留僵尸入口
      location.hash = '#/';
      showError(e);
    } else {
      showError(e, () => renderDetail(name));
    }
  });
}

/* ---- US1：列表视图 ---- */

function renderListBody(list) {
  const rows = list.map((kb) => `
    <tr class="${kb.failed_count > 0 ? 'link-failed' : ''}" data-name="${esc(kb.name)}">
      <td><a href="#/kb/${encodeURIComponent(kb.name)}">${esc(kb.name)}</a>
        ${kb.failed_count > 0 ? '<span class="badge warn">有失败文档</span>' : ''}</td>
      <td>${esc(kb.description || '—')}</td>
      <td class="num">${kb.document_count}</td>
      <td class="num">${kb.ready_count}</td>
      <td class="num">${kb.failed_count > 0
        ? '<span class="badge failed">' + kb.failed_count + '</span>' : '0'}</td>
      <td>${esc(kb.embedding_model || '—')}</td>
      <td>${esc(kb.updated_at || '—')}</td>
    </tr>`).join('');
  app.innerHTML = `
    <div class="card">
      <h2>知识库总览 <button class="btn sm" id="btn-new-kb" style="float:right">＋ 新建知识库</button></h2>
      <div id="create-slot"></div>
      ${list.length === 0 ? `
        <div class="empty">
          <p>暂无知识库。知识库用于为 Agent 提供可检索的私有文档（Markdown / 纯文本）。</p>
          <p><button class="btn" id="btn-new-kb-empty">创建第一个知识库</button></p>
        </div>` : `
        <table class="list">
          <thead><tr><th>名称</th><th>描述</th><th class="num">文档</th><th class="num">就绪</th>
            <th class="num">失败</th><th>嵌入模型</th><th>最近更新</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>`}
    </div>`;
}

/* 行点击进详情（链接与整行皆可点） */
app.addEventListener('click', (ev) => {
  const row = ev.target.closest('tr[data-name]');
  if (row && !ev.target.closest('a, button')) {
    location.hash = '#/kb/' + encodeURIComponent(row.dataset.name);
  }
  if (ev.target.closest('#btn-new-kb, #btn-new-kb-empty')) showCreateForm();
  if (ev.target.closest('#btn-add-doc')) showAddDocForm();
});

/* ---- US2：建库表单（FR-003） ---- */

const KB_NAME_RE = /^[a-z0-9][a-z0-9_-]{0,63}$/;

function showCreateForm() {
  const slot = document.getElementById('create-slot');
  slot.innerHTML = `
    <form class="stack" id="create-form">
      <label class="row">名称（小写字母/数字开头，可含 - _，≤64 字符）
        <input type="text" id="kb-name" placeholder="product-docs" autocomplete="off">
        <span class="field-err" id="kb-name-err"></span></label>
      <label class="row">描述（选填）
        <input type="text" id="kb-desc" placeholder="产品文档库" autocomplete="off"></label>
      <div><button class="btn" type="submit">创建</button>
        <button class="btn ghost" type="button" id="create-cancel">取消</button></div>
    </form>`;
  slot.querySelector('#create-cancel').addEventListener('click', () => { slot.innerHTML = ''; });
  slot.querySelector('#create-form').addEventListener('submit', (ev) => {
    ev.preventDefault();
    const name = slot.querySelector('#kb-name').value.trim();
    const desc = slot.querySelector('#kb-desc').value.trim();
    const errEl = slot.querySelector('#kb-name-err');
    errEl.textContent = '';
    if (!name) { errEl.textContent = '名称必填'; return; }
    if (!KB_NAME_RE.test(name)) {
      errEl.textContent = '名称需以小写字母或数字开头，仅含小写字母、数字、- 和 _，长度 1~64';
      return;
    }
    api('/api/v1/kbs', { method: 'POST', body: { name, description: desc } }).then(() => {
      renderList(); // 201：刷新列表展示新库
    }).catch((e) => {
      errEl.textContent = e.code === 409 ? '创建失败：名称「' + name + '」已存在'
        : (e.message || '创建失败');
    });
  });
}

/* ---- US1：详情视图 ---- */

function fmtSize(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function statusBadge(status) {
  const label = { pending: '待摄取', ready: '就绪', failed: '失败' }[status] || status;
  return `<span class="badge ${esc(status)}">${esc(label)}</span>`;
}

function renderDetailBody(name, kb) {
  const docs = kb.documents || [];
  const docRows = docs.map((d) => `
    <tr>
      <td>${esc(d.doc_path)}
        ${d.status === 'failed' && d.error_message
          ? `<span class="err-msg">失败原因：${esc(d.error_message)}</span>` : ''}</td>
      <td>${statusBadge(d.status)}</td>
      <td class="num">${d.chunk_count}</td>
      <td class="num">${fmtSize(d.size_bytes)}</td>
      <td>${esc(d.ingested_at || '—')}</td>
    </tr>`).join('');
  app.innerHTML = `
    <div class="card">
      <h2>${esc(kb.name)}
        <span style="float:right">
          <button class="btn sm" id="btn-ingest">开始摄取</button>
          <button class="btn sm ghost" id="btn-add-doc">添加文档</button>
          <button class="btn sm danger" id="btn-delete-kb">删除</button>
        </span></h2>
      <p class="muted">${esc(kb.description || '（无描述）')}　·　嵌入模型
        ${esc(kb.embedding_model || '未记录')}（${kb.embedding_dimensions ?? '—'} 维）　·
        创建 ${esc(kb.created_at || '—')}　·　更新 ${esc(kb.updated_at || '—')}</p>
      <div id="action-slot"></div>
      <h3>文档清单（${docs.length}）</h3>
      ${docs.length === 0 ? '<p class="muted">暂无文档。点击右上角「添加文档」开始。</p>' : `
      <table class="list doc-table">
        <thead><tr><th>文档路径</th><th>状态</th><th class="num">分块</th>
          <th class="num">大小</th><th>摄取时间</th></tr></thead>
        <tbody>${docRows}</tbody>
      </table>`}
    </div>
    <div class="card" id="overview-card"><h3>结构总览</h3><p class="loading">加载中……</p></div>
    <div class="card" id="search-slot"></div>
    <div class="card hidden" id="adddoc-card"></div>`;
  document.getElementById('btn-ingest').addEventListener('click', () => runIngest(name));
  document.getElementById('btn-delete-kb').addEventListener('click', () => confirmDelete(name, docs.length));
  loadOverview(name);
  mountSearch(name);
}

/* ---- US2：添加文档（FR-004） ---- */

const DOC_EXT_RE = /\.(md|markdown|txt)$/i;

function showAddDocForm() {
  const card = document.getElementById('adddoc-card');
  card.classList.remove('hidden');
  card.innerHTML = `
    <h3>添加文档</h3>
    <form class="stack" id="adddoc-form">
      <div class="radio-row">
        <label><input type="radio" name="src" value="paste" checked> 粘贴文本</label>
        <label><input type="radio" name="src" value="path"> 引用服务端已有文件</label>
      </div>
      <label class="row" id="row-filename">文件名（.md / .markdown / .txt）
        <input type="text" id="doc-filename" placeholder="faq.md" autocomplete="off">
        <span class="field-err" id="doc-filename-err"></span></label>
      <label class="row" id="row-content">文档内容
        <textarea id="doc-content" placeholder="粘贴 Markdown 或纯文本内容……"></textarea>
        <span class="field-err" id="doc-content-err"></span></label>
      <label class="row hidden" id="row-path">服务端文件路径（绝对路径或相对工作区路径）
        <input type="text" id="doc-path" placeholder="D:/docs/handbook.md" autocomplete="off">
        <span class="field-err" id="doc-path-err"></span></label>
      <div><button class="btn" type="submit">添加（待摄取）</button>
        <button class="btn ghost" type="button" id="adddoc-cancel">取消</button></div>
    </form>`;
  const form = card.querySelector('#adddoc-form');
  form.querySelectorAll('input[name=src]').forEach((r) => r.addEventListener('change', () => {
    const paste = form.querySelector('input[name=src]:checked').value === 'paste';
    form.querySelector('#row-filename').classList.toggle('hidden', !paste);
    form.querySelector('#row-content').classList.toggle('hidden', !paste);
    form.querySelector('#row-path').classList.toggle('hidden', paste);
  }));
  form.querySelector('#adddoc-cancel').addEventListener('click', () => {
    card.classList.add('hidden'); card.innerHTML = '';
  });
  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const paste = form.querySelector('input[name=src]:checked').value === 'paste';
    ['doc-filename-err', 'doc-content-err', 'doc-path-err'].forEach((id) => {
      const n = document.getElementById(id); if (n) n.textContent = '';
    });
    const finish = () => { card.classList.add('hidden'); card.innerHTML = ''; renderDetail(currentKb); };
    let req;
    if (paste) {
      const filename = form.querySelector('#doc-filename').value.trim();
      const content = form.querySelector('#doc-content').value;
      if (!filename) { form.querySelector('#doc-filename-err').textContent = '文件名必填'; return; }
      if (!DOC_EXT_RE.test(filename)) {
        form.querySelector('#doc-filename-err').textContent = '仅支持 .md / .markdown / .txt';
        return;
      }
      if (!content.trim()) { form.querySelector('#doc-content-err').textContent = '内容不能为空'; return; }
      req = api('/api/v1/kbs/' + encodeURIComponent(currentKb) + '/documents',
        { method: 'POST', body: { filename, content } });
    } else {
      const path = form.querySelector('#doc-path').value.trim();
      if (!path) { form.querySelector('#doc-path-err').textContent = '路径必填'; return; }
      req = api('/api/v1/kbs/' + encodeURIComponent(currentKb) + '/documents',
        { method: 'POST', body: { path } });
    }
    req.then(finish).catch((e) => { showError(e, showAddDocForm); });
  });
}

/* ---- US2：摄取 + 结果面板（FR-005/FR-006） ---- */

function ingestNotice(e) {
  if (e.code === 409) return '嵌入模型已变更，请恢复配置或重建知识库（' + e.message + '）';
  if (e.code === 503) return '嵌入服务未配置，无法摄取：' + e.message;
  if (e.code === 502) return '嵌入服务调用失败，可稍后重试：' + e.message;
  return e.message || '摄取失败';
}

function runIngest(name) {
  const slot = document.getElementById('action-slot');
  slot.innerHTML = '<div class="notice warn">摄取中……（嵌入 + 索引，文档多时需要一点时间）</div>';
  api('/api/v1/kbs/' + encodeURIComponent(name) + '/ingest', { method: 'POST' }).then((s) => {
    const rows = (s.documents || []).map((d) => `
      <tr><td>${esc(d.doc_path)}</td><td>${statusBadge(d.status)}</td>
        <td class="num">${d.chunk_count}</td></tr>`).join('');
    slot.innerHTML = `
      <div class="notice ${s.failed > 0 ? 'warn' : 'ok'}">
        摄取完成：处理 <b>${s.processed}</b> · 跳过 ${s.skipped} · 移除 ${s.removed}
        · 失败 <b>${s.failed}</b> · 耗时 ${s.duration_ms} ms</div>
      ${(s.documents || []).length ? `
      <table class="list"><thead><tr><th>文档</th><th>状态</th><th class="num">分块</th></tr></thead>
        <tbody>${rows}</tbody></table>` : ''}
      ${s.failed > 0 ? '<p class="muted">失败文档已保留待摄取状态，可再次点击「开始摄取」重试。</p>' : ''}`;
    // 同步刷新文档清单状态（就绪/失败）
    api('/api/v1/kbs/' + encodeURIComponent(name)).then((kb) => refreshDocTable(kb));
  }).catch((e) => {
    slot.innerHTML = '<div class="notice err">' + esc(ingestNotice(e)) + '</div>';
  });
}

function refreshDocTable(kb) {
  const card = app.querySelector('.card'); // 详情首卡
  if (!card || !kb) return;
  const h3 = card.querySelector('h3');
  if (!h3 || !h3.textContent.startsWith('文档清单')) return;
  const docs = kb.documents || [];
  h3.textContent = '文档清单（' + docs.length + '）';
  let table = card.querySelector('table.doc-table');
  const rows = docs.map((d) => `
    <tr><td>${esc(d.doc_path)}
      ${d.status === 'failed' && d.error_message
        ? `<span class="err-msg">失败原因：${esc(d.error_message)}</span>` : ''}</td>
      <td>${statusBadge(d.status)}</td><td class="num">${d.chunk_count}</td>
      <td class="num">${fmtSize(d.size_bytes)}</td><td>${esc(d.ingested_at || '—')}</td></tr>`).join('');
  if (!table) {
    table = document.createElement('table');
    table.className = 'list doc-table';
    table.innerHTML = `<thead><tr><th>文档路径</th><th>状态</th><th class="num">分块</th>
      <th class="num">大小</th><th>摄取时间</th></tr></thead><tbody></tbody>`;
    h3.after(table);
  }
  table.querySelector('tbody').innerHTML = rows;
}

function loadOverview(name) {
  const card = document.getElementById('overview-card');
  api('/api/v1/kbs/' + encodeURIComponent(name) + '/overview').then((ov) => {
    const docs = ov.documents || [];
    card.innerHTML = `<h3>结构总览（${docs.length} 篇）</h3>` + (docs.length === 0
      ? '<p class="muted">暂无文档。</p>'
      : docs.map((d) => `
        <details>
          <summary>${esc(d.doc_path)} <span class="muted">（${(d.headings || []).length} 个标题）</span></summary>
          <ul class="headings-tree">${(d.headings || []).map((h) => `<li># ${esc(h)}</li>`).join('')}</ul>
        </details>`).join(''));
  }).catch((e) => {
    card.innerHTML = '<h3>结构总览</h3><p class="muted">总览加载失败：' + esc(e.message) + '</p>';
  });
}

/* ---- US3：删除知识库（FR-007 / SC-005 取消零请求） ---- */

function confirmDelete(name, docCount) {
  confirmModal({
    title: '删除知识库',
    bodyHtml: `<p>即将删除知识库 <b>${esc(name)}</b>（含 ${docCount} 篇文档及其全部索引数据）。</p>
      <p class="modal-warn">该操作物理删除、不可恢复。</p>`,
    confirmText: '删除',
    danger: true,
  }).then((ok) => {
    if (!ok) return; // 取消：零请求
    api('/api/v1/kbs/' + encodeURIComponent(name), { method: 'DELETE' }).then(() => {
      if (location.hash === '#/' || !location.hash) renderList();
      else location.hash = '#/'; // hashchange 触发回列表
    }).catch((e) => {
      if (e.code === 404) { // 已被别处删除：不留僵尸入口
        if (location.hash === '#/' || !location.hash) renderList();
        else location.hash = '#/';
        showError(e);
      } else {
        showError(e, () => confirmDelete(name, docCount));
      }
    });
  });
}

/* ---- US4：试检索（FR-011，只读端点例外 FR-008） ---- */

function mountSearch(name) {
  const card = document.getElementById('search-slot');
  if (!card) return;
  card.classList.remove('hidden');
  card.innerHTML = `
    <h3>试检索</h3>
    <form class="searchbar" id="search-form">
      <input type="text" id="search-query" placeholder="输入查询，验证该库的检索效果……" autocomplete="off">
      <button class="btn" type="submit">检索</button>
    </form>
    <div id="search-result"></div>`;
  card.querySelector('#search-form').addEventListener('submit', (ev) => {
    ev.preventDefault();
    runSearch(name);
  });
}

function runSearch(name) {
  const out = document.getElementById('search-result');
  const q = document.getElementById('search-query').value.trim();
  out.innerHTML = '';
  if (!q) { out.innerHTML = '<p class="muted">请输入查询内容。</p>'; return; }
  out.innerHTML = '<p class="loading">检索中……</p>';
  api('/api/v1/kbs/' + encodeURIComponent(name) + '/search',
    { method: 'POST', body: { query: q } }).then((r) => {
    if (r.zero_result || !(r.hits || []).length) {
      out.innerHTML = '<p class="muted">无命中结果。</p>';
      return;
    }
    const hits = r.hits.map((h) => `
      <div class="hit">
        <div class="hit-meta"><span class="hit-score">score ${Number(h.score).toFixed(3)}</span>
          ·　${esc(h.doc_path)}${h.heading_path ? '　·　' + esc(h.heading_path) : ''}
          ·　chunk ${h.chunk_ordinal}</div>
        <div class="hit-content">${esc(h.content)}</div>
      </div>`).join('');
    out.innerHTML = `<div class="hits">${hits}</div>
      ${r.degraded ? '<p class="muted">注意：嵌入服务暂不可用，本次为仅关键词的降级检索。</p>' : ''}`;
  }).catch((e) => {
    out.innerHTML = '<div class="notice err">' + esc(searchNotice(e)) + '</div>';
  });
}

function searchNotice(e) {
  if (e.code === 409) return '嵌入模型已变更，请恢复配置或重建知识库（' + e.message + '）';
  if (e.code === 503) return '嵌入服务未配置，无法试检索：' + e.message;
  if (e.code === 502) return '检索失败，可稍后重试：' + e.message;
  return e.message || '检索失败';
}

route();
