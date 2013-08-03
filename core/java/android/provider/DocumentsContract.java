/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import libcore.io.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The contract between a storage backend and the platform. Contains definitions
 * for the supported URIs and columns.
 */
public final class DocumentsContract {
    private static final String TAG = "Documents";

    // content://com.example/roots/
    // content://com.example/roots/sdcard/
    // content://com.example/roots/sdcard/docs/0/
    // content://com.example/roots/sdcard/docs/0/contents/
    // content://com.example/roots/sdcard/docs/0/search/?query=pony

    /**
     * MIME type of a document which is a directory that may contain additional
     * documents.
     *
     * @see #buildContentsUri(Uri)
     */
    public static final String MIME_TYPE_DIRECTORY = "vnd.android.cursor.dir/doc";

    /** {@hide} */
    public static final String META_DATA_DOCUMENT_PROVIDER = "android.content.DOCUMENT_PROVIDER";

    /**
     * {@link DocumentColumns#DOC_ID} value representing the root directory of a
     * storage root.
     */
    public static final String ROOT_DOC_ID = "0";

    /**
     * Flag indicating that a document is a directory that supports creation of
     * new files within it.
     *
     * @see DocumentColumns#FLAGS
     * @see #buildContentsUri(Uri)
     */
    public static final int FLAG_SUPPORTS_CREATE = 1;

    /**
     * Flag indicating that a document is renamable.
     *
     * @see DocumentColumns#FLAGS
     * @see #renameDocument(ContentResolver, Uri, String)
     */
    public static final int FLAG_SUPPORTS_RENAME = 1 << 1;

    /**
     * Flag indicating that a document is deletable.
     *
     * @see DocumentColumns#FLAGS
     */
    public static final int FLAG_SUPPORTS_DELETE = 1 << 2;

    /**
     * Flag indicating that a document can be represented as a thumbnail.
     *
     * @see DocumentColumns#FLAGS
     * @see #getThumbnail(ContentResolver, Uri, Point)
     */
    public static final int FLAG_SUPPORTS_THUMBNAIL = 1 << 3;

    /**
     * Flag indicating that a document is a directory that supports search.
     *
     * @see DocumentColumns#FLAGS
     */
    public static final int FLAG_SUPPORTS_SEARCH = 1 << 4;

    /**
     * Optimal dimensions for a document thumbnail request, stored as a
     * {@link Point} object. This is only a hint, and the returned thumbnail may
     * have different dimensions.
     *
     * @see ContentProvider#openTypedAssetFile(Uri, String, Bundle)
     */
    public static final String EXTRA_THUMBNAIL_SIZE = "thumbnail_size";

    /**
     * Extra boolean flag included in a directory {@link Cursor#getExtras()}
     * indicating that the backend can provide additional data if requested,
     * such as additional search results.
     */
    public static final String EXTRA_HAS_MORE = "has_more";

    /**
     * Extra boolean flag included in a {@link Cursor#respond(Bundle)} call to a
     * directory to request that additional data should be fetched. When
     * requested data is ready, the provider should send a change notification
     * to cause a requery.
     *
     * @see Cursor#respond(Bundle)
     * @see ContentResolver#notifyChange(Uri, android.database.ContentObserver,
     *      boolean)
     */
    public static final String EXTRA_REQUEST_MORE = "request_more";

    private static final String PATH_ROOTS = "roots";
    private static final String PATH_DOCS = "docs";
    private static final String PATH_CONTENTS = "contents";
    private static final String PATH_SEARCH = "search";

    public static final String PARAM_QUERY = "query";

