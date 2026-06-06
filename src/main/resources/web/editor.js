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
        if (code && '0123456789abcdefABCDEFklmno'.indexOf(code) >= 0) {
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
      if ((text[i] === '\u00a7' || text[i] === '&') && '0123456789abcdefABCDEFklmno'.indexOf(text[i+1]) >= 0) { i += 2; continue; }
      out += text[i]; i++;
    }
    return out;
  },
  escapeHtml: function(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }
};

var Toast = {
  show: function(msg, type, duration) {
    type = type || 'info'; duration = duration || 2600;
    var container = document.getElementById('toast-container');
    var el = document.createElement('div');
    el.className = 'toast ' + type;
    var icons = {success:'\u2714', error:'\u2716', warning:'\u26a0', info:'\u2139'};
    el.innerHTML = '<span>' + (icons[type]||'') + '</span> ' + msg;
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
  currentPage: 1, pickerTarget: null, dirty: false
};

function $(id) { return document.getElementById(id); }

function isRootCategory(node) {
  if (!node || !state.categories) return false;
  for (var i = 0; i < state.categories.length; i++) {
    if (state.categories[i] === node) return true;
  }
  return false;
}

function markDirty() {
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
    var data = await resp.json();
    state.categories = data.categories;
    clearDirty();
    renderTree();
  } catch(e) { Toast.show('无法连接到服务器', 'error'); }
}

async function discardChanges() {
  Dialog.confirm('放弃所有未保存的修改？此操作会从服务器重新加载配置。', function(ok) {
    if (!ok) return;
    loadCategories().then(function() {
      state.selectedCategory = null;
      state.selectedNode = null;
      state.currentPage = 1;
      renderGrid();
      renderEditor();
      Toast.show('已从服务器重新加载', 'info');
    });
  });
}

function renderTree() {
  var root = $('tree-root');
  root.innerHTML = '';
  var cats = state.categories || [];
  cats.forEach(function(cat, i) { root.appendChild(buildTreeItem(cat, i, null, 0)); });
}

function buildTreeItem(cat, index, parentRef, depth) {
  var li = document.createElement('li');
  li.style.paddingLeft = (depth * 14) + 'px';
  var hasChildren = cat.children && cat.children.length > 0;
  var hasItems = cat.items && cat.items.length > 0;
  var totalChildren = (cat.children ? cat.children.length : 0) + (cat.items ? cat.items.length : 0);

  var icon = hasChildren ? '\u{1F4C1}' : '\u{1F4C4}';
  li.innerHTML = '<span class="tree-icon">' + icon + '</span><span class="tree-label" title="' + MC.strip(cat.display||cat.key) + '">' + MC.parseToHtml(cat.display||cat.key) + '</span>' + (totalChildren > 0 ? '<span class="tree-badge">' + totalChildren + '</span>' : '');

  li.onclick = function(e) { e.stopPropagation(); selectCategory(cat, index, parentRef); };
  if (state.selectedNode === cat) li.classList.add('active');

  if (hasChildren) {
    var ul = document.createElement('ul');
    cat.children.forEach(function(child, ci) { ul.appendChild(buildTreeItem(child, ci, cat, depth + 1)); });
    li.appendChild(ul);
  }
  return li;
}

function selectCategory(cat, index, parentRef) {
  state.selectedNode = cat;
  if (parentRef) {
    state.selectedCategory = parentRef;
    state.currentPage = cat.page || 1;
  } else {
    state.selectedCategory = cat;
    state.currentPage = 1;
  }
  renderTree();
  renderGrid();
  renderEditor();
}

