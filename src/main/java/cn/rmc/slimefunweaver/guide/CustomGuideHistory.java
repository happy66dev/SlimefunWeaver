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
package cn.rmc.slimefunweaver.guide;

import cn.rmc.slimefunweaver.model.CustomCategory;

import java.util.Deque;
import java.util.LinkedList;

public class CustomGuideHistory {

    public abstract static class HistoryEntry {
        public abstract boolean isCategory();
        public abstract int getPage();
    }

    public static class CategoryEntry extends HistoryEntry {
        private final CustomCategory category;
        private int page;

        public CategoryEntry(CustomCategory category, int page) {
            this.category = category;
            this.page = page;
        }

        public CustomCategory getCategory() { return category; }
        @Override public int getPage() { return page; }
        @Override public boolean isCategory() { return true; }
        public void setPage(int page) { this.page = page; }
    }

    public static class ItemEntry extends HistoryEntry {
        private final String slimefunId;

        public ItemEntry(String slimefunId) {
            this.slimefunId = slimefunId;
        }

        public String getSlimefunId() { return slimefunId; }
        @Override public int getPage() { return 1; }
        @Override public boolean isCategory() { return false; }
    }

    private final Deque<HistoryEntry> stack = new LinkedList<>();
    private int mainMenuPage = 1;
    private CustomCategory currentCategory = null;
    private int currentPage = 1;

    public void clear() {
        stack.clear();
        currentCategory = null;
        currentPage = 1;
    }

    public void setMainMenuPage(int page) { this.mainMenuPage = Math.max(1, page); }
    public int getMainMenuPage() { return mainMenuPage; }

    public void setCurrentCategory(CustomCategory cat) { this.currentCategory = cat; }
    public CustomCategory getCurrentCategory() { return currentCategory; }
    public void setCurrentPage(int page) { this.currentPage = Math.max(1, page); }
    public int getCurrentPage() { return currentPage; }

    public void push(CustomCategory category, int page) {
        HistoryEntry last = stack.peekLast();
        if (last instanceof CategoryEntry && ((CategoryEntry) last).getCategory().getKey().equals(category.getKey())) {
            ((CategoryEntry) last).setPage(page);
        } else {
            stack.addLast(new CategoryEntry(category, page));
        }
    }

    public void pushForce(CustomCategory category, int page) {
        stack.addLast(new CategoryEntry(category, page));
    }

    public void pushItem(String slimefunId) {
        stack.addLast(new ItemEntry(slimefunId));
    }

    public boolean hasHistory() { return !stack.isEmpty(); }

    public HistoryEntry goBack() {
        if (!stack.isEmpty()) stack.removeLast();
        return stack.peekLast();
    }

    public HistoryEntry getCurrent() { return stack.peekLast(); }

    public Deque<HistoryEntry> getStack() { return stack; }
}
