/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2016 Aaron Madlon-Kay
               Home page: https://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.externalfinder.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The external search items of one scope, in file order. An item this OmegaT
 * cannot read, say one written by a newer version with placeholders this one
 * does not know, is kept as the XML it was written as and written back in
 * place, so saving here never drops what another version configured.
 */
public class ExternalFinderConfiguration {

    // the default value means the items may be placed at the top of popup menu.
    private static final int DEFAULT_POPUP_PRIORITY = 50;

    private final int priority;

    private final List<Entry> entries;

    /** One item element of the file: read into an item, or kept as written. */
    public static final class Entry {
        private final @Nullable ExternalFinderItem item;
        private final @Nullable String fragment;

        private Entry(@Nullable ExternalFinderItem item, @Nullable String fragment) {
            this.item = item;
            this.fragment = fragment;
        }

        public static Entry of(ExternalFinderItem item) {
            return new Entry(Objects.requireNonNull(item), null);
        }

        /** An item element this version could not read, as XML without declaration. */
        public static Entry kept(String fragment) {
            return new Entry(null, Objects.requireNonNull(fragment));
        }

        public @Nullable ExternalFinderItem getItem() {
            return item;
        }

        public @Nullable String getFragment() {
            return fragment;
        }

        public boolean isKept() {
            return fragment != null;
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, fragment);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) obj;
            return Objects.equals(item, other.item) && Objects.equals(fragment, other.fragment);
        }
    }

    public static ExternalFinderConfiguration empty() {
        return new ExternalFinderConfiguration(DEFAULT_POPUP_PRIORITY, Collections.emptyList());
    }

    public static ExternalFinderConfiguration ofEntries(int priority, List<Entry> entries) {
        return new ExternalFinderConfiguration(priority, entries, true);
    }

    public ExternalFinderConfiguration(int priority, List<ExternalFinderItem> items) {
        this(priority, toEntries(items), true);
    }

    private ExternalFinderConfiguration(int priority, List<Entry> entries, boolean marker) {
        this.priority = priority >= 0 ? priority : DEFAULT_POPUP_PRIORITY;
        this.entries = new ArrayList<>(entries);
    }

    private static List<Entry> toEntries(List<ExternalFinderItem> items) {
        List<Entry> result = new ArrayList<>(items.size());
        for (ExternalFinderItem item : items) {
            result.add(Entry.of(item));
        }
        return result;
    }

    /**
     * The same configuration with other priority and items, as the editing
     * page produces it. The kept entries stay where they were in the file.
     */
    public ExternalFinderConfiguration withItems(int newPriority, List<ExternalFinderItem> newItems) {
        List<Entry> result = toEntries(newItems);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.isKept()) {
                result.add(Math.min(i, result.size()), entry);
            }
        }
        return new ExternalFinderConfiguration(newPriority, result, true);
    }

    public int getPriority() {
        return priority;
    }

    /** Items and kept item elements in file order. */
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** The items this version read, in file order. */
    public List<ExternalFinderItem> getItems() {
        List<ExternalFinderItem> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.item != null) {
                result.add(entry.item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** XML of the item elements this version could not read, in file order. */
    public List<String> getUnreadableItems() {
        List<String> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.fragment != null) {
                result.add(entry.fragment);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** True when there is nothing to write, not even a kept item. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, entries);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ExternalFinderConfiguration)) {
            return false;
        }
        ExternalFinderConfiguration other = (ExternalFinderConfiguration) obj;
        return priority == other.priority && entries.equals(other.entries);
    }
}