function getChildren() {
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

function renderGrid() {
  var grid = $('grid');
  grid.innerHTML = '';
  if (!state.selectedCategory) {
    $('grid-title').textContent = '请选择左侧分类';
    $('grid-page-controls').style.display = 'none';
    $('grid-empty').style.display = 'none';
    return;
  }
  $('grid-title').innerHTML = MC.parseToHtml(state.selectedCategory.display || state.selectedCategory.key || '未命名分类');
  $('grid-page-controls').style.display = 'flex';

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
      if (item.type === 'PLACEHOLDER') { typeLabel = '占位'; typeClass = 'placeholder'; cell.setAttribute('data-placeholder',''); }
      else if (item.icon || item.type === 'CATEGORY') { typeLabel = '分类'; typeClass = 'category'; cell.setAttribute('data-category',''); }
      else { typeLabel = '物品'; typeClass = 'item'; }

      var displayName = item.display || item.id || item.key || '?';
      if (typeClass === 'item') displayName = item.display || item.id || '?';
      cell.innerHTML = '<span class="cell-type-badge ' + typeClass + '">' + typeLabel + '</span><span class="cell-name">' + MC.parseToHtml(displayName) + '</span>';
      if (state.selectedNode === item) cell.classList.add('selected');

      cell.onclick = (function(it, iIndex) { return function(e) {
        e.stopPropagation();
        selectGridItem(it, iIndex);
      }; })(item, idx);

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
  if (!node) { $('editor-form').style.display = 'none'; $('editor-empty').style.display = 'block'; return; }
  $('editor-form').style.display = 'block';
  $('editor-empty').style.display = 'none';

  var isItem = node.type === 'ITEM';
  var isRoot = isRootCategory(node);

  $('edit-display').value = node.display || '';
  $('edit-display').disabled = isItem || isRoot;
  $('edit-lore').value = (node.lore || []).join('\n');
  $('edit-lore').disabled = isItem || isRoot;
  $('edit-glow').checked = !!node.glow;
  $('edit-glow').disabled = isItem || isRoot;
  $('btn-pick-icon').style.display = (isItem || isRoot) ? 'none' : '';
  $('edit-page').value = node.page || 1;

  updateDisplayPreview();
  updateLorePreview();

  var iconDisplay = '';
  if (node.icon) { iconDisplay = '[' + node.icon.type + '] ' + node.icon.id; }
  else if (node.id) { iconDisplay = '[ITEM] ' + node.id; }
  else { iconDisplay = '(无图标)'; }
  $('edit-icon-display').textContent = iconDisplay;
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
  var isRoot = isRootCategory(node);
  if (!isItem && !isRoot) node.display = $('edit-display').value;
  if (!isItem && !isRoot) node.lore = $('edit-lore').value.split('\n');
  if (!isItem && !isRoot) node.glow = $('edit-glow').checked;
  node.page = parseInt($('edit-page').value) || 1;
  state.currentPage = node.page;
  updateDisplayPreview();
  updateLorePreview();
  markDirty();
  renderGrid();
  renderTree();
}

document.getElementById('edit-display').addEventListener('input', updateDisplayPreview);
document.getElementById('edit-lore').addEventListener('input', updateLorePreview);

var ColorPicker = {
  colors: [
    {code:'0',hex:'#000000',name:'Black'},
    {code:'1',hex:'#0000AA',name:'Dark Blue'},
    {code:'2',hex:'#00AA00',name:'Dark Green'},
    {code:'3',hex:'#00AAAA',name:'Dark Aqua'},
    {code:'4',hex:'#AA0000',name:'Dark Red'},
    {code:'5',hex:'#AA00AA',name:'Dark Purple'},
    {code:'6',hex:'#FFAA00',name:'Gold'},
    {code:'7',hex:'#AAAAAA',name:'Gray'},
    {code:'8',hex:'#555555',name:'Dark Gray'},
    {code:'9',hex:'#5555FF',name:'Blue'},
    {code:'a',hex:'#55FF55',name:'Green'},
    {code:'b',hex:'#55FFFF',name:'Aqua'},
    {code:'c',hex:'#FF5555',name:'Red'},
    {code:'d',hex:'#FF55FF',name:'Light Purple'},
    {code:'e',hex:'#FFFF55',name:'Yellow'},
    {code:'f',hex:'#FFFFFF',name:'White'}
  ],
  formats: [
    {code:'l',label:'&l 粗体'},
    {code:'m',label:'&m 删除线'},
    {code:'n',label:'&n 下划线'},
    {code:'o',label:'&o 斜体'},
    {code:'k',label:'&k 闪烁'},
    {code:'r',label:'&r 重置'}
  ],
  _target: null,
  init: function() {
    var grid = document.getElementById('color-picker-grid');
    grid.innerHTML = '';
    var self = this;
    this.colors.forEach(function(c) {
      var chip = document.createElement('div');
      chip.className = 'color-chip ' + (parseInt(c.code,16) >= 8 ? 'color-chip-dark' : 'color-chip-light');
      chip.style.background = c.hex;
      chip.textContent = '&' + c.code;
      chip.title = c.name;
      chip.onmousedown = function(e) { e.preventDefault(); self.pick('&' + c.code); };
      grid.appendChild(chip);
    });
    var formats = document.getElementById('color-picker-formats');
    formats.innerHTML = '';
    this.formats.forEach(function(f) {
      var chip = document.createElement('span');
      chip.className = 'format-chip';
      chip.textContent = f.label;
      chip.title = '插入 ' + f.code;
      chip.onmousedown = function(e) { e.preventDefault(); self.pick('&' + f.code); };
      formats.appendChild(chip);
    });
    var tip = document.createElement('div');
    tip.className = 'color-picker-tip';
    tip.textContent = '点击插入 ESC关闭';
    formats.appendChild(tip);
  },
  show: function(input) {
    this._target = input;
    var picker = document.getElementById('color-picker');
    if (!picker.hasChildNodes() || picker.children.length === 0) { this.init(); }
    var rect = input.getBoundingClientRect();
    picker.style.left = rect.left + 'px';
    picker.style.top = (rect.bottom + 4) + 'px';
    picker.style.display = 'block';
    var self = this;
    setTimeout(function() {
      document.addEventListener('mousedown', self._hideHandler = function(e) {
        if (!picker.contains(e.target)) { self.hide(); }
      });
    }, 0);
  },
  hide: function() {
    document.getElementById('color-picker').style.display = 'none';
    this._target = null;
    if (this._hideHandler) { document.removeEventListener('mousedown', this._hideHandler); this._hideHandler = null; }
  },
  pick: function(code) {
    var input = this._target;
    if (!input) return;
    var pos = this._getCursorPos(input);
    var before = input.value.substring(0, pos);
    var after = input.value.substring(pos);
    var ampIdx = before.lastIndexOf('&');
    if (ampIdx < 0) { input.value = before + code + after; }
    else { input.value = before.substring(0, ampIdx) + code + after; }
    this.hide();
    input.focus();
    this._setCursorPos(input, ampIdx >= 0 ? ampIdx + code.length : before.length + code.length);
    input.dispatchEvent(new Event('input', {bubbles:true}));
    input.dispatchEvent(new Event('change', {bubbles:true}));
    markDirty();
  },
  _getCursorPos: function(input) {
    if (input.selectionStart !== undefined) return input.selectionStart;
    return input.value.length;
  },
  _setCursorPos: function(input, pos) {
    if (input.setSelectionRange) input.setSelectionRange(pos, pos);
  }
};

(function() {
  function onInputKey(e) {
    if (e.key === '&') {
      ColorPicker.show(e.target);
    }
    if (e.key === 'Escape') {
      ColorPicker.hide();
    }
  }
  document.getElementById('edit-display').addEventListener('keydown', onInputKey);
  document.getElementById('edit-lore').addEventListener('keydown', onInputKey);
})();

function deleteSelected() {
  if (!state.selectedNode || !state.selectedCategory) return;
  Dialog.confirm('确定要删除此项吗？此操作无法撤销。', function(ok) {
    if (!ok) return;
    if (state.selectedNode.type === 'CATEGORY') {
      state.selectedCategory.children = (state.selectedCategory.children || []).filter(function(c) { return c !== state.selectedNode; });
    } else {
      state.selectedCategory.items = (state.selectedCategory.items || []).filter(function(it) { return it !== state.selectedNode; });
    }
    state.selectedNode = null;
    markDirty();
    renderGrid();
    renderTree();
    renderEditor();
    Toast.show('已删除', 'info');
  });
}

function addCategory() {
  if (!state.selectedCategory) { Toast.show('请先选择左侧分类', 'warning'); return; }
  Dialog.prompt('输入分类 key（英文 ID）', 'new_category', function(key) {
    if (!key) return;
    var newCat = { key: key, display: key, icon: { type: 'VANILLA', id: 'BOOK' }, glow: false, lore: [], page: 1, slot: findEmptySlot(), children: [], items: [] };
    if (!state.selectedCategory.children) state.selectedCategory.children = [];
    state.selectedCategory.children.push(newCat);
    markDirty();
    renderTree();
    state.selectedNode = newCat;
    renderGrid();
    renderEditor();
    Toast.show('已添加分类: ' + MC.strip(key), 'success');
  });
}

function addPlaceholder() {
  if (!state.selectedCategory) { Toast.show('请先选择左侧分类', 'warning'); return; }
  var newPh = { type: 'PLACEHOLDER', display: '', icon: { type: 'VANILLA', id: 'GRAY_STAINED_GLASS_PANE' }, glow: false, lore: [], page: 1, slot: findEmptySlot() };
  if (!state.selectedCategory.items) state.selectedCategory.items = [];
  state.selectedCategory.items.push(newPh);
  markDirty();
  renderGrid();
  renderTree();
  Toast.show('已添加占位物品', 'success');
}

function addItem() {
  if (!state.selectedCategory) { Toast.show('请先选择左侧分类', 'warning'); return; }
  state.pickerTarget = 'addItem';
  $('picker-title').textContent = '添加物品';
  $('picker-overlay').style.display = 'flex';
  var input = $('picker-search');
  input.value = '';
  input.placeholder = '搜索物品名或ID...';
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词开始搜索</div>';
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
  state.pickerTarget = null;
}

function findEmptySlot() {
  var page = state.currentPage;
  var used = {};
  getChildren().forEach(function(item) { if ((item.page || 1) === page) used[item.slot || 0] = true; });
  for (var i = 0; i < 36; i++) { if (!used[i]) return i; }
  return 0;
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
async function searchMaterials() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(doSearch, 250);
}

async function doSearch() {
  var q = $('picker-search').value.trim();
  if (!q) { $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">输入关键词开始搜索</div>'; return; }
  $('picker-results').innerHTML = '<div style="padding:16px;color:#666;text-align:center;">搜索中...</div>';
  try {
    var resp = await fetch('/api/materials?q=' + encodeURIComponent(q));
    var data = await resp.json();
    var html = '';
    if (data.results && data.results.length > 0) {
      data.results.forEach(function(r) {
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
      var newItem = { type: 'ITEM', id: id, display: id, page: state.currentPage, slot: findEmptySlot() };
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
  var btn = $('btn-save');
  btn.disabled = true;
  btn.innerHTML = '<span class="btn-icon">\u23f3</span> 保存中...';
  updateSaveStatus();
  $('save-status').textContent = '保存中...';
  $('save-status').className = 'save-status saving';

  try {
    var body = JSON.stringify({ categories: state.categories });
    var resp = await fetch('/api/categories', { method: 'PUT', body: body });
    if (resp.ok) {
      clearDirty();
      Toast.show('保存成功！分类已重新加载', 'success');
    } else {
      Toast.show('保存失败: HTTP ' + resp.status, 'error');
    }
  } catch(e) {
    Toast.show('保存失败: 无法连接服务器', 'error');
  }

  btn.disabled = false;
  btn.innerHTML = '<span class="btn-icon">\u{1F4BE}</span> 保存';
}

document.addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); saveAll(); }
  if (e.key === 'ArrowLeft' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') prevPage();
  if (e.key === 'ArrowRight' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') nextPage();
});

window.addEventListener('beforeunload', function(e) {
  if (state.dirty) { e.preventDefault(); e.returnValue = '你有未保存的更改'; return e.returnValue; }
});

loadCategories();
