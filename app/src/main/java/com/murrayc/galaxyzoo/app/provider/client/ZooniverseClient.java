/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app.provider.client;

import android.content.Context;
import android.support.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.murrayc.galaxyzoo.app.Log;
import com.murrayc.galaxyzoo.app.LoginUtils;
import com.murrayc.galaxyzoo.app.Utils;
import com.murrayc.galaxyzoo.app.provider.Config;
import com.murrayc.galaxyzoo.app.provider.HttpUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by murrayc on 10/10/14.
 */
public class ZooniverseClient {
    private final Context mContext;
    private final String mServerBaseUri;

    private RequestQueue mQueue = null;

    public ZooniverseClient(final Context context, final String serverBaseUri) {
        super();
        mContext = context;
        mServerBaseUri = serverBaseUri;

        //The MockContext used by ProviderTestCase2 (in our unit tests),
        //doesn't implement getPackageName(), but Volley.newRequestQueue()
        //calls it. Replacing that MockContext in ProviderTestCase2 is rather difficult,
        //so this is a quick workaround until we really need to use volley from our ContentProvider test:
        try {
            context.getPackageName();

            mQueue = Volley.newRequestQueue(context);
        } catch (final UnsupportedOperationException ex) {
            Log.info("ZooniverseClient: Not creating mQueue because context.getPackageName() would fail.");
            mQueue = null; //Just for the unit test.
        }
    }

    private static HttpURLConnection openConnection(final String strURL) throws IOException {
        final URL url= new URL(strURL);

        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setConnectionUserAgent(conn);

        //Set a reasonable timeout.
        //Otherwise there is no timeout so we might never know if it fails,
        //so never have the chance to try again.
        conn.setConnectTimeout(HttpUtils.TIMEOUT_MILLIS);
        conn.setReadTimeout(HttpUtils.TIMEOUT_MILLIS);

        return conn;
    }

    private static void setConnectionUserAgent(final HttpURLConnection connection) {
        connection.setRequestProperty(HttpUtils.HTTP_REQUEST_HEADER_PARAM_USER_AGENT, HttpUtils.getUserAgent());
    }

    /** Return a group ID selected at random.
     *
     * @return
     */
    private static String getGroupIdForNextQuery() {

        //Get a list of only the groups that should be used for new queries.
        //TODO: Avoid doing this each time?
        final List<String> groupIds = Config.getSubjectGroupsToUseForNewQueries();
        if(groupIds.size() == 1) {
            return groupIds.get(0);
        }

        final Object[] values = groupIds.toArray();
        final int idx = new SecureRandom().nextInt(values.length);
        return (String)values[idx];
    }

    /** Get the first part of the Query URI.
     * The caller should append a number to indicate how many items are being requested.
     * @return
     */
    private String getQueryMoreItemsUri() {
        /**
         * REST uri for querying items.
         * Like, the Galaxy-Zoo website's code, this hard-codes the Group ID for the Sloan survey:
         */
        return mServerBaseUri + "groups/" + getGroupIdForNextQuery() + "/subjects?limit="; //Should have a number, such as 5, appended.
    }

    private String getPostUploadUri(final String groupId) {
        return mServerBaseUri + "workflows/" + groupId + "/classifications";
    }

    private String getLoginUri() {
        return mServerBaseUri + "login";
    }

    @Nullable
    public LoginUtils.LoginResult loginSync(final String username, final String password) throws LoginException {
        HttpUtils.throwIfNoNetwork(getContext(),
                false); //Ignore the wifi-only setting because this will be when the user is explicitly requesting a login.

        final HttpURLConnection conn;
        try {
            conn = openConnection(getLoginUri());
        } catch (final IOException e) {
            Log.error("loginSync(): Could not open connection", e);

            throw new LoginException("Could not open connection.", e);
        }

        final List<HttpUtils.NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new HttpUtils.NameValuePair("username", username));
        nameValuePairs.add(new HttpUtils.NameValuePair("password", password));

        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            writeParamsToHttpPost(conn, nameValuePairs);