    /**
     * Build URI representing the roots in a storage backend.
     */
    public static Uri buildRootsUri(String authority) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority).appendPath(PATH_ROOTS).build();
    }

    public static Uri buildRootUri(String authority, String rootId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority).appendPath(PATH_ROOTS).appendPath(rootId).build();
    }

    /**
     * Build URI representing the given {@link DocumentColumns#DOC_ID} in a
     * storage root.
     */
    public static Uri buildDocumentUri(String authority, String rootId, String docId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath(PATH_ROOTS).appendPath(rootId).appendPath(PATH_DOCS).appendPath(docId)
                .build();
    }

    /**
     * Build URI representing the contents of the given directory in a storage
     * backend. The given document must be {@link #MIME_TYPE_DIRECTORY}.
     */
    public static Uri buildContentsUri(String authority, String rootId, String docId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath(PATH_ROOTS).appendPath(rootId).appendPath(PATH_DOCS).appendPath(docId)
                .appendPath(PATH_CONTENTS).build();
    }

    /**
     * Build URI representing a search for matching documents under a directory
     * in a storage backend.
     */
    public static Uri buildSearchUri(String authority, String rootId, String docId, String query) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath(PATH_ROOTS).appendPath(rootId).appendPath(PATH_DOCS).appendPath(docId)
                .appendPath(PATH_SEARCH).appendQueryParameter(PARAM_QUERY, query).build();
    }

    public static Uri buildDocumentUri(Uri relatedUri, String docId) {
        return buildDocumentUri(relatedUri.getAuthority(), getRootId(relatedUri), docId);
    }

    public static Uri buildContentsUri(Uri relatedUri) {
        return buildContentsUri(
                relatedUri.getAuthority(), getRootId(relatedUri), getDocId(relatedUri));
    }

    public static Uri buildSearchUri(Uri relatedUri, String query) {
        return buildSearchUri(
                relatedUri.getAuthority(), getRootId(relatedUri), getDocId(relatedUri), query);
    }

    public static String getRootId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (!PATH_ROOTS.equals(paths.get(0))) {
            throw new IllegalArgumentException();
        }
        return paths.get(1);
    }

    public static String getDocId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (!PATH_ROOTS.equals(paths.get(0))) {
            throw new IllegalArgumentException();
        }
        if (!PATH_DOCS.equals(paths.get(2))) {
            throw new IllegalArgumentException();
        }
        return paths.get(3);
    }

    public static String getSearchQuery(Uri documentUri) {
        return documentUri.getQueryParameter(PARAM_QUERY);
    }

    /**
     * These are standard columns for document URIs. Storage backend providers
     * <em>must</em> support at least these columns when queried.
     *
     * @see Intent#ACTION_OPEN_DOCUMENT
     * @see Intent#ACTION_CREATE_DOCUMENT
     */
    public interface DocumentColumns extends OpenableColumns {
        /**
         * The ID for a document under a storage backend root. Values
         * <em>must</em> never change once returned. This field is read-only to
         * document clients.
         * <p>
         * Type: STRING
         */
        public static final String DOC_ID = "doc_id";

        /**
         * MIME type of a document, matching the value returned by
         * {@link ContentResolver#getType(android.net.Uri)}. This field must be
         * provided when a new document is created, but after that the field is
         * read-only.
         * <p>
         * Type: STRING
         *
         * @see DocumentsContract#MIME_TYPE_DIRECTORY
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * Timestamp when a document was last modified, in milliseconds since
         * January 1, 1970 00:00:00.0 UTC. This field is read-only to document
         * clients.
         * <p>
         * Type: INTEGER (long)
         *
         * @see System#currentTimeMillis()
         */
        public static final String LAST_MODIFIED = "last_modified";

        /**
         * Flags that apply to a specific document. This field is read-only to
         * document clients.
         * <p>
         * Type: INTEGER (int)
         */
        public static final String FLAGS = "flags";
    }

    public static final int ROOT_TYPE_SERVICE = 1;
    public static final int ROOT_TYPE_SHORTCUT = 2;
    public static final int ROOT_TYPE_DEVICE = 3;
    public static final int ROOT_TYPE_DEVICE_ADVANCED = 4;

    /**
     * These are standard columns for the roots URI.
     *
     * @see DocumentsContract#buildRootsUri(String)
     */
    public interface RootColumns {
        public static final String ROOT_ID = "root_id";

        /**
         * Storage root type, use for clustering.
         * <p>
         * Type: INTEGER (int)
         *
         * @see DocumentsContract#ROOT_TYPE_SERVICE
         * @see DocumentsContract#ROOT_TYPE_DEVICE
         */
        public static final String ROOT_TYPE = "root_type";

        /**
         * Icon resource ID for this storage root, or {@code 0} to use the
         * default {@link ProviderInfo#icon}.
         * <p>
         * Type: INTEGER (int)
         */
        public static final String ICON = "icon";

        /**
         * Title for this storage root, or {@code null} to use the default
         * {@link ProviderInfo#labelRes}.
         * <p>
         * Type: STRING
         */
        public static final String TITLE = "title";

        /**
         * Summary for this storage root, or {@code null} to omit.
         * <p>
         * Type: STRING
         */
        public static final String SUMMARY = "summary";

        /**
         * Number of free bytes of available in this storage root, or -1 if
         * unknown or unbounded.
         * <p>
         * Type: INTEGER (long)
         */
        public static final String AVAILABLE_BYTES = "available_bytes";
    }

    /**
     * Return thumbnail representing the document at the given URI. Callers are
     * responsible for their own caching. Given document must have
     * {@link #FLAG_SUPPORTS_THUMBNAIL} set.
     *
     * @return decoded thumbnail, or {@code null} if problem was encountered.
     */
    public static Bitmap getThumbnail(ContentResolver resolver, Uri documentUri, Point size) {
        final Bundle opts = new Bundle();
        opts.putParcelable(EXTRA_THUMBNAIL_SIZE, size);

        InputStream is = null;
        try {
            is = new AssetFileDescriptor.AutoCloseInputStream(
                    resolver.openTypedAssetFileDescriptor(documentUri, "image/*", opts));
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.w(TAG, "Failed to load thumbnail for " + documentUri + ": " + e);
            return null;
        } finally {
            IoUtils.closeQuietly(is);
        }
    }

    /**
     * Rename the document at the given URI. Given document must have
     * {@link #FLAG_SUPPORTS_RENAME} set.
     *
     * @return if rename was successful.
     */
    public static boolean renameDocument(
            ContentResolver resolver, Uri documentUri, String displayName) {
        final ContentValues values = new ContentValues();
        values.put(DocumentColumns.DISPLAY_NAME, displayName);
        return (resolver.update(documentUri, values, null, null) == 1);
    }
}
