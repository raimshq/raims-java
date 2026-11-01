package com.raims;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.raims.exception.UnexpectedResponseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;

/** A cursor page. Iteration over this object visits only its current items. */
public class RaimsCollection<T extends ApiResource> extends ApiResource implements Iterable<T> {
    private final List<T> list;
    private volatile BiFunction<String, Boolean, RaimsCollection<T>> pageLoader;
    private String initialAfter;
    private String initialBefore;

    public RaimsCollection(JsonObject data, ApiResponse response, BiFunction<JsonObject, ApiResponse, T> factory) {
        super(data, response);
        List<T> values = new ArrayList<>();
        if (this.data.has("list")) {
            for (JsonElement item : this.data.getAsJsonArray("list")) values.add(factory.apply(item.getAsJsonObject(), response));
        }
        list = Collections.unmodifiableList(values);
    }

    public final List<T> getList() { return list; }
    public JsonObject getPage() { return objectField("page"); }
    @Override public final Iterator<T> iterator() { return list.iterator(); }

    /** Internal hook used by the requestor to retain immutable request settings. */
    public final synchronized void attachPageLoader(BiFunction<String, Boolean, RaimsCollection<T>> loader,
            String after, String before) {
        if (pageLoader != null) throw new IllegalStateException("The page loader is already attached.");
        pageLoader = loader; initialAfter = after; initialBefore = before;
    }

    public final RaimsCollection<T> nextPage() { return navigate(false); }
    public final RaimsCollection<T> previousPage() { return navigate(true); }

    private RaimsCollection<T> navigate(boolean previous) {
        JsonObject page = getPage();
        String flag = previous ? "has_previous" : "has_next";
        if (page == null || !page.has(flag) || !page.get(flag).getAsBoolean()) return null;
        String cursor = cursor(page, previous);
        if (cursor.equals(previous ? initialBefore : initialAfter)) throw paginationFailure();
        BiFunction<String, Boolean, RaimsCollection<T>> loader = pageLoader;
        if (loader == null) throw new IllegalStateException("This page is not associated with a request.");
        return loader.apply(cursor, previous);
    }

    public final Iterable<T> autoPagingIterable() {
        return () -> new Iterator<T>() {
            private RaimsCollection<T> page = RaimsCollection.this;
            private Iterator<T> items = page.iterator();
            private final Set<String> seen = new HashSet<>();
            { if (initialAfter != null) seen.add(initialAfter); }

            @Override public boolean hasNext() {
                while (!items.hasNext()) {
                    JsonObject info = page.getPage();
                    if (info == null || !info.has("has_next") || !info.get("has_next").getAsBoolean()) return false;
                    String next = cursor(info, false);
                    if (seen.contains(next)) throw page.paginationFailure();
                    RaimsCollection<T> nextPage = page.nextPage();
                    seen.add(next);
                    page = nextPage;
                    items = page.iterator();
                }
                return true;
            }

            @Override public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return items.next();
            }
        };
    }

    public final List<T> autoPagingToList(int limit) {
        if (limit < 1 || limit > 1000000) throw new IllegalArgumentException("limit must be between 1 and 1000000.");
        List<T> values = new ArrayList<>();
        Iterator<T> iterator = autoPagingIterable().iterator();
        while (values.size() < limit && iterator.hasNext()) values.add(iterator.next());
        return Collections.unmodifiableList(values);
    }

    private String cursor(JsonObject page, boolean previous) {
        JsonElement value = page.get(previous ? "previous_before" : "next_after");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty()) throw paginationFailure();
        return value.getAsString();
    }

    private UnexpectedResponseException paginationFailure() {
        return new UnexpectedResponseException("Pagination returned a missing or repeated cursor.", getLastResponse());
    }
}