            conn.connect();
        } catch (final IOException e) {
            Log.error("loginSync(): exception during HTTP connection", e);

            conn.disconnect();

            throw new LoginException("Could not create POST.", e);
        }

        //Get the response:
        InputStream in = null;
        try {
            in = conn.getInputStream();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.error("loginSync(): response code: " + conn.getResponseCode());
                return null;
            }

            return LoginUtils.parseLoginResponseContent(in);
        } catch (final IOException e) {
            Log.error("loginSync(): exception during HTTP connection", e);

            throw new LoginException("Could not parse response.", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    Log.error("loginSync(): exception while closing in", e);
                }
            }

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void writeParamsToHttpPost(final HttpURLConnection conn, final List<HttpUtils.NameValuePair> nameValuePairs) throws IOException {
        OutputStream out = null;
        try {
            out = conn.getOutputStream();

            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(
                        new OutputStreamWriter(out, Utils.STRING_ENCODING));
                writer.write(HttpUtils.getPostDataBytes(nameValuePairs));
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (final IOException e) {
                    Log.error("writeParamsToHttpPost(): Exception while closing out", e);
                }
            }
        }
    }

    /** This will not always provide as many items as requested.
     *
     * @param count
     * @return
     */
    public List<Subject> requestMoreItemsSync(int count) throws RequestMoreItemsException {
        throwIfNoNetwork();

        //Avoid suddenly doing too much network and disk IO
        //as we download too many images.
        if (count > Config.MAXIMUM_DOWNLOAD_ITEMS) {
            count = Config.MAXIMUM_DOWNLOAD_ITEMS;
        }

        // Request a string response from the provided URL.
        // TODO: Use HttpUrlConnection directly instead of trying to use Volley synchronously?
        final RequestFuture<String> futureListener = RequestFuture.newFuture();
        requestMoreItemsAsync(count, futureListener, futureListener);

        String response = null;
        try {
            //Note: If we don't provider the RequestFuture as the errorListener too,
            //then this won't return until after the timeout, even if an error happen earlier.
            response = futureListener.get(HttpUtils.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException | ExecutionException e) {
            Log.error("requestMoreItemsSync(): Exception from request.", e);
            throw new RequestMoreItemsException("Exception from request.", e);
        } catch (final TimeoutException e) {
            Log.error("requestMoreItemsSync(): Timeout Exception from request.", e);
            throw new RequestMoreItemsException("Timeout Exception from request.", e);
        }

        //Presumably this happens when onErrorResponse() is called.
        if (response == null) {
            throw new RequestMoreItemsException("response is null.");
        }

        return MoreItemsJsonParser.parseMoreItemsResponseContent(response);
    }

    public void requestMoreItemsAsync(final int count, final Response.Listener<String> listener, final Response.ErrorListener errorListener) {
        throwIfNoNetwork();

        Log.info("requestMoreItemsAsync(): count=" + count);

        final Request request = new ZooStringRequest(Request.Method.GET,
                getQueryUri(count),
                listener,
                errorListener);

        //Identical requests for more items should get different results each time.
        request.setShouldCache(false);

        // Add the request to the RequestQueue.
        mQueue.add(request);
    }

    private String getQueryUri(final int count) {
        return getQueryMoreItemsUri() + Integer.toString(count); //TODO: Is Integer.toString() locale-dependent?
    }

    private void throwIfNoNetwork() {
        HttpUtils.throwIfNoNetwork(getContext());
    }

    private Context getContext() {
        return mContext;
    }

    public boolean uploadClassificationSync(final String authName, final String authApiKey, final String groupId, final List<HttpUtils.NameValuePair> nameValuePairs) throws UploadException {
        throwIfNoNetwork();

        final HttpURLConnection conn;
        try {
            conn = openConnection(getPostUploadUri(groupId));
        } catch (final IOException e) {
            Log.error("uploadClassificationSync(): Could not open connection", e);

            throw new UploadException("Could not open connection.", e);
        }

        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
        } catch (final IOException e) {
            Log.error("uploadClassificationSync: exception during HTTP connection", e);

            conn.disconnect();

            throw new UploadException("exception during HTTP connection", e);
        }

        //Add the authentication details to the headers;
        //Be careful: The server still returns OK_CREATED even if we provide the wrong Authorization here.
        //There doesn't seem to be any way to know if it's correct other than checking your recent
        //classifications in your profile.
        //See https://github.com/zooniverse/Galaxy-Zoo/issues/184
        if ((authName != null) && (authApiKey != null)) {
            conn.setRequestProperty("Authorization", HttpUtils.generateAuthorizationHeader(authName, authApiKey));
        }

        //This will be required by the newer API, but we don't ask for it already
        //because the current server wants it to be "application/x-www-form-urlencoded" instead,
        //See http://docs.panoptes.apiary.io/#introduction/authentication
        //conn.setRequestProperty(HttpUtils.HTTP_REQUEST_HEADER_PARAM_CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON);

        try {
            writeParamsToHttpPost(conn, nameValuePairs);
        } catch (final IOException e) {
            Log.error("uploadClassificationSync: writeParamsToHttpPost() failed", e);

            conn.disconnect();

            throw new UploadException("writeParamsToHttpPost() failed.", e);
        }

        //TODO: Is this necessary? conn.connect();

        //Get the response:
        InputStream in = null;
        try {
            //Note: At least with okhttp.mockwebserver, getInputStream() will throw an IOException (file
            //not found) if the response code was an error, such as HTTP_UNAUTHORIZED.
            in = conn.getInputStream();
            final int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                Log.error("uploadClassificationSync: Did not receive the 201 Created status code: " + conn.getResponseCode());
                return false;
            }

            return true;
        } catch (final IOException e) {
            Log.error("uploadClassificationSync: exception during HTTP connection", e);

            throw new UploadException("exception during HTTP connection", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    Log.error("uploadClassificationSync: exception while closing in", e);
                }
            }

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public RequestQueue getRequestQueue() {
        return mQueue;
    }

    /**
     * This class is meant to be immutable.
     * It only returns references to immutable Strings.
     */
    public static final class Subject {
        private final String mSubjectId;
        private final String mZooniverseId;
        private final String mGroupId;
        private final String mLocationStandard;
        private final String mLocationThumbnail;
        private final String mLocationInverted;

        public Subject(final String subjectId, final String zooniverseId, final String groupId, final String locationStandard, final String locationThumbnail, final String locationInverted) {
            super();
            this.mSubjectId = subjectId;
            this.mZooniverseId = zooniverseId;
            this.mGroupId = groupId;
            this.mLocationStandard = locationStandard;
            this.mLocationThumbnail = locationThumbnail;
            this.mLocationInverted = locationInverted;
        }

        public String getSubjectId() {
            return mSubjectId;
        }

        public String getZooniverseId() {
            return mZooniverseId;
        }

        public String getGroupId() {
            return mGroupId;
        }

        public String getLocationStandard() {
            return mLocationStandard;
        }

        public String getLocationThumbnail() {
            return mLocationThumbnail;
        }

        public String getLocationInverted() {
            return mLocationInverted;
        }

    }

    public static class LoginException extends Exception {
        LoginException(final String detail, final Exception cause) {
            super(detail, cause);
        }
    }

    public static class UploadException extends Exception {
        UploadException(final String detail, final Exception cause) {
            super(detail, cause);
        }
    }

    public static class RequestMoreItemsException extends Exception {
        RequestMoreItemsException(final String detail, final Exception cause) {
            super(detail, cause);
        }

        public RequestMoreItemsException(final String detail) {
            super(detail);
        }
    }
}
