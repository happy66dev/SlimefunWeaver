var state = {
  categories: [],
  selectedCategory: null,
  selectedNodeIndex: -1,
  selectedNode: null,
  currentPage: 1,
  pickerTarget: null
};

function $(id) { return document.getElementById(id); }

async function loadCategories() {
  var resp = await fetch('/api/categories');
  var data = await resp.json();
  state.categories = data.categories;
  renderTree();
}

function renderTree() {
  var root = $('tree-root');
  root.innerHTML = '';
  state.categories.forEach(function(cat, i) {
    root.appendChild(buildTreeItem(cat, i, null, 0));
  });
}

function buildTreeItem(cat, index, parentRef, depth) {
  var li = document.createElement('li');
  li.textContent = '\u{1F4C1} ' + (cat.display || cat.key);
  li.style.paddingLeft = (8 + depth * 12) + 'px';
  li.onclick = function(e) {
    e.stopPropagation();
    selectCategory(cat, index, parentRef);
  };
  if (state.selectedCategory === cat) li.classList.add('active');

  if (cat.children && cat.children.length > 0) {
    var ul = document.createElement('ul');
    cat.children.forEach(function(child, ci) {
      ul.appendChild(buildTreeItem(child, ci, cat, depth + 1));
    });
    li.appendChild(ul);
  }
  return li;
}

