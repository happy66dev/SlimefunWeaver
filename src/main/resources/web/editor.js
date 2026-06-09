// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy (k666kkk666k@163.com)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
var MC = {
  colors: {0:'#000000',1:'#0000AA',2:'#00AA00',3:'#00AAAA',4:'#AA0000',5:'#AA00AA',6:'#FFAA00',7:'#AAAAAA',8:'#555555',9:'#5555FF',a:'#55FF55',b:'#55FFFF',c:'#FF5555',d:'#FF55FF',e:'#FFFF55',f:'#FFFFFF'},
  parseToHtml: function(text) {
    if (!text) return '';
    var out = '', i = 0, style = '';
    while (i < text.length) {
      if (text[i] === '\u00a7' || text[i] === '&') {
        var code = text[i+1];
        if ((code && (code === 'x' || code === 'X')) && i + 13 < text.length) {
          var marker = text[i];
          var hex = '';
          var validHex = true;
          for (var h = 2; h <= 13; h += 2) {
            if (text[i + h] !== marker) { validHex = false; break; }
            var hexDigit = text[i + h + 1];
            if (!/[0-9a-fA-F]/.test(hexDigit)) { validHex = false; break; }
            hex += hexDigit;
          }
          if (validHex) {
            style += 'color:#' + hex + ';';
            i += 14;
            continue;
          }
        }
        if (code && '0123456789abcdefABCDEFrklmno'.indexOf(code) >= 0) {
          code = code.toLowerCase();
          if (code === 'r') style = '';
          else if (code === 'l') style += 'font-weight:bold;';
          else if (code === 'm') style += 'text-decoration:line-through;';
          else if (code === 'n') style += 'text-decoration:underline;';
          else if (code === 'o') style += 'font-style:italic;';
          else if (code === 'k') style += '';
          else if (MC.colors[code]) style += 'color:' + MC.colors[code] + ';';
          i += 2; continue;
        }
      }
      out += '<span style="' + style + '">' + MC.escapeHtml(text[i]) + '</span>';
      i++;
    }
    return out;
  },
  strip: function(text) {
    if (!text) return '';
    var out = '', i = 0;
    while (i < text.length) {
      if ((text[i] === '\u00a7' || text[i] === '&') && (text[i+1] === 'x' || text[i+1] === 'X') && i + 13 < text.length) {
        var marker = text[i];
        var validHex = true;
        for (var h = 2; h <= 13; h += 2) {
          if (text[i + h] !== marker) { validHex = false; break; }
          var hexDigit = text[i + h + 1];
          if (!/[0-9a-fA-F]/.test(hexDigit)) { validHex = false; break; }
        }
        if (validHex) { i += 14; continue; }
      }
      if ((text[i] === '\u00a7' || text[i] === '&') && '0123456789abcdefABCDEFrklmno'.indexOf(text[i+1]) >= 0) { i += 2; continue; }
      out += text[i]; i++;
    }
    return out;
  },
  escapeHtml: function(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  },
  escapeAttr: function(s) {
    return MC.escapeHtml(String(s || '')).replace(/"/g,'&quot;').replace(/'/g,'&#39;');
  }
};

var API_TOKEN = sessionStorage.getItem('scg_token') || '';
if (API_TOKEN) sessionStorage.setItem('scg_token', API_TOKEN);
var nativeFetch = window.fetch.bind(window);
window.fetch = function(input, init) {
  init = init || {};
  init.headers = init.headers || {};
  if (API_TOKEN) {
    init.headers['X-slimefunweaver-Token'] = API_TOKEN;
  }
  return nativeFetch(input, init);
};

var Toast = {
  show: function(msg, type, duration) {
    type = type || 'info'; duration = duration || 2600;
    var container = document.getElementById('toast-container');
    var el = document.createElement('div');
    el.className = 'toast ' + type;
    var icons = {success:'\u2714', error:'\u2716', warning:'\u26a0', info:'\u2139'};
    var icon = document.createElement('span');
    icon.textContent = icons[type] || '';
    el.appendChild(icon);
    el.appendChild(document.createTextNode(' ' + msg));
    container.appendChild(el);
    setTimeout(function() { el.classList.add('fadeout'); setTimeout(function() { el.remove(); }, 300); }, duration);
  }
};

var Dialog = {
  _overlay: null,
  _init: function() { if (!Dialog._overlay) Dialog._overlay = document.getElementById('modal-overlay'); },
  alert: function(msg, cb) {
    Dialog._init();
    Dialog._overlay.innerHTML = '<div class="modal-box"><div class="modal-header"><span>提示</span><button class="btn btn-sm" onclick="Dialog.close()">\u2715</button></div><div class="modal-body">' + msg + '</div><div class="modal-footer"><button class="btn btn-primary btn-sm" onclick="Dialog.close()">确定</button></div></div>';
    Dialog._overlay.style.display = 'flex';
    Dialog._cb = cb;
  },
  confirm: function(msg, cb) {
    Dialog._init();
    Dialog._overlay.innerHTML = '<div class="modal-box"><div class="modal-header"><span>确认</span><button class="btn btn-sm" onclick="Dialog.close()">\u2715</button></div><div class="modal-body">' + msg + '</div><div class="modal-footer"><button class="btn btn-sm" onclick="Dialog.close()">取消</button><button class="btn btn-danger btn-sm" id="dialog-confirm-btn">确定</button></div></div>';
    Dialog._overlay.style.display = 'flex';
    document.getElementById('dialog-confirm-btn').onclick = function() { Dialog.close(); if (cb) cb(true); };
  },
  prompt: function(msg, defVal, cb) {
    Dialog._init();
    Dialog._overlay.innerHTML = '<div class="modal-box"><div class="modal-header"><span>' + msg + '</span><button class="btn btn-sm" onclick="Dialog.close()">\u2715</button></div><div class="modal-body"><input type="text" id="dialog-input" value="' + (defVal||'').replace(/"/g,'&quot;') + '" autofocus></div><div class="modal-footer"><button class="btn btn-sm" onclick="Dialog.close()">取消</button><button class="btn btn-primary btn-sm" id="dialog-ok-btn">确定</button></div></div>';
    Dialog._overlay.style.display = 'flex';
    var input = document.getElementById('dialog-input');
    input.focus(); input.select();
    var ok = function() { var v = input.value.trim(); Dialog.close(); if (cb) cb(v); };
    document.getElementById('dialog-ok-btn').onclick = ok;
    input.onkeydown = function(e) { if (e.key === 'Enter') ok(); };
  },
  close: function() {
    Dialog._overlay.style.display = 'none';
    Dialog._overlay.innerHTML = '';
  }
};

var state = {
  categories: [], selectedCategory: null, selectedNode: null,
  currentPage: 1, pickerTarget: null, dirty: false, pickerFilter: 'all', saving: false, dirtyVersion: 0, reloading: false
};

function $(id) { return document.getElementById(id); }

function markDirty() {
  if (state.reloading || state.saving) return;
  state.dirtyVersion++;
  if (!state.dirty) { state.dirty = true; updateSaveStatus(); }
}

function clearDirty() {
  state.dirty = false; updateSaveStatus();
}

function updateSaveStatus() {
  var el = $('save-status');
  el.textContent = state.dirty ? '未保存' : '已保存';
  el.className = 'save-status ' + (state.dirty ? 'unsaved' : 'saved');
}

async function loadCategories() {
  try {
    var resp = await fetch('/api/categories');
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var data = await resp.json();
    if (!Array.isArray(data.categories)) throw new Error('分类数据格式错误');
    state.categories = data.categories;
    clearDirty();
    renderTree();
    return true;
  } catch(e) { Toast.show('加载失败: ' + e.message, 'error'); return false; }
}

async function discardChanges() {
  if (state.saving) { Toast.show('正在保存，请稍后再放弃修改', 'info'); return; }
  Dialog.confirm('放弃所有未保存的修改？此操作会从服务器重新加载配置。', function(ok) {
    if (!ok) return;
    state.reloading = true;
    loadCategories().then(function(ok) {
      if (!ok) { state.reloading = false; return; }
      state.selectedCategory = null;
      state.selectedNode = null;
      state.currentPage = 1;
      state.reloading = false;
      renderGrid();
      renderEditor();
      Toast.show('已从服务器重新加载', 'info');
    });
  });
}

function renderTree() {
  var root = $('tree-root');
  root.innerHTML = '';

  var rootLi = document.createElement('li');
  rootLi.className = 'tree-root-entry';
  rootLi.innerHTML = '<span class="tree-icon">\u21a9</span><span class="tree-label tree-root-label">[根级别]</span>';
  rootLi.onclick = function(e) { e.stopPropagation(); selectRoot(); };
  if (state.selectedCategory === null) rootLi.classList.add('active');
  root.appendChild(rootLi);

  var cats = state.categories || [];
  cats.forEach(function(cat, i) { root.appendChild(buildTreeItem(cat, i, null, 0)); });
  applyTreeFilter();
}

function selectRoot() {
  state.selectedCategory = null;
  state.selectedNode = null;
  state.currentPage = 1;
  renderTree();
  renderGrid();
  renderEditor();
}

function filterTree() {
  applyTreeFilter();
}

function applyTreeFilter() {
  var q = ($('tree-search').value || '').toLowerCase().trim();
  var allLis = document.querySelectorAll('#tree-root li');
  allLis.forEach(function(li) {
    var labelEl = li.querySelector('.tree-label');
    var label = (labelEl ? (labelEl.getAttribute('title') || labelEl.textContent) : '').toLowerCase();
    var display = 'block';
    if (q && label.indexOf(q) < 0) {
      var hasVisibleChild = li.querySelector('li:not([style*="display: none"])');
      display = hasVisibleChild ? 'block' : 'none';
    }
    li.style.display = display;
  });
  // second pass: hide parents whose all children are hidden
  if (q) {
    allLis.forEach(function(li) {
      var labelEl = li.querySelector('.tree-label');
      var label = (labelEl ? (labelEl.getAttribute('title') || labelEl.textContent) : '').toLowerCase();
      if (label.indexOf(q) < 0) {
        var vis = li.querySelector('li:not([style*="display: none"])');
        if (!vis) li.style.display = 'none';
      }
    });
  }
}

function buildTreeItem(cat, index, parentRef, depth) {
  var li = document.createElement('li');
  li.style.paddingLeft = (depth * 14) + 'px';
  var hasChildren = cat.children && cat.children.length > 0;
  var hasItems = cat.items && cat.items.length > 0;
  var totalChildren = (cat.children ? cat.children.length : 0) + (cat.items ? cat.items.length : 0);

  var icon = '\u25b8';
  li.innerHTML = '<span class="tree-icon">' + icon + '</span><span class="tree-label tree-category" title="' + MC.escapeAttr(MC.strip(cat.display||cat.key)) + '">' + MC.parseToHtml(cat.display||cat.key) + '</span>' + (totalChildren > 0 ? '<span class="tree-badge">' + totalChildren + '</span>' : '');

  li.onclick = function(e) { e.stopPropagation(); selectCategory(cat, index, parentRef); };
  if (state.selectedNode === cat) li.classList.add('active');

  var ul = null;
  if (hasChildren) {
    ul = document.createElement('ul');
    cat.children.forEach(function(child, ci) { ul.appendChild(buildTreeItem(child, ci, cat, depth + 1)); });
  }
  if (hasItems) {
    if (!ul) ul = document.createElement('ul');
    cat.items.forEach(function(it, ii) {
      var itemLi = document.createElement('li');
      itemLi.style.paddingLeft = ((depth + 1) * 14) + 'px';
      var itType = it.type || 'ITEM';
      var itIcon = itType === 'REFERENCE' ? '\u21b3' : (itType === 'PLACEHOLDER' ? '\u25a2' : (it.icon ? '\u25a3' : '\u25a0'));
      var itLabel = it.display || it.id || it.key || '?';
      if (itType === 'PLACEHOLDER') itLabel = it.display || '(占位)';
      if (itType === 'REFERENCE') itLabel = it.display || '\u21b3 ' + (it.ref || '?');
      itemLi.innerHTML = '<span class="tree-icon" style="opacity:0.7">' + itIcon + '</span><span class="tree-label tree-item" title="' + MC.escapeAttr(MC.strip(itLabel)) + '">' + MC.parseToHtml(itLabel) + '</span>';
      itemLi.onclick = function(e) { e.stopPropagation(); selectGridItem(it, ii); };
      if (state.selectedNode === it) itemLi.classList.add('active');
      ul.appendChild(itemLi);
    });
  }
  if (ul) li.appendChild(ul);
  return li;
}

function selectCategory(cat, index, parentRef) {
  state.selectedNode = cat;
  state.selectedCategory = cat;
  state.currentPage = 1;
  renderTree();
  renderGrid();
  renderEditor();
}

function getChildren() {
  if (state.selectedCategory === null) return state.categories || [];
  if (!state.selectedCategory) return [];
  var list = [];
  if (state.selectedCategory.children) list = list.concat(state.selectedCategory.children);
  if (state.selectedCategory.items) list = list.concat(state.selectedCategory.items);
  return list;
}

function getPageItems(page) {
  return getChildren().filter(function(item) { return (item.page || 1) === page; });
}

function getMaxPage() {
  var max = 1;
  getChildren().forEach(function(item) { if ((item.page || 1) > max) max = item.page || 1; });
  return max;
}

function gridEnterCategory(cat) {
  if (!cat.key) return;
  state.selectedCategory = cat;
  state.selectedNode = cat;
  state.currentPage = 1;
  renderGrid();
  renderEditor();
  renderTree();
}

function findCategoryParent(target) {
  function walk(cats, parent) {
    for (var i = 0; i < cats.length; i++) {
      if (cats[i] === target) return parent;
      if (cats[i].children && cats[i].children.length > 0) {
        var found = walk(cats[i].children, cats[i]);
        if (found !== undefined) return found;
      }
    }
    return undefined;
  }
  return walk(state.categories, null);
}

function gridGoBack() {
  if (state.selectedCategory === null) return;
  var parent = findCategoryParent(state.selectedCategory);
  state.selectedCategory = parent;
  state.selectedNode = parent;
  state.currentPage = 1;
  renderGrid();
  renderEditor();
  renderTree();
}

function renderGrid() {
  var grid = $('grid');
  grid.innerHTML = '';
  if (!state.selectedCategory && state.selectedCategory !== null) {
    $('grid-title').textContent = '请选择左侧分类';
    $('grid-page-controls').style.display = 'none';
    $('grid-empty').style.display = 'none';
    return;
  }
  var isRoot = state.selectedCategory === null;
  $('grid-title').textContent = isRoot ? '根级别' : '';
  if (!isRoot) $('grid-title').innerHTML = MC.parseToHtml(state.selectedCategory.display || state.selectedCategory.key || '未命名分类');
  $('grid-page-controls').style.display = 'flex';
  $('btn-grid-back').disabled = state.selectedCategory === null;

  var maxPage = getMaxPage();
  state.currentPage = Math.min(state.currentPage, Math.max(1, maxPage));
  $('grid-page-info').textContent = '第 ' + state.currentPage + '/' + maxPage + ' 页';

  var pageItems = getPageItems(state.currentPage);
  $('grid-empty').style.display = pageItems.length === 0 ? 'block' : 'none';

  for (var i = 0; i < 36; i++) {
    var cell = document.createElement('div');
    cell.className = 'grid-cell';
    cell.dataset.slot = i;
    cell.draggable = true;

    var item = null, idx = -1;
    for (var j = 0; j < pageItems.length; j++) {
      if ((pageItems[j].slot || 0) === i) { item = pageItems[j]; idx = j; break; }
    }

    if (item) {
      var typeLabel = '', typeClass = '';
      if (item.type === 'REFERENCE') { typeLabel = '\u21b3 ' + ((item.mode === 'copy') ? '引用(copy)' : '引用'); typeClass = 'category'; cell.setAttribute('data-reference',''); }
      else if (item.type === 'PLACEHOLDER') { typeLabel = '占位'; typeClass = 'placeholder'; cell.setAttribute('data-placeholder',''); }
      else if (item.icon || item.type === 'CATEGORY' || item.key) { typeLabel = '分类'; typeClass = 'category'; cell.setAttribute('data-category',''); }
      else { typeLabel = '物品'; typeClass = 'item'; }

      var displayName = item.display || item.id || item.key || '?';
      if (typeClass === 'item') displayName = item.display || item.id || '?';
      if (item.type === 'REFERENCE') displayName = item.display || '\u21b3 ' + (item.ref || '?');
      cell.innerHTML = '<span class="cell-type-badge ' + typeClass + '">' + typeLabel + '</span><span class="cell-name">' + MC.parseToHtml(displayName) + '</span>';
      if (state.selectedNode === item) cell.classList.add('selected');

      cell.onclick = (function(it, iIndex) { return function(e) {
        e.stopPropagation();
        selectGridItem(it, iIndex);
      }; })(item, idx);

      var isCat = item.icon || item.type === 'CATEGORY' || item.key;
      if (isCat) {
        cell.ondblclick = (function(it) { return function(e) {
          e.stopPropagation();
          gridEnterCategory(it);
        }; })(item);
      }

      cell.oncontextmenu = (function(it) { return function(e) {
        e.preventDefault();
        showContextMenu(e.clientX, e.clientY, it);
      }; })(item);

      cell.addEventListener('dragstart', (function(it) { return function(e) {
        e.dataTransfer.setData('text/plain', JSON.stringify({ slot: it.slot || 0, page: it.page || 1 }));
        e.target.classList.add('dragging');
      }; })(item));

      cell.addEventListener('dragend', function(e) { e.target.classList.remove('dragging'); });
    }

    cell.addEventListener('dragover', function(e) { e.preventDefault(); e.target.classList.add('drag-over'); });
    cell.addEventListener('dragleave', function(e) { e.target.classList.remove('drag-over'); });
    cell.addEventListener('drop', function(e) {
      e.preventDefault();
      e.target.classList.remove('drag-over');
      try {
        var data = JSON.parse(e.dataTransfer.getData('text/plain'));
        var targetSlot = parseInt(e.target.dataset.slot);
        if (isNaN(targetSlot)) return;
        swapItems(data.page, data.slot, state.currentPage, targetSlot);
      } catch(ex) {}
    });

    grid.appendChild(cell);
  }
}

function swapItems(fromPage, fromSlot, toPage, toSlot) {
  if (state.saving) return;
  var all = getChildren();
  var fromItem = null, toItem = null;
  all.forEach(function(item) {
    if ((item.page || 1) === fromPage && (item.slot || 0) === fromSlot) fromItem = item;
    if ((item.page || 1) === toPage && (item.slot || 0) === toSlot) toItem = item;
  });
  if (!fromItem) return;
  if (toItem) { toItem.slot = fromSlot; toItem.page = fromPage; }
  fromItem.slot = toSlot;
  fromItem.page = toPage;
  markDirty();
  renderGrid();
}

function selectGridItem(item, idx) {
  state.selectedNode = item;
  renderGrid();
  renderEditor();
  renderTree();
}

function renderEditor() {
  var node = state.selectedNode;
  if (!node || state.selectedCategory === null) { $('editor-form').style.display = 'none'; $('editor-empty').style.display = 'block'; return; }
  $('editor-form').style.display = 'block';
  $('editor-empty').style.display = 'none';

  var isItem = node.type === 'ITEM';
  var isRef = node.type === 'REFERENCE';
  var isRefCopy = isRef && node.mode === 'copy';
  var readOnly = isItem || isRefCopy;

  var effectiveNode = node;
  if (isRefCopy) {
    var resolved = resolveRefCopy(node.ref);
    if (resolved) effectiveNode = resolved;
  }

  $('edit-display').value = effectiveNode.display || '';
  $('edit-display').disabled = readOnly;
  $('edit-lore').value = (effectiveNode.lore || []).join('\n');
  $('edit-lore').disabled = readOnly;
  $('edit-glow').checked = !!effectiveNode.glow;
  $('edit-glow').disabled = readOnly;
  $('btn-pick-icon').style.display = readOnly ? 'none' : '';
  $('edit-page').value = node.page || 1;

  if (isRef) {
    $('edit-ref-mode-wrap').style.display = '';
    $('edit-ref-mode').value = node.mode || 'custom';
  } else {
    $('edit-ref-mode-wrap').style.display = 'none';
  }

  updateDisplayPreview();
  updateLorePreview();

  var iconDisplay = '';
  if (effectiveNode.icon) { iconDisplay = '[' + effectiveNode.icon.type + '] ' + effectiveNode.icon.id; }
  else if (effectiveNode.id) { iconDisplay = '[ITEM] ' + effectiveNode.id; }
  else if (isRef) { iconDisplay = '[REF] \u21b3 ' + (node.ref || '?'); }
  else { iconDisplay = '(无图标)'; }
  $('edit-icon-display').textContent = iconDisplay;
}

function resolveRefCopy(refPath) {
  if (!refPath) return null;
  var parts = refPath.split('/');
  var cats = state.categories;
  var found = null;
  for (var pi = 0; pi < parts.length; pi++) {
    var matched = false;
    for (var ci = 0; ci < cats.length; ci++) {
      if (cats[ci].key === parts[pi]) {
        found = cats[ci];
        cats = [];
        if (found.children) {
          for (var cj = 0; cj < found.children.length; cj++) {
            if (found.children[cj].key) cats.push(found.children[cj]);
          }
        }
        matched = true;
        break;
      }
    }
    if (!matched) return null;
  }
  return found;
}

function updateDisplayPreview() {
  var val = $('edit-display').value;
  $('display-preview').innerHTML = val ? MC.parseToHtml(val) : '<span style="color:#484f58;">预览</span>';
}

function updateLorePreview() {
  var val = $('edit-lore').value;
  if (!val.trim()) { $('lore-preview').innerHTML = '<span style="color:#484f58;">预览</span>'; return; }
  var lines = val.split('\n');
  var html = '';
  lines.forEach(function(line) { html += '<div>' + MC.parseToHtml(line) + '</div>'; });
  $('lore-preview').innerHTML = html;
}

function updateSelection() {
  var node = state.selectedNode;
  if (!node) return;
  var isItem = node.type === 'ITEM';
  var isRef = node.type === 'REFERENCE';
  var prevMode = node.mode;
  var isRefCopy = isRef && node.mode === 'copy';
  if (!isItem && !isRefCopy) node.display = $('edit-display').value;
  if (!isItem && !isRefCopy) node.lore = $('edit-lore').value.split('\n');
  if (!isItem && !isRefCopy) node.glow = $('edit-glow').checked;
  if (isRef) node.mode = $('edit-ref-mode').value;
  node.page = parseInt($('edit-page').value) || 1;
  state.currentPage = node.page;
  if (isRef && node.mode !== prevMode) renderEditor();
  updateDisplayPreview();
  updateLorePreview();
  markDirty();
  renderGrid();
  renderTree();
}

function syncCurrentSelection() {
  if (!state.selectedNode) return;
  updateSelection();
}

document.getElementById('edit-display').addEventListener('input', function(){ updateDisplayPreview(); syncCurrentSelection(); });
document.getElementById('edit-lore').addEventListener('input', function(){ updateLorePreview(); syncCurrentSelection(); });

function deleteSelected() {
  if (state.saving) return;
  if (!state.selectedNode || !state.selectedCategory) return;
  Dialog.confirm('确定要删除此项吗？此操作无法撤销。', function(ok) {
    if (!ok || state.saving) return;
    var parent = findParent(state.selectedNode);
    if (state.selectedNode.key) {
      if (parent && parent.children) {
        parent.children = parent.children.filter(function(c) { return c !== state.selectedNode; });
      } else {
        state.categories = state.categories.filter(function(c) { return c !== state.selectedNode; });
      }
    } else {
      if (parent && parent.items) {
        parent.items = parent.items.filter(function(it) { return it !== state.selectedNode; });
      }
    }
    state.selectedNode = null;
    state.selectedCategory = parent || null;
    markDirty();
    renderGrid();
    renderTree();
    renderEditor();
    Toast.show('已删除', 'info');
  });
}

function findParent(node) {
  function search(cats, parent) {
    for (var i = 0; i < cats.length; i++) {
      var cat = cats[i];
      if (cat === node) return parent;
      if (cat.children) {
        var found = search(cat.children, cat);
        if (found) return found;
      }
      if (cat.items) {
        for (var j = 0; j < cat.items.length; j++) {
          if (cat.items[j] === node) return cat;
        }
      }
    }
    return null;
  }
  return search(state.categories, null);
}

function collectSiblingKeys() {
  var keys = new Set();
  var siblings;
  if (state.selectedCategory === null) { siblings = state.categories; }
  else if (state.selectedCategory) { siblings = state.selectedCategory.children || []; }
  else { return keys; }
  siblings.forEach(function(c) { if (c.key) keys.add(c.key); });
  return keys;
}

function makeUniqueKey(baseKey) {
  var existing = collectSiblingKeys();
  if (!existing.has(baseKey)) return baseKey;
  var n = 2;
  while (existing.has(baseKey + '_' + n)) n++;
  return baseKey + '_' + n;
}

function addCategory() {
  if (state.saving) return;
  Dialog.prompt('输入分类 key（英文 ID）', 'new_category', function(key) {
    if (!key || state.saving) return;
    var existing = collectSiblingKeys();
    if (existing.has(key)) {
      var suggested = makeUniqueKey(key);
      Toast.show('key "' + key + '" 已存在，自动更名为 "' + suggested + '"', 'warning');
      key = suggested;
    }
    var slot = findEmptySlot();
    if (slot < 0) { Toast.show('当前页已满 (36/36)', 'error'); return; }
    var newCat = { key: key, display: key, icon: { type: 'VANILLA', id: 'BOOK' }, glow: false, lore: [], page: state.currentPage, slot: slot, children: [], items: [] };
    if (state.selectedCategory) {
      if (!state.selectedCategory.children) state.selectedCategory.children = [];
      state.selectedCategory.children.push(newCat);
    } else {
      state.categories.push(newCat);
      state.selectedCategory = newCat;
    }
    markDirty();
    renderTree();
    state.selectedNode = newCat;
    renderGrid();
    renderEditor();
    Toast.show('已添加分类: ' + MC.strip(key), 'success');
  });
}

function addPlaceholder() {
  if (!state.selectedCategory && state.selectedCategory !== null) { Toast.show('请先选择左侧分类', 'warning'); return; }
  if (state.selectedCategory === null) { Toast.show('根级别不能添加占位物品，请选择子分类', 'warning'); return; }
  var slot = findEmptySlot();
  if (slot < 0) { Toast.show('当前页已满 (36/36)', 'error'); return; }
  var newPh = { type: 'PLACEHOLDER', display: '', icon: { type: 'VANILLA', id: 'GRAY_STAINED_GLASS_PANE' }, glow: false, lore: [], page: state.currentPage, slot: slot };
  if (!state.selectedCategory.items) state.selectedCategory.items = [];
  state.selectedCategory.items.push(newPh);
  markDirty();
  renderGrid();
  renderTree();
  Toast.show('已添加占位物品', 'success');
}

function addItem() {
  if (!state.selectedCategory && state.selectedCategory !== null) { Toast.show('请先选择左侧分类', 'warning'); return; }
  if (state.selectedCategory === null) { Toast.show('根级别不能添加物品，请选择子分类', 'warning'); return; }
  state.pickerTarget = 'addItem';
  state.pickerFilter = 'all';
  $('picker-title').textContent = '添加物品';
  $('picker-filters').style.display = 'flex';
  $('picker-overlay').style.display = 'flex';
  var input = $('picker-search');
  input.value = '';
  input.placeholder = '搜索物品名或ID...';
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词开始搜索</div>';
  updatePickerFilterBtns();
  setTimeout(function() { input.focus(); }, 100);
}

function addReference() {
  if (!state.selectedCategory && state.selectedCategory !== null) { Toast.show('请先选择左侧分类', 'warning'); return; }
  if (state.selectedCategory === null) { Toast.show('根级别不能添加引用，请选择子分类', 'warning'); return; }
  state.pickerTarget = 'addReference';
  $('picker-title').textContent = '选择引用目标分类';
  $('picker-filters').style.display = 'none';
  $('picker-overlay').style.display = 'flex';
  var input = $('picker-search');
  input.value = '';
  input.placeholder = '搜索分类名或 key...';
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词搜索，或直接选择</div>';
  searchReferenceCategories();
  setTimeout(function() { input.focus(); }, 100);
}

function openIconPicker() {
  state.pickerTarget = 'icon';
  $('picker-title').textContent = '选择图标';
  $('picker-overlay').style.display = 'flex';
  var input = $('picker-search');
  input.value = '';
  input.placeholder = '搜索物品名或ID...';
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词开始搜索</div>';
  setTimeout(function() { input.focus(); }, 100);
}

function closePicker() {
  $('picker-overlay').style.display = 'none';
  $('picker-filters').style.display = 'none';
  state.pickerTarget = null;
}

function collectUsedSlimefunIds() {
  var ids = new Set();
  function walk(cats) {
    if (!cats) return;
    cats.forEach(function(cat) {
      if (cat.items) {
        cat.items.forEach(function(it) {
          if (it.type === 'ITEM' && it.id) ids.add(it.id);
        });
      }
      if (cat.children) walk(cat.children);
    });
  }
  walk(state.categories);
  return ids;
}

function collectCategoryItemIds(cat) {
  var ids = new Set();
  if (!cat || !cat.items) return ids;
  cat.items.forEach(function(it) { if (it.type === 'ITEM' && it.id) ids.add(it.id); });
  return ids;
}

function setPickerFilter(filter) {
  state.pickerFilter = filter;
  updatePickerFilterBtns();
  doSearch();
}

function updatePickerFilterBtns() {
  var btns = document.querySelectorAll('#picker-filters .picker-filter-btn');
  btns.forEach(function(btn) {
    var isActive = btn.getAttribute('data-filter') === state.pickerFilter;
    btn.className = 'picker-filter-btn' + (isActive ? ' active' : '');
  });
}

function findEmptySlot() {
  var page = state.currentPage;
  var used = {};
  getChildren().forEach(function(item) { if ((item.page || 1) === page) used[item.slot || 0] = true; });
  for (var i = 0; i < 36; i++) { if (!used[i]) return i; }
  return -1;
}

function showContextMenu(x, y, item) {
  var menu = $('context-menu');
  menu.style.display = 'block';
  menu.style.left = x + 'px';
  menu.style.top = y + 'px';
  menu._targetItem = item;
  document.addEventListener('click', function hide() { menu.style.display = 'none'; document.removeEventListener('click', hide); });
}

function moveToPage() {
  var item = $('context-menu')._targetItem;
  $('context-menu').style.display = 'none';
  if (!item) return;
  Dialog.prompt('移动到第几页？', (item.page || 1) + '', function(pageStr) {
    if (!pageStr) return;
    var newPage = parseInt(pageStr);
    if (isNaN(newPage) || newPage < 1) { Toast.show('请输入有效的页码', 'warning'); return; }
    var used = {};
    getChildren().forEach(function(it) { if (it !== item && (it.page || 1) === newPage) used[it.slot || 0] = true; });
    var targetSlot = -1;
    for (var i = 0; i < 36; i++) { if (!used[i]) { targetSlot = i; break; } }
    if (targetSlot < 0) { Toast.show('目标页已满 (36/36)', 'error'); return; }
    item.page = newPage;
    item.slot = targetSlot;
    state.currentPage = newPage;
    markDirty();
    renderGrid();
    renderEditor();
    Toast.show('已移动到第 ' + newPage + ' 页', 'success');
  });
}

var searchTimer = null;
var refCategoryResults = [];
var refSearchTimer = null;

function searchReferenceCategories() {
  if (refSearchTimer) clearTimeout(refSearchTimer);
  refSearchTimer = setTimeout(doRefSearch, 200);
}

function doRefSearch() {
  var q = ($('picker-search').value || '').toLowerCase().trim();
  refCategoryResults = [];
  collectAllCategories(state.categories, [], q);
  renderReferenceResults();
}

function collectAllCategories(cats, breadcrumb, q) {
  if (!cats) return;
  cats.forEach(function(cat) {
    if (state.selectedCategory && cat === state.selectedCategory) return;
    var key = (cat.key || '').toLowerCase();
    var display = MC.strip(cat.display || cat.key || '').toLowerCase();
    if (!q || key.indexOf(q) >= 0 || display.indexOf(q) >= 0) {
      refCategoryResults.push({ cat: cat, breadcrumb: breadcrumb.slice() });
    }
    if (cat.children && cat.children.length > 0) {
      var subBreadcrumb = breadcrumb.slice();
      subBreadcrumb.push(cat);
      collectAllCategories(cat.children, subBreadcrumb, q);
    }
  });
}

function renderReferenceResults() {
  if (refCategoryResults.length === 0) {
    $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">无匹配分类</div>';
    return;
  }
  var html = '';
  refCategoryResults.forEach(function(r, i) {
    var cat = r.cat;
    var pathHtml = '';
    r.breadcrumb.forEach(function(bc) {
      pathHtml += '<span class="ref-path-seg">' + MC.escapeHtml(MC.strip(bc.display || bc.key)) + '</span><span class="ref-path-arrow">\u25b8</span>';
    });
    html += '<div class="picker-item" onclick="pickRefCategory(' + i + ')">' +
      '<span class="item-type category">分类</span>' +
      '<span>' + MC.parseToHtml(cat.display || cat.key) + '</span>' +
      '<span class="item-id">' + MC.escapeHtml(cat.key || '') + '</span>' +
      '<div class="ref-path">' + pathHtml + '</div>' +
      '</div>';
  });
  $('picker-results').innerHTML = html;
}

function pickRefCategory(index) {
  if (state.saving) { closePicker(); return; }
  var r = refCategoryResults[index];
  var cat = r.cat;
  var key = cat.key;
  if (!key) return;
  var pathParts = r.breadcrumb.map(function(bc) { return bc.key; });
  pathParts.push(key);
  var refPath = pathParts.join('/');
  var slot = findEmptySlot();
  if (slot < 0) { Toast.show('当前页已满 (36/36)', 'error'); return; }
  var newRef = { type: 'REFERENCE', ref: refPath, mode: 'custom', display: '', icon: { type: 'VANILLA', id: 'ARROW' }, glow: false, lore: [], page: state.currentPage, slot: slot };
  if (!state.selectedCategory.items) state.selectedCategory.items = [];
  state.selectedCategory.items.push(newRef);
  closePicker();
  markDirty();
  renderGrid();
  renderTree();
  Toast.show('已添加引用: \u21b3 ' + refPath, 'success');
}

async function searchMaterials() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(doSearch, 250);
}

async function doSearch() {
  if (state.pickerTarget === 'addReference') { searchReferenceCategories(); return; }
  var q = $('picker-search').value.trim();
  if (!q) { $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词开始搜索</div>'; return; }
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">搜索中...</div>';
  try {
    var url = '/api/materials?q=' + encodeURIComponent(q);
    if (state.pickerTarget === 'addItem') url += '&type=SLIMEFUN';
    var resp = await fetch(url);
    var data = await resp.json();
    var results = data.results || [];
    if (state.pickerTarget === 'addItem' && state.pickerFilter !== 'all') {
      var usedIds = collectUsedSlimefunIds();
      if (state.pickerFilter === 'added') {
        results = results.filter(function(r) { return usedIds.has(r.id); });
      } else if (state.pickerFilter === 'not_added') {
        results = results.filter(function(r) { return !usedIds.has(r.id); });
      }
    }
    var html = '';
    if (results.length > 0) {
      results.forEach(function(r) {
        var typeClass = r.type === 'VANILLA' ? 'vanilla' : (r.type === 'HEAD' ? 'head' : 'slimefun');
        var safeId = (r.id || '').replace(/'/g, "\\'").replace(/"/g, '&quot;');
        html += '<div class="picker-item" onclick="pickMaterial(\'' + r.type + '\',\'' + safeId + '\')">' +
          '<span class="item-type ' + typeClass + '">' + r.type + '</span>' +
          '<span>' + MC.parseToHtml(r.display || r.id) + '</span>' +
          '<span class="item-id">' + MC.escapeHtml(r.id) + '</span>' +
          '</div>';
      });
    } else {
      html = '<div style="padding:16px;color:#666;text-align:center;">无结果</div>';
    }
    $('picker-results').innerHTML = html;
  } catch(e) {
    $('picker-results').innerHTML = '<div style="padding:16px;color:#f85149;text-align:center;">搜索失败</div>';
  }
}

function pickMaterial(type, id) {
  if (state.saving) { closePicker(); return; }
  if (state.pickerTarget === 'icon') {
    if (state.selectedNode && state.selectedNode.type !== 'ITEM') {
      state.selectedNode.icon = { type: type, id: id };
      markDirty();
      renderEditor();
      renderGrid();
      Toast.show('图标已更新', 'success');
    }
  } else if (state.pickerTarget === 'addItem') {
    if (state.selectedCategory) {
      var catItemIds = collectCategoryItemIds(state.selectedCategory);
      if (catItemIds.has(id)) {
        Toast.show('物品 "' + id + '" 已存在于当前分类，不可重复添加', 'warning');
        closePicker();
        return;
      }
      var slot = findEmptySlot();
      if (slot < 0) { Toast.show('当前页已满 (36/36)', 'error'); closePicker(); return; }
      var newItem = { type: 'ITEM', id: id, display: id, page: state.currentPage, slot: slot };
      if (!state.selectedCategory.items) state.selectedCategory.items = [];
      state.selectedCategory.items.push(newItem);
      markDirty();
      renderGrid();
      renderTree();
      Toast.show('已添加物品', 'success');
    }
  }
  closePicker();
}

function prevPage() { if (state.currentPage > 1) { state.currentPage--; renderGrid(); } }
function nextPage() { var max = getMaxPage(); if (state.currentPage < max) { state.currentPage++; renderGrid(); } }

async function saveAll() {
  if (state.saving || state.reloading) return;
  state.saving = true;
  syncCurrentSelection();
  var savingDirtyVersion = state.dirtyVersion;
  var btn = $('btn-save');
  btn.disabled = true;
  btn.innerHTML = '<span class="btn-icon">\u23f3</span> 保存中...';
  updateSaveStatus();
  $('save-status').textContent = '保存中...';
  $('save-status').className = 'save-status saving';

  try {
    var body = JSON.stringify({ categories: state.categories });
    var resp = await fetch('/api/categories', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: body });
    if (resp.ok) {
      if (state.dirtyVersion === savingDirtyVersion) clearDirty();
      else updateSaveStatus();
      Toast.show('保存成功！分类已重新加载', 'success');
    } else {
      var data = null;
      try { data = await resp.json(); } catch(e) {}
      Toast.show('保存失败: ' + (data && data.error ? data.error : ('HTTP ' + resp.status)), 'error');
    }
  } catch(e) {
    Toast.show('保存失败: 无法连接服务器', 'error');
  } finally {
    state.saving = false;
    btn.disabled = false;
    btn.innerHTML = '<span class="btn-icon">\u{1F4BE}</span> 保存';
  }
}

document.addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); saveAll(); }
  if (e.key === 'ArrowLeft' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') prevPage();
  if (e.key === 'ArrowRight' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') nextPage();
  if (e.key === 'Backspace' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') {
    e.preventDefault();
    gridGoBack();
  }
});

window.addEventListener('beforeunload', function(e) {
  if (state.dirty) { e.preventDefault(); e.returnValue = '你有未保存的更改'; return e.returnValue; }
});

document.getElementById('picker-filters').addEventListener('click', function(e) {
  var btn = e.target.closest('.picker-filter-btn');
  if (!btn) return;
  var filter = btn.getAttribute('data-filter');
  if (filter) setPickerFilter(filter);
});

loadCategories();