function selectCategory(cat, index, parentRef) {
  state.selectedCategory = cat;
  state.selectedNode = null;
  state.selectedNodeIndex = -1;
  state.currentPage = 1;
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
    return;
  }
  $('grid-title').textContent = (state.selectedCategory.display || state.selectedCategory.key || '未命名分类');
  $('grid-page-controls').style.display = 'flex';

  var maxPage = getMaxPage();
  state.currentPage = Math.min(state.currentPage, Math.max(1, maxPage));
  $('grid-page-info').textContent = '第 ' + state.currentPage + '/' + maxPage + ' 页';

  var pageItems = getPageItems(state.currentPage);
  for (var i = 0; i < 36; i++) {
    var cell = document.createElement('div');
    cell.className = 'grid-cell';
    cell.dataset.slot = i;
    cell.draggable = true;

    var item = null;
    var idx = -1;
    for (var j = 0; j < pageItems.length; j++) {
      if ((pageItems[j].slot || 0) === i) { item = pageItems[j]; idx = j; break; }
    }

    if (item) {
      cell.dataset.type = item.type || (item.icon ? 'CATEGORY' : 'ITEM');
      var typeLabel = item.type === 'PLACEHOLDER' ? 'P' : (item.icon ? 'C' : 'I');
      cell.innerHTML = '<span class="cell-type">' + typeLabel + '</span>' + (item.display || item.id || item.key || '?');
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
      var data = JSON.parse(e.dataTransfer.getData('text/plain'));
      var targetSlot = parseInt(e.target.dataset.slot);
      if (isNaN(targetSlot)) return;
      swapItems(data.page, data.slot, state.currentPage, targetSlot);
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
  renderGrid();
}

function selectGridItem(item, idx) {
  state.selectedNode = item;
  state.selectedNodeIndex = idx;
  renderGrid();
  renderEditor();
}

function renderEditor() {
  var node = state.selectedNode;
  if (!node) { $('editor-form').style.display = 'none'; $('editor-empty').style.display = 'block'; return; }
  $('editor-form').style.display = 'block';
  $('editor-empty').style.display = 'none';

  var isItem = node.type === 'ITEM';
  $('edit-display').value = node.display || '';
  $('edit-display').disabled = isItem;
  $('edit-lore').value = (node.lore || []).join('\n');
  $('edit-glow').checked = !!node.glow;
  $('edit-page').value = node.page || 1;

  var iconDisplay = '';
  if (node.icon) {
    iconDisplay = '[' + node.icon.type + '] ' + node.icon.id;
  } else if (node.id) {
    iconDisplay = '[ITEM] ' + node.id;
  } else {
    iconDisplay = '(无图标)';
  }
  $('edit-icon-display').textContent = iconDisplay;
}

function updateSelection() {
  var node = state.selectedNode;
  if (!node) return;
  var isItem = node.type === 'ITEM';
  if (!isItem) node.display = $('edit-display').value;
  node.lore = $('edit-lore').value.split('\n').filter(function(l) { return l.trim() !== ''; });
  node.glow = $('edit-glow').checked;
  node.page = parseInt($('edit-page').value) || 1;
  renderGrid();
  renderTree();
}

function deleteSelected() {
  if (!state.selectedNode || !state.selectedCategory) return;
  if (!confirm('确定删除此项？')) return;
  if (state.selectedNode.type === 'CATEGORY' || state.selectedNode.icon) {
    state.selectedCategory.children = (state.selectedCategory.children || []).filter(function(c) { return c !== state.selectedNode; });
  } else {
    state.selectedCategory.items = (state.selectedCategory.items || []).filter(function(it) { return it !== state.selectedNode; });
  }
  state.selectedNode = null;
  state.selectedNodeIndex = -1;
  renderGrid();
  renderTree();
  renderEditor();
}

function addCategory() {
  if (!state.selectedCategory) { alert('请先选择左侧分类'); return; }
  var key = prompt('分类 key（英文 ID）:', 'new_category');
  if (!key) return;
  var newCat = { key: key, display: key, icon: { type: 'VANILLA', id: 'BOOK' }, glow: false, lore: [], page: 1, slot: findEmptySlot(), children: [], items: [] };
  if (!state.selectedCategory.children) state.selectedCategory.children = [];
  state.selectedCategory.children.push(newCat);
  renderTree();
  state.selectedCategory = newCat;
  state.selectedNode = null;
  renderGrid();
}

function addPlaceholder() {
  if (!state.selectedCategory) { alert('请先选择左侧分类'); return; }
  var newPh = { type: 'PLACEHOLDER', display: '', icon: { type: 'VANILLA', id: 'GRAY_STAINED_GLASS_PANE' }, glow: false, lore: [], page: 1, slot: findEmptySlot() };
  if (!state.selectedCategory.items) state.selectedCategory.items = [];
  state.selectedCategory.items.push(newPh);
  renderGrid();
  renderTree();
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
  var menu = $('context-menu');
  var item = menu._targetItem;
  menu.style.display = 'none';
  if (!item) return;
  var pageStr = prompt('移动到第几页？', item.page || 1);
  if (!pageStr) return;
  var newPage = parseInt(pageStr);
  if (isNaN(newPage) || newPage < 1) return;
  var used = {};
  getChildren().forEach(function(it) { if (it !== item && (it.page || 1) === newPage) used[it.slot || 0] = true; });
  var targetSlot = 0;
  for (var i = 0; i < 36; i++) { if (!used[i]) { targetSlot = i; break; } }
  item.page = newPage;
  item.slot = targetSlot;
  state.currentPage = newPage;
  renderGrid();
  renderEditor();
}

function openItemPickerForAdd() {
  if (!state.selectedCategory) { alert('请先选择左侧分类'); return; }
  state.pickerTarget = 'addItem';
  $('picker-title').textContent = '添加物品';
  $('picker-overlay').style.display = 'flex';
  $('picker-search').value = '';
  $('picker-search').focus();
  searchMaterials();
}

function openIconPicker() {
  state.pickerTarget = 'icon';
  $('picker-title').textContent = '选择图标';
  $('picker-overlay').style.display = 'flex';
  $('picker-search').value = '';
  $('picker-search').focus();
  searchMaterials();
}

function closePicker() {
  $('picker-overlay').style.display = 'none';
  state.pickerTarget = null;
}

async function searchMaterials() {
  var q = $('picker-search').value.trim();
  if (!q) { $('picker-results').innerHTML = '<div style="padding:8px;color:#888;">输入关键词搜索...</div>'; return; }
  var resp = await fetch('/api/materials?q=' + encodeURIComponent(q));
  var data = await resp.json();
  var html = '';
  data.results.forEach(function(r) {
    var typeClass = r.type === 'VANILLA' ? 'vanilla' : 'slimefun';
    html += '<div class="picker-item" onclick="pickMaterial(\'' + r.type + '\',\'' + (r.id || '').replace(/'/g, "\\'") + '\')">' +
      '<span class="item-type ' + typeClass + '">' + r.type + '</span>' +
      '<span>' + (r.display || r.id) + '</span>' +
      '<span class="item-id">' + r.id + '</span>' +
      '</div>';
  });
  $('picker-results').innerHTML = html || '<div style="padding:8px;color:#888;">无结果</div>';
}

function pickMaterial(type, id) {
  if (state.pickerTarget === 'icon') {
    if (state.selectedNode && state.selectedNode.type !== 'ITEM') {
      state.selectedNode.icon = { type: type, id: id };
      renderEditor();
      renderGrid();
    }
  } else if (state.pickerTarget === 'addItem') {
    if (state.selectedCategory) {
      var newItem = { type: 'ITEM', id: id, display: id, page: state.currentPage, slot: findEmptySlot() };
      if (!state.selectedCategory.items) state.selectedCategory.items = [];
      state.selectedCategory.items.push(newItem);
      renderGrid();
      renderTree();
    }
  }
  closePicker();
}

function prevPage() { if (state.currentPage > 1) { state.currentPage--; renderGrid(); } }
function nextPage() { var max = getMaxPage(); if (state.currentPage < max) { state.currentPage++; renderGrid(); } }

async function saveAll() {
  var body = JSON.stringify({ categories: state.categories });
  var resp = await fetch('/api/categories', { method: 'PUT', body: body });
  alert(resp.ok ? '保存成功！分类已重新加载。' : '保存失败！');
}

loadCategories();
