package fr.gouv.etalab.mastodon.client;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Application;
import fr.gouv.etalab.mastodon.client.Entities.Attachment;
import fr.gouv.etalab.mastodon.client.Entities.Card;
import fr.gouv.etalab.mastodon.client.Entities.Conversation;
import fr.gouv.etalab.mastodon.client.Entities.Emojis;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.client.Entities.Filters;
import fr.gouv.etalab.mastodon.client.Entities.HowToVideo;
import fr.gouv.etalab.mastodon.client.Entities.Instance;
import fr.gouv.etalab.mastodon.client.Entities.InstanceSocial;
import fr.gouv.etalab.mastodon.client.Entities.Mention;
import fr.gouv.etalab.mastodon.client.Entities.Notification;
import fr.gouv.etalab.mastodon.client.Entities.Peertube;
import fr.gouv.etalab.mastodon.client.Entities.Relationship;
import fr.gouv.etalab.mastodon.client.Entities.Results;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.Entities.Tag;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;


/**
 * Created by Thomas on 23/04/2017.
 * Manage Calls to the REST API
 * Modify the 16/11/2017 with httpsurlconnection
 */

public class API {


    private Account account;
    private Context context;
    private Results results;
    private Attachment attachment;
    private List<Account> accounts;
    private List<Status> statuses;
    private List<Conversation> conversations;
    private int tootPerPage, accountPerPage, notificationPerPage;
    private int actionCode;
    private String instance;
    private String prefKeyOauthTokenT;
    private APIResponse apiResponse;
    private Error APIError;
    private List<String> domains;

    public enum StatusAction {
        FAVOURITE,
        UNFAVOURITE,
        REBLOG,
        UNREBLOG,
        MUTE,
        MUTE_NOTIFICATIONS,
        UNMUTE,
        BLOCK,
        UNBLOCK,
        FOLLOW,
        UNFOLLOW,
        CREATESTATUS,
        UNSTATUS,
        AUTHORIZE,
        REJECT,
        REPORT,
        REMOTE_FOLLOW,
        PIN,
        UNPIN,
        ENDORSE,
        UNENDORSE,
        SHOW_BOOST,
        HIDE_BOOST,
        BLOCK_DOMAIN

    }

    public enum accountPrivacy {
        PUBLIC,
        LOCKED
    }

    public API(Context context) {
        this.context = context;
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        tootPerPage = sharedpreferences.getInt(Helper.SET_TOOTS_PER_PAGE, 40);
        accountPerPage = sharedpreferences.getInt(Helper.SET_ACCOUNTS_PER_PAGE, 40);
        notificationPerPage = sharedpreferences.getInt(Helper.SET_NOTIFICATIONS_PER_PAGE, 15);
        if (Helper.getLiveInstance(context) != null)
            this.instance = Helper.getLiveInstance(context);
        else {
            SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
            Account account = new AccountDAO(context, db).getAccountByID(userId);
            this.instance = account.getInstance().trim();
        }
        this.prefKeyOauthTokenT = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
        apiResponse = new APIResponse();
        APIError = null;
    }

    public API(Context context, String instance, String token) {
        this.context = context;
        if (context == null) {
            apiResponse = new APIResponse();
            APIError = new Error();
            return;
        }
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        tootPerPage = sharedpreferences.getInt(Helper.SET_TOOTS_PER_PAGE, 40);
        accountPerPage = sharedpreferences.getInt(Helper.SET_ACCOUNTS_PER_PAGE, 40);
        notificationPerPage = sharedpreferences.getInt(Helper.SET_NOTIFICATIONS_PER_PAGE, 15);
        if (instance != null)
            this.instance = instance;
        else
            this.instance = Helper.getLiveInstance(context);

        if (token != null)
            this.prefKeyOauthTokenT = token;
        else
            this.prefKeyOauthTokenT = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
        apiResponse = new APIResponse();
        APIError = null;
    }


    /***
     * Get info on the current Instance *synchronously*
     * @return APIResponse
     */
    public APIResponse getInstance() {
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/meta"), 30, null, null);
            Instance instanceEntity = parseInstance(new JSONObject(response));
            apiResponse.setInstance(instanceEntity);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }


    /***
     * Update credential of the authenticated user *synchronously*
     * @return APIResponse
     */
    public APIResponse updateCredential(String display_name, String note, ByteArrayInputStream avatar, String avatarName, ByteArrayInputStream header, String headerName, accountPrivacy privacy, HashMap<String, String> customFields) {

        JSONObject requestParams = new JSONObject();
        try {
            if (display_name != null)
                try {
                    requestParams.put("name", URLEncoder.encode(display_name, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    requestParams.put("name", display_name);
                }
            if (note != null)
                try {
                    requestParams.put("description", URLEncoder.encode(note, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    requestParams.put("description", note);
                }
            if (privacy != null)
                requestParams.put("isLocked", privacy == accountPrivacy.LOCKED ? "true" : "false");
            // missing avatar and header
            // requires uploading each file to drive then getting id
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            new HttpsConnection(context).post(getAbsoluteUrl("/i/update"), 60, requestParams, prefKeyOauthTokenT);
        } catch (HttpsConnection.HttpsConnectionException e) {
            e.printStackTrace();
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }

    /***
     * Verifiy credential of the authenticated user *synchronously*
     * @return Account
     */
    public Account verifyCredentials() {
        account = new Account();
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/i"), 60, null, prefKeyOauthTokenT);
            account = parseAccountResponse(context, new JSONObject(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return account;
    }

    /**
     * Returns an account
     *
     * @param accountId String account fetched
     * @return Account entity
     */
    public Account getAccount(String accountId) {

        account = new Account();
        JSONObject params = new JSONObject();
        try {
            params.put("userId", accountId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/users/show"), 60, null, null);
            account = parseAccountResponse(context, new JSONObject(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return account;
    }


    /**
     * Returns a relationship between the authenticated account and an account
     *
     * @param accountId String account fetched
     * @return Relationship entity
     */
    public Relationship getRelationship(String accountId) {

        List<Relationship> relationships;
        Relationship relationship = null;
        HashMap<String, String> params = new HashMap<>();
        params.put("userId", accountId);
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/users/relation"), 60, new JSONObject(params), prefKeyOauthTokenT);
            relationships = parseRelationshipResponseList(new JSONObject(response));
            if (relationships != null && relationships.size() > 0)
                relationship = relationships.get(0);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return relationship;
    }


    /**
     * Returns a relationship between the authenticated account and an account
     *
     * @param accounts ArrayList<Account> accounts fetched
     * @return Relationship entity
     */
    public APIResponse getRelationship(List<Account> accounts) {
        JSONObject params = new JSONObject();
        if (accounts != null && accounts.size() > 0) {
            JSONArray parameters = new JSONArray();
            for (Account account : accounts)
                parameters.put(account.getId());
            try {
                params.put("userId", parameters);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            List<Relationship> relationships = new ArrayList<>();
            try {
                HttpsConnection httpsConnection = new HttpsConnection(context);
                String response = httpsConnection.post(getAbsoluteUrl("/users/relation"), 60, params, prefKeyOauthTokenT);
                relationships = parseRelationshipResponseList(new JSONArray(response));
                apiResponse.setSince_id(httpsConnection.getSince_id());
                apiResponse.setMax_id(httpsConnection.getMax_id());
            } catch (HttpsConnection.HttpsConnectionException e) {
                setError(e.getStatusCode(), e);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            apiResponse.setRelationships(relationships);
        }

        return apiResponse;
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param accountId String Id of the account
     * @return APIResponse
     */
    public APIResponse getStatus(String accountId) {
        return getStatus(accountId, false, false, false, null, null, tootPerPage);
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param accountId String Id of the account
     * @param max_id    String id max
     * @return APIResponse
     */
    public APIResponse getStatus(String accountId, String max_id) {
        return getStatus(accountId, false, false, false, max_id, null, tootPerPage);
    }

    /**
     * Retrieves status with media for the account *synchronously*
     *
     * @param accountId String Id of the account
     * @param max_id    String id max
     * @return APIResponse
     */
    public APIResponse getStatusWithMedia(String accountId, String max_id) {
        return getStatus(accountId, true, false, false, max_id, null, tootPerPage);
    }

    /**
     * Retrieves pinned status(es) *synchronously*
     *
     * @param accountId String Id of the account
     * @param max_id    String id max
     * @return APIResponse
     */
    public APIResponse getPinnedStatuses(String accountId, String max_id) {
        return getStatus(accountId, false, true, false, max_id, null, tootPerPage);
    }

    /**
     * Retrieves replies status(es) *synchronously*
     *
     * @param accountId String Id of the account
     * @param max_id    String id max
     * @return APIResponse
     */
    public APIResponse getAccountTLStatuses(String accountId, String max_id, boolean exclude_replies) {
        return getStatus(accountId, false, false, exclude_replies, max_id, null, tootPerPage);
    }

    /**
     * Retrieves status for the account *synchronously*
     *
     * @param accountId       String Id of the account
     * @param onlyMedia       boolean only with media
     * @param exclude_replies boolean excludes replies
     * @param max_id          String id max
     * @param since_id        String since the id
     * @param limit           int limit  - max value 40
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getStatus(String accountId, boolean onlyMedia, boolean pinned,
                                  boolean exclude_replies, String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 < limit || limit > 40)
                        limit = 40;
            if (onlyMedia)
                params.put("only_media", true);
            if (pinned)
                params.put("pinned", true);
            params.put("includeReplies", !exclude_replies);
            params.put("limit", limit);
            params.put("userId", accountId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/users/notes"), 60, params, null);
            statuses = parseStatuses(context, new JSONArray(response), instance);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Retrieves accounts that reblogged the status *synchronously*
     *
     * @param statusId String Id of the status
     * @param max_id   String id max
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getRebloggedBy(String statusId, String max_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
//            params.put("untilId", max_id);
            params.put("limit", "80");
        params.put("noteId", statusId);
        accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/renotes"), 60, new JSONObject(params), null);
            accounts = parseAccountResponse(new JSONArray(response));
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves accounts that favourited the status *synchronously*
     *
     * @param statusId String Id of the status
     * @param max_id   String id max
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getFavouritedBy(String statusId, String max_id) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilID", max_id);
                params.put("limit", "80");
            params.put("noteId", statusId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/reactions"), 60, params, null);
            accounts = parseAccountResponse(new JSONArray(response));
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves one status *synchronously*
     *
     * @param statusId String Id of the status
     * @return APIResponse
     */
    public APIResponse getStatusbyId(String statusId) {
        statuses = new ArrayList<>();
        HashMap<String, String> param = new HashMap<>();
        param.put("noteId", statusId);
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/show"), 60, new JSONObject(param), null);
            Status status = parseStatuses(context, new JSONObject(response), instance);
            statuses.add(status);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }

    /**
     * Retrieves the context of status with replies *synchronously*
     *
     * @param statusId Id of the status
     * @return List<Status>
     */
    public fr.gouv.etalab.mastodon.client.Entities.Context getStatusContext(String statusId) {
        fr.gouv.etalab.mastodon.client.Entities.Context statusContext = new fr.gouv.etalab.mastodon.client.Entities.Context();
        HashMap<String, String> param = new HashMap<>();
        param.put("noteId", statusId);
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/conversation"), 60, new JSONObject(param), null);
            statusContext = parseContext(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return statusContext;
    }


    /**
     * Retrieves direct timeline for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getDirectTimeline(String max_id) {
        return getDirectTimeline(max_id, null, tootPerPage);
    }

    /**
     * Retrieves conversation timeline for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getConversationTimeline(String max_id) {
        return getConversationTimeline(max_id, null, tootPerPage);
    }

    /**
     * Retrieves direct timeline for the account since an Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getConversationTimelineSinceId(String since_id) {
        return getConversationTimeline(null, since_id, tootPerPage);
    }

    /**
     * Retrieves conversation timeline for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    private APIResponse getConversationTimeline(String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 > limit || limit > 80)
                        limit = 80;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        conversations = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/conversations"), 60, params, null);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            conversations = parseConversations(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setConversations(conversations);
        return apiResponse;
    }

    /**
     * Retrieves direct timeline for the account since an Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getDirectTimelineSinceId(String since_id) {
        return getDirectTimeline(null, since_id, tootPerPage);
    }

    /**
     * Retrieves direct timeline for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    private APIResponse getDirectTimeline(String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 > limit || limit > 80)
                        limit = 80;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/mentions"), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Retrieves home timeline for the account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getHomeTimeline(String max_id) {
        return getHomeTimeline(max_id, null, null, tootPerPage);
    }


    /**
     * Retrieves home timeline for the account since an Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getHomeTimelineSinceId(String since_id) {
        return getHomeTimeline(null, since_id, null, tootPerPage);
    }

    /**
     * Retrieves home timeline for the account from a min Id value *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getHomeTimelineMinId(String min_id) {
        return getHomeTimeline(null, null, min_id, tootPerPage);
    }


    /**
     * Retrieves home timeline for the account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    private APIResponse getHomeTimeline(String max_id, String since_id, String min_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (min_id != null)
//                params.put("untilId", min_id);
                        if (0 > limit || limit > 80)
                            limit = 80;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/timeline"), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
            /*if( response != null) {
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            List<Status> statuses;
                            statuses = API.parseStatuses(context, new JSONArray(response));
                            SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();

                            List<Status> alreadyCached = new TimelineCacheDAO(context, db).getAllStatus(TimelineCacheDAO.HOME_TIMELINE);
                            ArrayList<String> cachedId = new ArrayList<>();
                            if(alreadyCached != null){
                                for(Status status: alreadyCached){
                                    cachedId.add(status.getId());
                                }
                            }
                            for(Status status: statuses){
                                if(!cachedId.contains(status.getId())){
                                    new TimelineCacheDAO(context, db).insertStatus(TimelineCacheDAO.HOME_TIMELINE, status, prefKeyOauthTokenT);
                                }
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                };
                thread.start();
            }*/
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getPeertubeChannel(String instance, String name) {

        List<Account> accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/accounts/%s/video-channels", name), 60, null, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            accounts = parseAccountResponsePeertube(context, instance, jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getPeertubeChannelVideos(String instance, String name) {

        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/video-channels/%s/videos", name), 60, null, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(instance, jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getPeertube(String instance, String max_id) {

        List<Peertube> peertubes = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        if (max_id == null)
            max_id = "0";
        params.put("start", max_id);
        params.put("count", "50");
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get("https://" + instance + "/api/v1/videos", 60, params, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(instance, jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSinglePeertube(String instance, String videoId) {


        Peertube peertube = null;
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/videos/%s", videoId), 60, null, null);
            JSONObject jsonObject = new JSONObject(response);
            peertube = parseSinglePeertube(context, instance, jsonObject);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        List<Peertube> peertubes = new ArrayList<>();
        peertubes.add(peertube);
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves peertube search *synchronously*
     *
     * @param query String search
     * @return APIResponse
     */
    public APIResponse searchPeertube(String instance, String query) {
        HashMap<String, String> params = new HashMap<>();
        params.put("count", "50");
        try {
            params.put("search", URLEncoder.encode(query, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            params.put("search", query);
        }
        List<Peertube> peertubes = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get("https://" + instance + "/api/v1/search/videos", 60, params, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            peertubes = parsePeertube(instance, jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setPeertubes(peertubes);
        return apiResponse;
    }

    /**
     * Retrieves Peertube videos from an instance *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getSinglePeertubeComments(String instance, String videoId) {
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(String.format("https://" + instance + "/api/v1/videos/%s/comment-threads", videoId), 60, null, null);
            JSONObject jsonObject = new JSONObject(response);
            statuses = parseSinglePeertubeComments(context, instance, jsonObject);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }

    /**
     * Retrieves home timeline for the account *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getHowTo() {

        List<HowToVideo> howToVideos = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get("https://peertube.social/api/v1/video-channels/mastalab_channel/videos", 60, null, null);
            JSONArray jsonArray = new JSONObject(response).getJSONArray("data");
            howToVideos = parseHowTos(jsonArray);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setHowToVideos(howToVideos);
        return apiResponse;
    }

    /**
     * Retrieves public timeline for the account *synchronously*
     *
     * @param local  boolean only local timeline
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getPublicTimeline(String instanceName, boolean local, String max_id) {
        return getPublicTimeline(local, instanceName, max_id, null, tootPerPage);
    }

    /**
     * Retrieves public timeline for the account *synchronously*
     *
     * @param local  boolean only local timeline
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getPublicTimeline(boolean local, String max_id) {
        return getPublicTimeline(local, null, max_id, null, tootPerPage);
    }

    /**
     * Retrieves public timeline for the account since an Id value *synchronously*
     *
     * @param local    boolean only local timeline
     * @param since_id String id since
     * @return APIResponse
     */
    public APIResponse getPublicTimelineSinceId(boolean local, String since_id) {
        return getPublicTimeline(local, null, null, since_id, tootPerPage);
    }

    /**
     * Retrieves instance timeline since an Id value *synchronously*
     *
     * @param instanceName String instance name
     * @param since_id     String id since
     * @return APIResponse
     */
    public APIResponse getInstanceTimelineSinceId(String instanceName, String since_id) {
        return getPublicTimeline(true, instanceName, null, since_id, tootPerPage);
    }

    /**
     * Retrieves public timeline for the account *synchronously*
     *
     * @param local    boolean only local timeline
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    private APIResponse getPublicTimeline(boolean local, String instanceName, String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (local)
                params.put("local", true);
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 > limit || limit > 40)
                        limit = 40;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String action;
            if (instanceName == null)
                action = getAbsoluteUrl("/notes/global-timeline");
            else
                action = getAbsoluteUrlRemoteInstance(instanceName);
            String response = httpsConnection.post(action, 60, params, null);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    public APIResponse getCustomArtTimeline(boolean local, String tag, String max_id) {
        return getArtTimeline(local, tag, max_id, null);
    }

    public APIResponse getArtTimeline(boolean local, String max_id) {
        return getArtTimeline(local, null, max_id, null);
    }

    public APIResponse getCustomArtTimelineSinceId(boolean local, String tag, String since_id) {
        return getArtTimeline(local, tag, null, since_id);
    }

    public APIResponse getArtTimelineSinceId(boolean local, String since_id) {
        return getArtTimeline(local, null, null, since_id);
    }

    /**
     * Retrieves art timeline
     *
     * @param local  boolean only local timeline
     * @param max_id String id max
     * @return APIResponse
     */
    private APIResponse getArtTimeline(boolean local, String tag, String max_id, String since_id) {
        if (tag == null)
            tag = "mastoart";
        APIResponse apiResponse = getPublicTimelineTag(tag, local, true, max_id, since_id, tootPerPage);
        APIResponse apiResponseReply = new APIResponse();
        if (apiResponse != null) {
            apiResponseReply.setMax_id(apiResponse.getMax_id());
            apiResponseReply.setSince_id(apiResponse.getSince_id());
            apiResponseReply.setStatuses(new ArrayList<>());
            if (apiResponse.getStatuses() != null && apiResponse.getStatuses().size() > 0) {
                for (Status status : apiResponse.getStatuses()) {
                    if (status.getMedia_attachments() != null) {
                        String statusSerialized = Helper.statusToStringStorage(status);
                        for (Attachment attachment : status.getMedia_attachments()) {
                            Status newStatus = Helper.restoreStatusFromString(statusSerialized);
                            if (newStatus == null)
                                break;
                            newStatus.setArt_attachment(attachment);
                            apiResponseReply.getStatuses().add(newStatus);
                        }
                    }
                }
            }
        }
        return apiResponseReply;
    }

    /**
     * Retrieves public tag timeline *synchronously*
     *
     * @param tag    String
     * @param local  boolean only local timeline
     * @param max_id String id max
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getPublicTimelineTag(String tag, boolean local, String max_id) {
        return getPublicTimelineTag(tag, local, false, max_id, null, tootPerPage);
    }

    /**
     * Retrieves public tag timeline *synchronously*
     *
     * @param tag      String
     * @param local    boolean only local timeline
     * @param since_id String since id
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getPublicTimelineTagSinceId(String tag, boolean local, String since_id) {
        return getPublicTimelineTag(tag, local, false, null, since_id, tootPerPage);
    }

    /**
     * Retrieves public tag timeline *synchronously*
     *
     * @param tag      String
     * @param local    boolean only local timeline
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getPublicTimelineTag(String tag, boolean local, boolean onlymedia, String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (local)
                params.put("local", true);
            if (max_id != null)
                params.put("max_id", max_id);
            if (since_id != null)
                params.put("since_id", since_id);
            if (0 > limit || limit > 40)
                limit = 40;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statuses = new ArrayList<>();
        if (tag == null)
            return null;
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl(String.format("/timelines/tag/%s", tag.trim())), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Retrieves muted users by the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getMuted(String max_id) {
        return getAccounts("/mutes", max_id, null, accountPerPage, null);
    }

    /**
     * Retrieves blocked users by the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getBlocks(String max_id) {
        return getAccounts("/blocks", max_id, null, accountPerPage, null);
    }


    /**
     * Retrieves following for the account specified by targetedId  *synchronously*
     *
     * @param targetedId String targetedId
     * @param max_id     String id max
     * @return APIResponse
     */
    public APIResponse getFollowing(String targetedId, String max_id) {
        return getAccounts("/users/following", max_id, null, accountPerPage, targetedId);
    }

    /**
     * Retrieves followers for the account specified by targetedId  *synchronously*
     *
     * @param targetedId String targetedId
     * @param max_id     String id max
     * @return APIResponse
     */
    public APIResponse getFollowers(String targetedId, String max_id) {
        return getAccounts("/users/followers", max_id, null, accountPerPage, targetedId);
    }

    /**
     * Retrieves blocked users by the authenticated account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getAccounts(String action, String max_id, String since_id, int limit, String target) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 > limit || limit > 40)
                        limit = 40;
            if (target != null)
                params.put("userId", target);
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl(action), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            JSONObject jsonobj = new JSONObject(response);
            accounts = parseAccountResponse(jsonobj.getJSONArray("users"));
            if (accounts != null && accounts.size() == 1) {
                if (accounts.get(0).getAcct() == null) {
                    Throwable error = new Throwable(context.getString(R.string.toast_error));
                    setError(500, error);
                }
            }
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves blocked domains for the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getBlockedDomain(String max_id) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("max_id", max_id);
        params.put("limit", "80");
        domains = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/domain_blocks"), 60, new JSONObject(params), prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            domains = parseDomains(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setDomains(domains);
        return apiResponse;
    }


    /**
     * Delete a blocked domains for the authenticated account *synchronously*
     *
     * @param domain String domain name
     */
    @SuppressWarnings("SameParameterValue")
    public int deleteBlockedDomain(String domain) {

        HashMap<String, String> params = new HashMap<>();
        params.put("domain", domain);
        domains = new ArrayList<>();
        HttpsConnection httpsConnection;
        try {
            httpsConnection = new HttpsConnection(context);
            httpsConnection.delete(getAbsoluteUrl("/domain_blocks"), 60, params, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Retrieves follow requests for the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getFollowRequest(String max_id) {
        return getFollowRequest(max_id, null, accountPerPage);
    }

    /**
     * Retrieves follow requests for the authenticated account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getFollowRequest(String max_id, String since_id, int limit) {

        accounts = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/following/requests/list"), 60, null, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            accounts = parseAccountResponse(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves favourited status for the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getFavourites(String max_id) {
        return getFavourites(max_id, null, tootPerPage);
    }

    /**
     * Retrieves favourited status for the authenticated account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    private APIResponse getFavourites(String max_id, String since_id, int limit) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
//                params.put("untilId", max_id);
                if (since_id != null)
//                params.put("sinceId", since_id);
                    if (0 > limit || limit > 40)
                        limit = 40;
            params.put("limit", limit);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/i/favorites"), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Makes the post action for a status
     *
     * @param statusAction Enum
     * @param targetedId   String id of the targeted Id *can be this of a status or an account*
     * @return in status code - Should be equal to 200 when action is done
     */
    public int postAction(StatusAction statusAction, String targetedId) {
        return postAction(statusAction, targetedId, null, null);
    }

    /**
     * Makes the post action for a status
     *
     * @param targetedId        String id of the targeted Id *can be this of a status or an account*
     * @param muteNotifications - boolean - notifications should be also muted
     * @return in status code - Should be equal to 200 when action is done
     */
    public int muteNotifications(String targetedId, boolean muteNotifications) {

        HashMap<String, String> params = new HashMap<>();
        params.put("notifications", Boolean.toString(muteNotifications));
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            httpsConnection.post(getAbsoluteUrl(String.format("/users/%s/mute", targetedId)), 60, new JSONObject(params), prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }


    /**
     * Makes the post action
     *
     * @param status  Status object related to the status
     * @param comment String comment for the report
     * @return in status code - Should be equal to 200 when action is done
     */
    public int reportAction(Status status, String comment) {
        return postAction(API.StatusAction.REPORT, null, status, comment);
    }

    public int statusAction(Status status) {
        return postAction(StatusAction.CREATESTATUS, null, status, null);
    }

    /**
     * Makes the post action
     *
     * @param statusAction Enum
     * @param targetedId   String id of the targeted Id *can be this of a status or an account*
     * @param status       Status object related to the status
     * @param comment      String comment for the report
     * @return in status code - Should be equal to 200 when action is done
     */
    private int postAction(StatusAction statusAction, String targetedId, Status status, String comment) {

        String action;
        HashMap<String, String> params = new HashMap<>();
        switch (statusAction) {
            case FAVOURITE:
                action = "/notes/favorites/create";
                params.put("noteId", targetedId);
                break;
            case UNFAVOURITE:
                action = "/notes/favorites/delete";
                params.put("noteId", targetedId);
                break;
            case REBLOG:
                action = "/notes/create";
                params.put("renoteId", targetedId);
                break;
            case UNREBLOG:
                action = "/notes/delete";
                params.put("noteId", targetedId);
                break;
            case FOLLOW:
                action = "/following/create";
                params.put("userId", targetedId);
                break;
            case REMOTE_FOLLOW:
                action = "/following/create";
                params.put("userId", targetedId);
                break;
            case UNFOLLOW:
                action = "/following/delete";
                params.put("userId", targetedId);
                break;
            case BLOCK:
                action = String.format("/users/%s/block", targetedId);
                break;
            case BLOCK_DOMAIN:
                action = "/domain_blocks";
                params.put("domain", targetedId);
                break;
            case UNBLOCK:
                action = String.format("/users/%s/unblock", targetedId);
                break;
            case MUTE:
                action = String.format("/users/%s/mute", targetedId);
                break;
            case UNMUTE:
                action = String.format("/users/%s/unmute", targetedId);
                break;
            case PIN:
                action = String.format("/statuses/%s/pin", targetedId);
                break;
            case UNPIN:
                action = String.format("/statuses/%s/unpin", targetedId);
                break;
            case ENDORSE:
                action = String.format("/users/%s/pin", targetedId);
                break;
            case UNENDORSE:
                action = String.format("/users/%s/unpin", targetedId);
                break;
            case SHOW_BOOST:
                params.put("reblogs", "true");
                action = String.format("/users/%s/follow", targetedId);
                break;
            case HIDE_BOOST:
                params.put("reblogs", "false");
                action = String.format("/users/%s/follow", targetedId);
                break;
            case UNSTATUS:
                action = "/notes/delete";
                params.put("noteId", targetedId);
                break;
            case AUTHORIZE:
                action = String.format("/follow_requests/%s/authorize", targetedId);
                break;
            case REJECT:
                action = String.format("/follow_requests/%s/reject", targetedId);
                break;
            case REPORT:
                action = "/reports";
                params.put("account_id", status.getAccount().getId());
                params.put("comment", comment);
                params.put("status_ids[]", status.getId());
                break;
            case CREATESTATUS:
                action = "/notes/create";
                params.put("text", status.getContent());
                if (status.getIn_reply_to_id() != null)
                    params.put("replyId", status.getIn_reply_to_id());
                if (status.getMedia_attachments() != null && status.getMedia_attachments().size() > 0) {
                    StringBuilder parameters = new StringBuilder();
                    for (Attachment attachment : status.getMedia_attachments())
                        parameters.append("media_ids[]=").append(attachment.getId()).append("&");
                    parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(12));
                    params.put("media_ids[]", parameters.toString());
                }
                if (status.isSensitive())
                    params.put("sensitive", Boolean.toString(status.isSensitive()));
                if (status.getSpoiler_text() != null)
                    params.put("cw", status.getSpoiler_text());
                params.put("visibility", status.getVisibility());
                break;
            default:
                return -1;
        }
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            httpsConnection.post(getAbsoluteUrl(action), 60, new JSONObject(params), prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
            Log.d("response", String.valueOf(actionCode));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }


    /**
     * Posts a status
     *
     * @param status Status object related to the status
     * @return APIResponse
     */
    public APIResponse postStatusAction(Status status) {

        HashMap<String, String> params = new HashMap<>();
        params.put("text", status.getContent());
        if (status.getIn_reply_to_id() != null)
            params.put("replyId", status.getIn_reply_to_id());
        if (status.getMedia_attachments() != null && status.getMedia_attachments().size() > 0) {
            StringBuilder parameters = new StringBuilder();
            for (Attachment attachment : status.getMedia_attachments())
                parameters.append("media_ids[]=").append(attachment.getId()).append("&");
            parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(12));
            params.put("media_ids[]", parameters.toString());
        }
        if (status.isSensitive())
            params.put("sensitive", Boolean.toString(status.isSensitive()));
        if (status.getSpoiler_text() != null)
            params.put("cw", status.getSpoiler_text());
        params.put("visibility", status.getVisibility());
        statuses = new ArrayList<>();

        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/notes/create"), 60, new JSONObject(params), prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            Status statusreturned = parseStatuses(context, new JSONObject(response).getJSONObject("createdNote"), instance);
            statuses.add(statusreturned);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Posts a status
     *
     * @param notificationId String, the current notification id, if null all notifications are deleted
     * @return APIResponse
     */
    public APIResponse postNoticationAction(String notificationId) {

        String action;
        HashMap<String, String> params = new HashMap<>();
        if (notificationId == null)
            action = "/notifications/mark_all_as_read";
        else {
            params.put("id", notificationId);
            action = "/notifications/dismiss";
        }
        try {
            new HttpsConnection(context).post(getAbsoluteUrl(action), 60, new JSONObject(params), prefKeyOauthTokenT);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }


    /**
     * Retrieves notifications for the authenticated account since an id*synchronously*
     *
     * @param since_id String since max
     * @return APIResponse
     */
    public APIResponse getNotificationsSince(String since_id, boolean display) {
        return getNotifications(null, since_id, notificationPerPage, display);
    }

    /**
     * Retrieves notifications for the authenticated account since an id*synchronously*
     *
     * @param since_id String since max
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getNotificationsSince(String since_id, int notificationPerPage, boolean display) {
        return getNotifications(null, since_id, notificationPerPage, display);
    }

    /**
     * Retrieves notifications for the authenticated account *synchronously*
     *
     * @param max_id String id max
     * @return APIResponse
     */
    public APIResponse getNotifications(String max_id, boolean display) {
        return getNotifications(max_id, null, notificationPerPage, display);
    }


    /**
     * Retrieves notifications for the authenticated account *synchronously*
     *
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    private APIResponse getNotifications(String max_id, String since_id, int limit, boolean display) {

        JSONObject params = new JSONObject();
        try {
            if (max_id != null)
                params.put("max_id", max_id);
            if (since_id != null)
                params.put("since_id", since_id);
            if (0 > limit || limit > 30)
                limit = 30;
            params.put("limit", limit);

            final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            boolean notif_follow, notif_add, notif_mention, notif_share;
            if (display) {
                notif_follow = sharedpreferences.getBoolean(Helper.SET_NOTIF_FOLLOW_FILTER, true);
                notif_add = sharedpreferences.getBoolean(Helper.SET_NOTIF_ADD_FILTER, true);
                notif_mention = sharedpreferences.getBoolean(Helper.SET_NOTIF_MENTION_FILTER, true);
                notif_share = sharedpreferences.getBoolean(Helper.SET_NOTIF_SHARE_FILTER, true);
            } else {
                notif_follow = sharedpreferences.getBoolean(Helper.SET_NOTIF_FOLLOW, true);
                notif_add = sharedpreferences.getBoolean(Helper.SET_NOTIF_ADD, true);
                notif_mention = sharedpreferences.getBoolean(Helper.SET_NOTIF_MENTION, true);
                notif_share = sharedpreferences.getBoolean(Helper.SET_NOTIF_SHARE, true);
            }
            StringBuilder parameters = new StringBuilder();

            if (!notif_follow)
                parameters.append("exclude_types[]=").append("follow").append("&");
            if (!notif_add)
                parameters.append("exclude_types[]=").append("favourite").append("&");
            if (!notif_share)
                parameters.append("exclude_types[]=").append("reblog").append("&");
            if (!notif_mention)
                parameters.append("exclude_types[]=").append("mention").append("&");
            if (parameters.length() > 0) {
                parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(16));
                params.put("exclude_types[]", parameters.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }


        List<Notification> notifications = new ArrayList<>();

        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/i/notifications"), 60, params, prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            notifications = parseNotificationResponse(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setNotifications(notifications);
        return apiResponse;
    }


    /**
     * Changes media description
     *
     * @param mediaId     String
     * @param description String
     * @return Attachment
     */
    public Attachment updateDescription(String mediaId, String description) {

        HashMap<String, String> params = new HashMap<>();
        try {
            params.put("description", URLEncoder.encode(description, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            params.put("description", description);
        }
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.put(getAbsoluteUrl(String.format("/media/%s", mediaId)), 240, params, prefKeyOauthTokenT);
            attachment = parseAttachmentResponse(new JSONObject(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return attachment;
    }

    /**
     * Retrieves Accounts and feeds when searching *synchronously*
     *
     * @param query String search
     * @return List<Account>
     */
    public Results search(String query) {
        JSONObject responseholder = new JSONObject();
        HashMap<String, String> params = new HashMap<>();
        try {
            params.put("query", URLEncoder.encode(query, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            params.put("query", query);
        }

        {
            try {
                HttpsConnection httpsConnection = new HttpsConnection(context);
                String response = httpsConnection.post(getAbsoluteUrl("/notes/search"), 60, new JSONObject(params), null);
                responseholder.put("statuses", new JSONArray(response));
            } catch (HttpsConnection.HttpsConnectionException e) {
                setError(e.getStatusCode(), e);
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        {
            try {
                HttpsConnection httpsConnection = new HttpsConnection(context);
                String response = httpsConnection.post(getAbsoluteUrl("/users/search"), 60, new JSONObject(params), null);
                responseholder.put("users", new JSONArray(response));
            } catch (HttpsConnection.HttpsConnectionException e) {
                setError(e.getStatusCode(), e);
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        {
            try {
                HttpsConnection httpsConnection = new HttpsConnection(context);
                String response = httpsConnection.post(getAbsoluteUrl("/hashtags/search"), 60, new JSONObject(params), null);
                responseholder.put("hashtags", new JSONArray(response));
            } catch (HttpsConnection.HttpsConnectionException e) {
                setError(e.getStatusCode(), e);
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        results = parseResultsResponse(responseholder);
        return results;
    }


    /**
     * Retrieves Accounts when searching (ie: via @...) *synchronously*
     * Not limited to following
     *
     * @param query String search
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse searchAccounts(String query, int count) {
        return searchAccounts(query, count, false);
    }

    /**
     * Retrieves Accounts when searching (ie: via @...) *synchronously*
     *
     * @param query     String search
     * @param count     int limit
     * @param following boolean following only
     * @return APIResponse
     */
    public APIResponse searchAccounts(String query, int count, boolean following) {

        JSONObject params = new JSONObject();
        try {
            params.put("q", query);
            if (count < 5)
                count = 5;
            if (count > 40)
                count = 40;
            if (following)
                params.put("following", Boolean.toString(true));
            params.put("limit", count);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/users/search"), 60, params, null);
            accounts = parseAccountResponse(new JSONArray(response));
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Retrieves Accounts when searching (ie: via @...) *synchronously*
     *
     * @return APIResponse
     */
    public APIResponse getCustomEmoji() {
        List<Emojis> emojis = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl("/meta"), 60, null, null);
            JSONObject meta = new JSONObject(response);
            emojis = parseEmojis(meta.getJSONArray("emojis"));
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());

        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setEmojis(emojis);
        return apiResponse;
    }


    /**
     * Get filters for the user
     *
     * @return APIResponse
     */
    public APIResponse getFilters() {

        List<Filters> filters = null;
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/filters"), 60, null, prefKeyOauthTokenT);
            filters = parseFilters(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setFilters(filters);
        return apiResponse;
    }

    /**
     * Get a Filter by its id
     *
     * @return APIResponse
     */
    @SuppressWarnings("unused")
    public APIResponse getFilters(String filterId) {

        List<fr.gouv.etalab.mastodon.client.Entities.Filters> filters = new ArrayList<>();
        fr.gouv.etalab.mastodon.client.Entities.Filters filter;
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl(String.format("/filters/%s", filterId)), 60, null, prefKeyOauthTokenT);
            filter = parseFilter(new JSONObject(response));
            filters.add(filter);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setFilters(filters);
        return apiResponse;
    }


    /**
     * Create a filter
     *
     * @param filter Filter
     * @return APIResponse
     */
    public APIResponse addFilters(Filters filter) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phrase", filter.getPhrase());
        StringBuilder parameters = new StringBuilder();
        for (String context : filter.getContext())
            parameters.append("context[]=").append(context).append("&");
        if (parameters.length() > 0) {
            parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(10));
            params.put("context[]", parameters.toString());
        }
        params.put("irreversible", String.valueOf(filter.isIrreversible()));
        params.put("whole_word", String.valueOf(filter.isWhole_word()));
        params.put("expires_in", String.valueOf(filter.getExpires_in()));
        ArrayList<Filters> filters = new ArrayList<>();
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/filters"), 60, new JSONObject(params), prefKeyOauthTokenT);
            Filters resfilter = parseFilter(new JSONObject(response));
            filters.add(resfilter);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setFilters(filters);
        return apiResponse;
    }

    /**
     * Delete a filter
     *
     * @param filter Filter
     * @return APIResponse
     */
    public int deleteFilters(Filters filter) {

        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            httpsConnection.delete(getAbsoluteUrl(String.format("/filters/%s", filter.getId())), 60, null, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Delete a filter
     *
     * @param filter Filter
     * @return APIResponse
     */
    public APIResponse updateFilters(Filters filter) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phrase", filter.getPhrase());
        StringBuilder parameters = new StringBuilder();
        for (String context : filter.getContext())
            parameters.append("context[]=").append(context).append("&");
        if (parameters.length() > 0) {
            parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(10));
            params.put("context[]", parameters.toString());
        }
        params.put("irreversible", String.valueOf(filter.isIrreversible()));
        params.put("whole_word", String.valueOf(filter.isWhole_word()));
        params.put("expires_in", String.valueOf(filter.getExpires_in()));
        ArrayList<Filters> filters = new ArrayList<>();
        try {
            String response = new HttpsConnection(context).put(getAbsoluteUrl(String.format("/filters/%s", filter.getId())), 60, params, prefKeyOauthTokenT);
            Filters resfilter = parseFilter(new JSONObject(response));
            filters.add(resfilter);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setFilters(filters);
        return apiResponse;
    }

    /**
     * Get lists for the user
     *
     * @return APIResponse
     */
    public APIResponse getLists() {

        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/users/lists/list"), 60, null, prefKeyOauthTokenT);
            lists = parseLists(new JSONArray(response));
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setLists(lists);
        return apiResponse;
    }

    /**
     * Get lists for a user by its id
     *
     * @return APIResponse
     */
    @SuppressWarnings("unused")
    public APIResponse getLists(String userId) {

        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        fr.gouv.etalab.mastodon.client.Entities.List list;
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl(String.format("/users/%s/lists", userId)), 60, null, prefKeyOauthTokenT);
            list = parseList(new JSONObject(response));
            lists.add(list);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setLists(lists);
        return apiResponse;
    }

    /**
     * Retrieves list timeline  *synchronously*
     *
     * @param list_id  String id of the list
     * @param max_id   String id max
     * @param since_id String since the id
     * @param limit    int limit  - max value 40
     * @return APIResponse
     */
    public APIResponse getListTimeline(String list_id, String max_id, String since_id, int limit) {

        HashMap<String, String> params = new HashMap<>();
        if (max_id != null)
            params.put("max_id", max_id);
        if (since_id != null)
            params.put("since_id", since_id);
        if (0 > limit || limit > 80)
            limit = 80;
        params.put("limit", String.valueOf(limit));
        statuses = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl(String.format("/timelines/user-list-timeline/%s", list_id)), 60, new JSONObject(params), prefKeyOauthTokenT);
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
            statuses = parseStatuses(context, new JSONArray(response), instance);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return apiResponse;
    }


    /**
     * Get accounts in a list for a user
     *
     * @param listId String, id of the list
     * @param limit  int, limit of results
     * @return APIResponse
     */
    @SuppressWarnings("SameParameterValue")
    public APIResponse getAccountsInList(String listId, int limit) {

        HashMap<String, String> params = new HashMap<>();
        if (limit < 0)
            limit = 0;
        if (limit > 50)
            limit = 50;
        params.put("limit", String.valueOf(limit));
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.post(getAbsoluteUrl(String.format("/lists/%s/accounts", listId)), 60, new JSONObject(params), prefKeyOauthTokenT);
            accounts = parseAccountResponse(new JSONArray(response));
            apiResponse.setSince_id(httpsConnection.getSince_id());
            apiResponse.setMax_id(httpsConnection.getMax_id());
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setAccounts(accounts);
        return apiResponse;
    }


    /**
     * Get a list
     *
     * @param id String, id of the list
     * @return APIResponse
     */
    @SuppressWarnings("unused")
    public APIResponse getList(String id) {

        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        fr.gouv.etalab.mastodon.client.Entities.List list;
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl(String.format("/users/lists/show/%s", id)), 60, null, prefKeyOauthTokenT);
            list = parseList(new JSONObject(response));
            lists.add(list);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setLists(lists);
        return apiResponse;
    }


    /**
     * Add an account in a list
     *
     * @param id          String, id of the list
     * @param account_ids String, account to add
     * @return APIResponse
     */
    //TODO: it is unclear what is returned here
    //TODO: improves doc https://github.com/tootsuite/documentation/blob/4bb149c73f40fa58fd7265a336703dd2d83efb1c/Using-the-API/API.md#addingremoving-accounts-tofrom-a-list
    public APIResponse addAccountToList(String id, String[] account_ids) {

        HashMap<String, String> params = new HashMap<>();
        StringBuilder parameters = new StringBuilder();
        for (String val : account_ids)
            parameters.append("account_ids[]=").append(val).append("&");
        if (parameters.length() > 0) {
            parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(14));
            params.put("account_ids[]", parameters.toString());
        }
        try {
            new HttpsConnection(context).post(getAbsoluteUrl(String.format("/lists/%s/accounts", id)), 60, new JSONObject(params), prefKeyOauthTokenT);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return apiResponse;
    }


    /**
     * Delete an account from a list
     *
     * @param id String, the id of the list
     * @return APIResponse
     */
    public int deleteAccountFromList(String id, String[] account_ids) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            StringBuilder parameters = new StringBuilder();
            HashMap<String, String> params = new HashMap<>();
            for (String val : account_ids)
                parameters.append("account_ids[]=").append(val).append("&");
            if (parameters.length() > 0) {
                parameters = new StringBuilder(parameters.substring(0, parameters.length() - 1).substring(14));
                params.put("account_ids[]", parameters.toString());
            }
            httpsConnection.delete(getAbsoluteUrl(String.format("/lists/%s/accounts", id)), 60, params, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return actionCode;
    }

    /**
     * Posts a list
     *
     * @param title String, the title of the list
     * @return APIResponse
     */
    public APIResponse createList(String title) {

        HashMap<String, String> params = new HashMap<>();
        params.put("title", title);
        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        fr.gouv.etalab.mastodon.client.Entities.List list;
        try {
            String response = new HttpsConnection(context).post(getAbsoluteUrl("/lists"), 60, new JSONObject(params), prefKeyOauthTokenT);
            list = parseList(new JSONObject(response));
            lists.add(list);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setLists(lists);
        return apiResponse;
    }


    /**
     * Update a list by its id
     *
     * @param id    String, the id of the list
     * @param title String, the title of the list
     * @return APIResponse
     */
    public APIResponse updateList(String id, String title) {

        HashMap<String, String> params = new HashMap<>();
        params.put("title", title);
        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        fr.gouv.etalab.mastodon.client.Entities.List list;
        try {
            String response = new HttpsConnection(context).put(getAbsoluteUrl(String.format("/lists/%s", id)), 60, params, prefKeyOauthTokenT);
            list = parseList(new JSONObject(response));
            lists.add(list);
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setLists(lists);
        return apiResponse;
    }


    /**
     * Delete a list by its id
     *
     * @param id String, the id of the list
     * @return APIResponse
     */
    public int deleteList(String id) {
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            httpsConnection.delete(getAbsoluteUrl(String.format("/lists/%s", id)), 60, null, prefKeyOauthTokenT);
            actionCode = httpsConnection.getActionCode();
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (Exception e) {
            setDefaultError(e);
        }
        return actionCode;
    }


    /**
     * Retrieves list from Communitywiki *synchronously*
     *
     * @return APIResponse
     */
    public ArrayList<String> getCommunitywikiList() {
        ArrayList<String> list = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(getAbsoluteUrlCommunitywiki("/list"), 60, null, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONArray(response);
            int len = jsonArray.length();
            for (int i = 0; i < len; i++) {
                list.add(jsonArray.get(i).toString());
            }
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return list;
    }

    /**
     * Retrieves list from Communitywiki *synchronously*
     *
     * @return APIResponse
     */
    public ArrayList<String> getCommunitywikiList(String name) {
        ArrayList<String> list = new ArrayList<>();
        try {
            HttpsConnection httpsConnection = new HttpsConnection(context);
            String response = httpsConnection.get(getAbsoluteUrlCommunitywiki(String.format("/list/%s", name)), 60, null, prefKeyOauthTokenT);

            JSONArray jsonArray = new JSONArray(response);
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    list.add(jsonArray.getJSONObject(i).getString("acct"));
                } catch (JSONException ignored) {
                }
            }
        } catch (HttpsConnection.HttpsConnectionException e) {
            setError(e.getStatusCode(), e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        apiResponse.setStatuses(statuses);
        return list;
    }


    /**
     * Parse json response an unique account
     *
     * @param resobj JSONObject
     * @return Account
     */
    private Results parseResultsResponse(JSONObject resobj) {

        Results results = new Results();
        try {
            results.setAccounts(parseAccountResponse(resobj.getJSONArray("users")));
            results.setStatuses(parseStatuses(context, resobj.getJSONArray("statuses"), instance));
            results.setHashtags(parseTags(resobj.getJSONArray("hashtags")));
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultError(e);
        }
        return results;
    }

    /**
     * Parse json response an unique Car
     *
     * @param resobj JSONObject
     * @return Card
     */
    private static Card parseCardResponse(JSONObject resobj) {

        Card card = new Card();
        try {
            card.setUrl(resobj.get("url").toString());
            card.setTitle(resobj.get("title").toString());
            card.setDescription(resobj.get("description").toString());
            card.setImage(resobj.get("image").toString());
            card.setHtml(resobj.get("html").toString());
            card.setType(resobj.get("type").toString());
            try {
                card.setAuthor_name(resobj.get("author_name").toString());
            } catch (Exception e) {
                e.printStackTrace();
                card.setAuthor_name(null);
            }
            try {
                card.setAuthor_url(resobj.get("author_url").toString());
            } catch (Exception e) {
                e.printStackTrace();
                card.setAuthor_url(null);
            }
            try {
                card.setEmbed_url(resobj.get("embed_url").toString());
            } catch (Exception e) {
                e.printStackTrace();
                card.setEmbed_url(null);
            }
            try {
                card.setProvider_name(resobj.get("provider_name").toString());
            } catch (Exception e) {
                e.printStackTrace();
                card.setProvider_name(null);
            }
            try {
                card.setProvider_url(resobj.get("provider_url").toString());
            } catch (Exception e) {
                e.printStackTrace();
                card.setProvider_url(null);
            }
            try {
                card.setHeight(Integer.parseInt(resobj.get("height").toString()));
            } catch (Exception e) {
                e.printStackTrace();
                card.setHeight(0);
            }
            try {
                card.setWidth(Integer.parseInt(resobj.get("width").toString()));
            } catch (Exception e) {
                e.printStackTrace();
                card.setWidth(0);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            card = null;
        }
        return card;
    }

    /**
     * Parse json response an unique instance social result
     *
     * @param resobj JSONObject
     * @return InstanceSocial
     */
    public static InstanceSocial parseInstanceSocialResponse(Context context, JSONObject resobj) {

        InstanceSocial instanceSocial = new InstanceSocial();
        try {


            instanceSocial.setUptime(Float.parseFloat(resobj.get("uptime").toString()));
            instanceSocial.setUp(Boolean.parseBoolean(resobj.get("up").toString()));

            instanceSocial.setConnections(Long.parseLong(resobj.get("connections").toString()));
            instanceSocial.setDead(Boolean.parseBoolean(resobj.get("dead").toString()));


            instanceSocial.setId(resobj.get("id").toString());

            instanceSocial.setInfo(resobj.get("info").toString());
            instanceSocial.setVersion(resobj.get("version").toString());
            instanceSocial.setName(resobj.get("name").toString());
            instanceSocial.setObs_rank(resobj.get("obs_rank").toString());
            instanceSocial.setThumbnail(resobj.get("thumbnail").toString());
            instanceSocial.setIpv6(Boolean.parseBoolean(resobj.get("ipv6").toString()));
            instanceSocial.setObs_score(Integer.parseInt(resobj.get("obs_score").toString()));
            instanceSocial.setOpen_registrations(Boolean.parseBoolean(resobj.get("open_registrations").toString()));

            instanceSocial.setUsers(Long.parseLong(resobj.get("users").toString()));
            instanceSocial.setStatuses(Long.parseLong(resobj.get("statuses").toString()));

            instanceSocial.setHttps_rank(resobj.get("https_rank").toString());
            instanceSocial.setHttps_score(Integer.parseInt(resobj.get("https_score").toString()));
            instanceSocial.setAdded_at(Helper.mstStringToDate(context, resobj.get("added_at").toString()));
            instanceSocial.setChecked_at(Helper.mstStringToDate(context, resobj.get("checked_at").toString()));
            instanceSocial.setUpdated_at(Helper.mstStringToDate(context, resobj.get("updated_at").toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return instanceSocial;
    }


    /**
     * Parse Domains
     *
     * @param jsonArray JSONArray
     * @return List<String> of domains
     */
    private List<String> parseDomains(JSONArray jsonArray) {
        List<String> list_tmp = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                list_tmp.add(jsonArray.getString(i));
            } catch (JSONException ignored) {
            }
        }
        return list_tmp;
    }


    /**
     * Parse Tags
     *
     * @param jsonArray JSONArray
     * @return List<String> of tags
     */
    private List<String> parseTags(JSONArray jsonArray) {
        List<String> list_tmp = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                list_tmp.add(jsonArray.getString(i));
            } catch (JSONException ignored) {
            }
        }
        return list_tmp;
    }

    /**
     * Parse json response for several howto
     *
     * @param jsonArray JSONArray
     * @return List<HowToVideo>
     */
    private List<HowToVideo> parseHowTos(JSONArray jsonArray) {

        List<HowToVideo> howToVideos = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {

                JSONObject resobj = jsonArray.getJSONObject(i);
                HowToVideo howToVideo = parseHowTo(context, resobj);
                i++;
                howToVideos.add(howToVideo);
            }

        } catch (JSONException e) {
            setDefaultError(e);
        }
        return howToVideos;
    }

    /**
     * Parse json response for several howto
     *
     * @param jsonArray JSONArray
     * @return List<Peertube>
     */
    private List<Peertube> parsePeertube(String instance, JSONArray jsonArray) {

        List<Peertube> peertubes = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Peertube peertube = parsePeertube(context, instance, resobj);
                i++;
                peertubes.add(peertube);
            }

        } catch (JSONException e) {
            setDefaultError(e);
        }
        return peertubes;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    public static Peertube parsePeertube(Context context, String instance, JSONObject resobj) {
        Peertube peertube = new Peertube();
        try {
            peertube.setId(resobj.get("id").toString());
            peertube.setCache(resobj);
            peertube.setUuid(resobj.get("uuid").toString());
            peertube.setName(resobj.get("name").toString());
            peertube.setDescription(resobj.get("description").toString());
            peertube.setEmbedPath(resobj.get("embedPath").toString());
            peertube.setPreviewPath(resobj.get("previewPath").toString());
            peertube.setThumbnailPath(resobj.get("thumbnailPath").toString());
            peertube.setAccount(parseAccountResponsePeertube(context, instance, resobj.getJSONObject("account")));
            peertube.setInstance(instance);
            peertube.setView(Integer.parseInt(resobj.get("views").toString()));
            peertube.setLike(Integer.parseInt(resobj.get("likes").toString()));
            peertube.setDislike(Integer.parseInt(resobj.get("dislikes").toString()));
            peertube.setDuration(Integer.parseInt(resobj.get("duration").toString()));
            try {
                peertube.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return peertube;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    private static Peertube parseSinglePeertube(Context context, String instance, JSONObject resobj) {
        Peertube peertube = new Peertube();
        try {
            peertube.setId(resobj.get("id").toString());
            peertube.setUuid(resobj.get("uuid").toString());
            peertube.setName(resobj.get("name").toString());
            peertube.setCache(resobj);
            peertube.setInstance(instance);
            peertube.setHost(resobj.getJSONObject("account").get("host").toString());
            peertube.setDescription(resobj.get("description").toString());
            peertube.setEmbedPath(resobj.get("embedPath").toString());
            peertube.setPreviewPath(resobj.get("previewPath").toString());
            peertube.setThumbnailPath(resobj.get("thumbnailPath").toString());
            peertube.setView(Integer.parseInt(resobj.get("views").toString()));
            peertube.setLike(Integer.parseInt(resobj.get("likes").toString()));
            peertube.setCommentsEnabled(Boolean.parseBoolean(resobj.get("commentsEnabled").toString()));
            peertube.setDislike(Integer.parseInt(resobj.get("dislikes").toString()));
            peertube.setDuration(Integer.parseInt(resobj.get("duration").toString()));
            peertube.setAccount(parseAccountResponsePeertube(context, instance, resobj.getJSONObject("account")));
            try {
                peertube.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            JSONArray files = resobj.getJSONArray("files");
            ArrayList<String> resolutions = new ArrayList<>();
            for (int j = 0; j < files.length(); j++) {
                JSONObject attObj = files.getJSONObject(j);
                resolutions.add(attObj.getJSONObject("resolution").get("id").toString());
            }
            peertube.setResolution(resolutions);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return peertube;
    }

    /**
     * Parse json response for peertube comments
     *
     * @param resobj JSONObject
     * @return Peertube
     */
    private static List<Status> parseSinglePeertubeComments(Context context, String instance, JSONObject resobj) {
        List<Status> statuses = new ArrayList<>();
        try {
            JSONArray jsonArray = resobj.getJSONArray("data");
            int i = 0;
            while (i < jsonArray.length()) {
                Status status = new Status();
                JSONObject comment = jsonArray.getJSONObject(i);
                status.setId(comment.get("id").toString());
                status.setUri(comment.get("url").toString());
                status.setUrl(comment.get("url").toString());
                status.setSensitive(false);
                status.setSpoiler_text("");
                status.setContent(comment.get("text").toString());
                status.setIn_reply_to_id(comment.get("inReplyToCommentId").toString());
                status.setAccount(parseAccountResponsePeertube(context, instance, comment.getJSONObject("account")));
                status.setCreated_at(Helper.mstStringToDate(context, comment.get("createdAt").toString()));
                status.setMentions(new ArrayList<>());
                status.setEmojis(new ArrayList<>());
                status.setMedia_attachments(new ArrayList<>());
                status.setVisibility("public");
                i++;
                statuses.add(status);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return statuses;
    }

    /**
     * Parse json response for unique how to
     *
     * @param resobj JSONObject
     * @return HowToVideo
     */
    private static HowToVideo parseHowTo(Context context, JSONObject resobj) {
        HowToVideo howToVideo = new HowToVideo();
        try {
            howToVideo.setId(resobj.get("id").toString());
            howToVideo.setUuid(resobj.get("uuid").toString());
            howToVideo.setName(resobj.get("name").toString());
            howToVideo.setDescription(resobj.get("description").toString());
            howToVideo.setEmbedPath(resobj.get("embedPath").toString());
            howToVideo.setPreviewPath(resobj.get("previewPath").toString());
            howToVideo.setThumbnailPath(resobj.get("thumbnailPath").toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return howToVideo;
    }

    /**
     * Parse json response for several conversations
     *
     * @param jsonArray JSONArray
     * @return List<Conversation>
     */
    private List<Conversation> parseConversations(JSONArray jsonArray) {

        List<Conversation> conversations = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {

                JSONObject resobj = jsonArray.getJSONObject(i);
                Conversation conversation = parseConversation(context, resobj);
                i++;
                conversations.add(conversation);
            }

        } catch (JSONException e) {
            setDefaultError(e);
        }
        return conversations;
    }

    /**
     * Parse json response for unique conversation
     *
     * @param resobj JSONObject
     * @return Conversation
     */
    @SuppressWarnings("InfiniteRecursion")
    private Conversation parseConversation(Context context, JSONObject resobj) {
        Conversation conversation = new Conversation();
        try {
            conversation.setId(resobj.get("id").toString());
            conversation.setUnread(Boolean.parseBoolean(resobj.get("unread").toString()));
            conversation.setAccounts(parseAccountResponse(resobj.getJSONArray("accounts")));
            conversation.setLast_status(parseStatuses(context, resobj.getJSONObject("last_status"), instance));
        } catch (JSONException ignored) {
        }
        return conversation;
    }

    /**
     * Parse json response for several status
     *
     * @param jsonArray JSONArray
     * @return List<Status>
     */
    public static List<Status> parseStatuses(Context context, JSONArray jsonArray, String instance) {

        List<Status> statuses = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {

                JSONObject resobj = jsonArray.getJSONObject(i);
                Status status = parseStatuses(context, resobj, instance);
                i++;
                statuses.add(status);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return statuses;
    }

    /**
     * Parse json response for unique status
     *
     * @param resobj JSONObject
     * @return Status
     */
    @SuppressWarnings("InfiniteRecursion")
    public static Status parseStatuses(Context context, JSONObject resobj, String instance) {
        Status status = new Status();
        try {
            status.setId(resobj.get("id").toString());
            status.setUri(Helper.instanceWithProtocol(instance) + "/notes/" + resobj.get("id").toString());
            status.setUrl(Helper.instanceWithProtocol(instance) + "/notes/" + resobj.get("id").toString());
            status.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            try {
                status.setIn_reply_to_id(resobj.getJSONObject("reply").get("replyId").toString());
                status.setIn_reply_to_account_id(resobj.getJSONObject("reply").getJSONObject("user").get("id").toString());
            } catch (JSONException e) {
                status.setIn_reply_to_id(null);
                status.setIn_reply_to_account_id(null);
            }
//            status.setSensitive(Boolean.parseBoolean(resobj.get("sensitive").toString()));
            status.setSensitive(false);
            if (resobj.getString("cw") != null && resobj.getString("cw") != "null") {
                status.setSpoiler_text(resobj.getString("cw"));
            }
            try {
                status.setVisibility(resobj.get("visibility").toString());
            } catch (Exception e) {
                status.setVisibility("public");
            }
//            status.setLanguage(resobj.get("language").toString());
            status.setUri(instance + "/notes" + resobj.get("id").toString());
            //TODO: replace by the value
            status.setApplication(new Application());

            //Retrieves attachments
            JSONArray arrayAttachement = resobj.getJSONArray("files");
            ArrayList<Attachment> attachments = new ArrayList<>();
            if (arrayAttachement != null) {
                for (int j = 0; j < arrayAttachement.length(); j++) {
                    JSONObject attObj = arrayAttachement.getJSONObject(j);
                    Attachment attachment = new Attachment();
                    attachment.setId(attObj.get("id").toString());
                    attachment.setPreview_url(attObj.get("thumbnailUrl").toString());
                    attachment.setRemote_url(attObj.get("url").toString());
                    attachment.setType(attObj.get("type").toString());
                    attachment.setText_url(null);
                    attachment.setUrl(attObj.get("url").toString());
                    try {
                        attachment.setDescription(attObj.get("description").toString());
                    } catch (JSONException ignore) {
                    }
                    attachments.add(attachment);
                }
                status.setMedia_attachments(attachments);
            }
            try {
                status.setCard(parseCardResponse(resobj.getJSONObject("card")));
            } catch (Exception e) {
                status.setCard(null);
            }

            //Retrieves mentions
            // TODO: fix mentions
            List<Mention> mentions = new ArrayList<>();
            try {
                JSONArray arrayMention = resobj.getJSONArray("mentionedRemoteUsers");
                JSONArray arrayID = resobj.getJSONArray("mentions");
                if (arrayMention != null) {
                    for (int j = 0; j < arrayMention.length(); j++) {
                        JSONObject menObj = arrayMention.getJSONObject(j);
                        Mention mention = new Mention();
                        mention.setId(arrayID.get(j).toString());
                        mention.setUrl(menObj.get("uri").toString());
                        if (menObj.getString("host") != null) {
                            mention.setAcct(menObj.getString("username") + "@" + menObj.getString("host"));
                        } else {
                            mention.setAcct(menObj.getString("username"));
                        }
                        mention.setUsername(menObj.get("username").toString());
                        mentions.add(mention);
                    }
                }
            } catch (JSONException ignored) {
            }
            status.setMentions(mentions);
            //Retrieves tags
            List<Tag> tags = new ArrayList<>();
//            JSONArray arrayTag = resobj.getJSONArray("tags");
//            if( arrayTag != null){
//                for(int j = 0 ; j < arrayTag.length() ; j++){
//                    JSONObject tagObj = arrayTag.getJSONObject(j);
//                    Tag tag = new Tag();
//                    tag.setName(tagObj.get("name").toString());
//                    tag.setUrl(tagObj.get("url").toString());
//                    tags.add(tag);
//                }
//            }
            status.setTags(tags);

            //Retrieves emjis
            List<Emojis> emojiList = new ArrayList<>();
            try {
                JSONArray emojisTag = resobj.getJSONArray("emojis");
                if (emojisTag != null) {
                    for (int j = 0; j < emojisTag.length(); j++) {
                        JSONObject emojisObj = emojisTag.getJSONObject(j);
                        Emojis emojis = parseEmojis(emojisObj);
                        emojiList.add(emojis);
                    }
                }
                status.setEmojis(emojiList);
            } catch (Exception e) {
                status.setEmojis(new ArrayList<>());
            }

            //Retrieve Application
            Application application = new Application();
            try {
                if (resobj.getJSONObject("application") != null) {
                    application.setName(resobj.getJSONObject("application").getString("name"));
                    application.setWebsite(resobj.getJSONObject("application").getString("website"));
                }
            } catch (Exception e) {
                application = new Application();
            }
            status.setApplication(application);


            status.setAccount(parseAccountResponse(context, resobj.getJSONObject("user"), instance));
            status.setContent(resobj.get("text").toString());
            try {
                status.setFavourites_count(resobj.getJSONArray("reactionCounts").length());
            } catch (JSONException e) {
                status.setFavourites_count(0);
            }
            try {
                status.setReblogs_count(resobj.getInt("renoteCount"));
            } catch (JSONException e) {
                status.setReblogs_count(0);
            }
            try {
                status.setReplies_count(Integer.valueOf(resobj.get("repliesCount").toString()));
            } catch (Exception e) {
                status.setReplies_count(-1);
            }
            try {
                status.setReblogged(Boolean.valueOf(resobj.get("reblogged").toString()));
            } catch (Exception e) {
                status.setReblogged(false);
            }
            // TODO: support reactions
            if (resobj.isNull("myReaction")) {
                status.setFavourited(false);
            } else {
                status.setFavourited(true);
            }
            try {
                status.setMuted(Boolean.valueOf(resobj.get("muted").toString()));
            } catch (Exception e) {
                status.setMuted(false);
            }
            try {
                status.setPinned(Boolean.valueOf(resobj.get("pinned").toString()));
            } catch (JSONException e) {
                status.setPinned(false);
            }
            try {
                status.setReblog(parseStatuses(context, resobj.getJSONObject("renote"), instance));
            } catch (Exception ignored) {
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * Parse json response an unique instance
     *
     * @param resobj JSONObject
     * @return Instance
     */
    private Instance parseInstance(JSONObject resobj) {

        Instance instance = new Instance();
        try {
            instance.setUri(resobj.get("uri").toString());
            instance.setTitle(resobj.get("name").toString());
            instance.setDescription(resobj.get("description").toString());
            instance.setEmail(resobj.getJSONObject("maintainer").get("email").toString());
            instance.setVersion(resobj.get("version").toString());
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return instance;
    }


    /**
     * Parse emojis
     *
     * @param jsonArray JSONArray
     * @return List<Emojis> of emojis
     */
    private List<Emojis> parseEmojis(JSONArray jsonArray) {
        List<Emojis> emojis = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Emojis emojis1 = parseEmojis(resobj);
                emojis.add(emojis1);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return emojis;
    }


    /**
     * Parse json response for emoji
     *
     * @param resobj JSONObject
     * @return Emojis
     */
    private static Emojis parseEmojis(JSONObject resobj) {
        Emojis emojis = new Emojis();
        try {
            emojis.setShortcode(resobj.get("name").toString());
            emojis.setStatic_url(resobj.get("url").toString());
            emojis.setUrl(resobj.get("url").toString());
        } catch (Exception ignored) {
        }
        return emojis;
    }


    /**
     * Parse Filters
     *
     * @param jsonArray JSONArray
     * @return List<Filters> of filters
     */
    private List<Filters> parseFilters(JSONArray jsonArray) {
        List<Filters> filters = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Filters filter = parseFilter(resobj);
                if (filter != null)
                    filters.add(filter);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return filters;
    }

    /**
     * Parse json response for filter
     *
     * @param resobj JSONObject
     * @return Filter
     */
    private Filters parseFilter(JSONObject resobj) {
        Filters filter = new fr.gouv.etalab.mastodon.client.Entities.Filters();
        try {

            filter.setId(resobj.get("id").toString());
            if (resobj.get("phrase").toString() == null)
                return null;
            filter.setPhrase(resobj.get("phrase").toString());
            if (resobj.get("expires_at") != null && !resobj.get("expires_at").toString().equals("null"))
                filter.setSetExpires_at(Helper.mstStringToDate(context, resobj.get("expires_at").toString()));
            filter.setWhole_word(Boolean.parseBoolean(resobj.get("whole_word").toString()));
            filter.setIrreversible(Boolean.parseBoolean(resobj.get("irreversible").toString()));
            String contextString = resobj.get("context").toString();
            contextString = contextString.replaceAll("\\[", "");
            contextString = contextString.replaceAll("]", "");
            contextString = contextString.replaceAll("\"", "");
            if (contextString != null) {
                String[] context = contextString.split(",");
                if (contextString.length() > 0) {
                    ArrayList<String> finalContext = new ArrayList<>();
                    for (String c : context)
                        finalContext.add(c.trim());
                    filter.setContext(finalContext);
                }
            }
            return filter;
        } catch (Exception ignored) {
            return null;
        }

    }


    /**
     * Parse Lists
     *
     * @param jsonArray JSONArray
     * @return List<List> of lists
     */
    private List<fr.gouv.etalab.mastodon.client.Entities.List> parseLists(JSONArray jsonArray) {
        List<fr.gouv.etalab.mastodon.client.Entities.List> lists = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                fr.gouv.etalab.mastodon.client.Entities.List list = parseList(resobj);
                lists.add(list);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return lists;
    }


    /**
     * Parse json response for emoji
     *
     * @param resobj JSONObject
     * @return Emojis
     */
    private static fr.gouv.etalab.mastodon.client.Entities.List parseList(JSONObject resobj) {
        fr.gouv.etalab.mastodon.client.Entities.List list = new fr.gouv.etalab.mastodon.client.Entities.List();
        try {
            list.setId(resobj.get("id").toString());
            list.setTitle(resobj.get("title").toString());
        } catch (Exception ignored) {
        }
        return list;
    }

    private List<Account> parseAccountResponsePeertube(Context context, String instance, JSONArray jsonArray) {
        List<Account> accounts = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Account account = parseAccountResponsePeertube(context, instance, resobj);
                accounts.add(account);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return accounts;
    }

    /**
     * Parse json response an unique peertube account
     *
     * @param resobj JSONObject
     * @return Account
     */
    private static Account parseAccountResponsePeertube(Context context, String instance, JSONObject resobj) {
        Account account = new Account();
        try {
            account.setId(resobj.get("id").toString());
            account.setUsername(resobj.get("name").toString());
            account.setAcct(resobj.get("name").toString() + "@" + resobj.get("host").toString());
            account.setDisplay_name(resobj.get("displayName").toString());
            account.setHost(resobj.get("host").toString());
            if (resobj.has("createdAt"))
                account.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            else
                account.setCreated_at(new Date());

            if (resobj.has("followersCount"))
                account.setFollowers_count(Integer.valueOf(resobj.get("followersCount").toString()));
            else
                account.setFollowers_count(0);
            if (resobj.has("followingCount"))
                account.setFollowing_count(Integer.valueOf(resobj.get("followingCount").toString()));
            else
                account.setFollowing_count(0);
            account.setStatuses_count(0);
            if (resobj.has("description"))
                account.setNote(resobj.get("description").toString());
            else
                account.setNote("");
            account.setUrl(resobj.get("url").toString());
            if (resobj.has("avatar") && !resobj.get("avatar").toString().equals("null")) {
                account.setAvatar("https://" + instance + resobj.getJSONObject("avatar").get("path"));
            } else
                account.setAvatar(null);
            account.setAvatar_static(resobj.get("avatar").toString());
        } catch (JSONException ignored) {
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return account;
    }

    /**
     * Parse json response an unique account
     *
     * @param resobj JSONObject
     * @return Account
     */
    @SuppressWarnings("InfiniteRecursion")
    private static Account parseAccountResponse(Context context, JSONObject resobj, String instance) {

        Account account = new Account();
        try {
            account.setId(resobj.get("id").toString());
            account.setUsername(resobj.get("username").toString());
            if (!resobj.isNull("host")) {
                account.setAcct(resobj.get("username").toString() + "@" + resobj.getString("host"));
                account.setInstance("https://" + resobj.get("host").toString());
                account.setHost(resobj.get("host").toString());
            } else
                account.setAcct(resobj.get("username").toString());
            account.setInstance(instance);
            account.setHost(instance);
            account.setDisplay_name(resobj.get("name").toString());
//            account.setLocked(Boolean.parseBoolean(resobj.get("isLocked").toString()));
            try {
                if (!resobj.isNull("createdAt")) {
                    Log.d("createdat", resobj.getString("createdAt"));
                    account.setCreated_at(Helper.mstStringToDate(context, resobj.getString("createdAt")));
                } else if (!resobj.isNull("lastFetchedAt")) {
                    account.setCreated_at(Helper.mstStringToDate(context, resobj.getString("lastFetchedAt")));
                } else {
                    throw new Exception();
                }
            } catch (Exception ignored) {
                Locale userLocale;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    userLocale = context.getResources().getConfiguration().getLocales().get(0);
                } else {
                    userLocale = context.getResources().getConfiguration().locale;
                }
                String date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", userLocale).format(new Date());
                account.setCreated_at(Helper.mstStringToDate(context, date));
            }
            if (!resobj.isNull("followersCount")) {
                account.setFollowers_count(resobj.getInt("followersCount"));
            } else {
                account.setFollowers_count(0);
            }
            if (!resobj.isNull("followingCount")) {
                account.setFollowing_count(resobj.getInt("followingCount"));
            } else {
                account.setFollowing_count(0);
            }
            if (!resobj.isNull("notesCount")) {
                account.setStatuses_count(resobj.getInt("notesCount"));
            } else {
                account.setStatuses_count(0);
            }
            if (!resobj.isNull("description")) {
                account.setNote(resobj.getString("description"));
            } else {
                account.setNote("");
            }
            try {
                account.setBot(Boolean.parseBoolean(resobj.get("isBot").toString()));
            } catch (Exception e) {
                account.setBot(false);
            }
            try {
                account.setMoved_to_account(parseAccountResponse(context, resobj.getJSONObject("moved"), instance));
            } catch (Exception ignored) {
                account.setMoved_to_account(null);
            }
            account.setUrl(instance + "/@" + account.getAcct());
            if (!resobj.isNull("avatarUrl")) {
                account.setAvatar(resobj.get("avatarUrl").toString());
                account.setAvatar_static(resobj.get("avatarUrl").toString());
            }
            if (!resobj.isNull("bannerUrl")) {
                account.setHeader(resobj.get("bannerUrl").toString());
                account.setHeader_static(resobj.get("bannerUrl").toString());
            }

            JSONArray fields = new JSONArray();
            try {
                fields = resobj.getJSONArray("fields");
            } catch (JSONException ignored) {
            }

            LinkedHashMap<String, String> fieldsMap = new LinkedHashMap<>();
            LinkedHashMap<String, Boolean> fieldsMapVerified = new LinkedHashMap<>();
            if (fields != null) {
                for (int j = 0; j < fields.length(); j++) {
                    fieldsMap.put(fields.getJSONObject(j).getString("name"), fields.getJSONObject(j).getString("value"));
                    try {
                        fieldsMapVerified.put(fields.getJSONObject(j).getString("name"), (fields.getJSONObject(j).getString("verified_at") != null && !fields.getJSONObject(j).getString("verified_at").equals("null")));
                    } catch (Exception e) {
                        fieldsMapVerified.put(fields.getJSONObject(j).getString("name"), false);
                    }

                }
            }
            account.setFields(fieldsMap);
            account.setFieldsVerified(fieldsMapVerified);


            //Retrieves emjis
            List<Emojis> emojiList = new ArrayList<>();
            try {
                JSONArray emojisTag = resobj.getJSONArray("emojis");
                if (emojisTag != null) {
                    for (int j = 0; j < emojisTag.length(); j++) {
                        JSONObject emojisObj = emojisTag.getJSONObject(j);
                        Emojis emojis = parseEmojis(emojisObj);
                        emojiList.add(emojis);
                    }
                }
                account.setEmojis(emojiList);
            } catch (Exception e) {
                account.setEmojis(new ArrayList<>());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return account;
    }

    /**
     * Parse json response for list of accounts
     *
     * @param jsonArray JSONArray
     * @return List<Account>
     */
    private List<Account> parseAccountResponse(JSONArray jsonArray) {

        List<Account> accounts = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Account account = parseAccountResponse(context, resobj, instance);
                accounts.add(account);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return accounts;
    }


    /**
     * Parse json response an unique relationship
     *
     * @param resobj JSONObject
     * @return Relationship
     */
    private Relationship parseRelationshipResponse(JSONObject resobj) {

        Relationship relationship = new Relationship();
        try {
            relationship.setId(resobj.get("id").toString());
            relationship.setFollowing(resobj.getBoolean("isFollowing"));
            relationship.setFollowed_by(resobj.getBoolean("isFollowed"));
            relationship.setBlocking(resobj.getBoolean("isBlocking"));
            relationship.setMuting(resobj.getBoolean("isMuted"));
            try {
                relationship.setMuting_notifications(Boolean.valueOf(resobj.get("muting_notifications").toString()));
            } catch (Exception ignored) {
                relationship.setMuting_notifications(true);
            }
            try {
                relationship.setEndorsed(Boolean.valueOf(resobj.get("endorsed").toString()));
            } catch (Exception ignored) {
                relationship.setEndorsed(false);
            }
            try {
                relationship.setShowing_reblogs(Boolean.valueOf(resobj.get("showing_reblogs").toString()));
            } catch (Exception ignored) {
                relationship.setShowing_reblogs(false);
            }
            try {
                relationship.setRequested(resobj.getBoolean("hasPendingFollowRequestFromYou"));
            } catch (Exception ignored) {
                relationship.setRequested(false);
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return relationship;
    }


    /**
     * Parse json response for list of relationship
     *
     * @param jsonArray JSONArray
     * @return List<Relationship>
     */
    private List<Relationship> parseRelationshipResponseList(JSONArray jsonArray) {

        List<Relationship> relationships = new ArrayList<>();
        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject resobj = jsonArray.getJSONObject(i);
                Relationship relationship = parseRelationshipResponse(resobj);
                relationships.add(relationship);
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return relationships;
    }

    private List<Relationship> parseRelationshipResponseList(JSONObject jsonArray) {

        List<Relationship> relationships = new ArrayList<>();
        Relationship relationship = parseRelationshipResponse(jsonArray);
        relationships.add(relationship);
        return relationships;
    }

    /**
     * Parse json response for the context
     *
     * @param jsonarray JSONArray
     * @return fr.gouv.etalab.mastodon.client.Entities.Context
     */
    private fr.gouv.etalab.mastodon.client.Entities.Context parseContext(JSONArray jsonarray) {
        JSONArray ancestors = new JSONArray();
        for (int i = jsonarray.length() - 1; i >= 0; i--) {
            try {
                ancestors.put(jsonarray.get(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        fr.gouv.etalab.mastodon.client.Entities.Context context = new fr.gouv.etalab.mastodon.client.Entities.Context();
        context.setAncestors(parseStatuses(this.context, ancestors, instance));
        context.setDescendants(parseStatuses(this.context, new JSONArray(), instance));
        return context;
    }

    /**
     * Parse json response an unique attachment
     *
     * @param resobj JSONObject
     * @return Relationship
     */
    static Attachment parseAttachmentResponse(JSONObject resobj) {

        Attachment attachment = new Attachment();
        try {
            attachment.setId(resobj.get("id").toString());
            attachment.setType(resobj.get("type").toString());
            attachment.setUrl(resobj.get("url").toString());
            try {
                attachment.setDescription(resobj.get("description").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setRemote_url(resobj.get("remote_url").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setPreview_url(resobj.get("preview_url").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setMeta(resobj.get("meta").toString());
            } catch (JSONException ignore) {
            }
            try {
                attachment.setText_url(resobj.get("text_url").toString());
            } catch (JSONException ignore) {
            }

        } catch (JSONException ignored) {
        }
        return attachment;
    }


    /**
     * Parse json response an unique notification
     *
     * @param resobj JSONObject
     * @return Account
     */
    public static Notification parseNotificationResponse(Context context, JSONObject resobj, String instance) {

        Notification notification = new Notification();
        try {
            notification.setId(resobj.get("id").toString());
            notification.setType(resobj.get("type").toString());
            notification.setCreated_at(Helper.mstStringToDate(context, resobj.get("createdAt").toString()));
            notification.setAccount(parseAccountResponse(context, resobj.getJSONObject("user"), instance));
            try {
                notification.setStatus(parseStatuses(context, resobj.getJSONObject("note"), instance));
            } catch (Exception ignored) {
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return notification;
    }

    /**
     * Parse json response for list of notifications
     *
     * @param jsonArray JSONArray
     * @return List<Notification>
     */
    private List<Notification> parseNotificationResponse(JSONArray jsonArray) {

        List<Notification> notifications = new ArrayList<>();
        try {
            int i = 0;
            while (i < jsonArray.length()) {

                JSONObject resobj = jsonArray.getJSONObject(i);
                Notification notification = parseNotificationResponse(context, resobj, instance);
                notifications.add(notification);
                i++;
            }
        } catch (JSONException e) {
            setDefaultError(e);
        }
        return notifications;
    }


    /**
     * Set the error message
     *
     * @param statusCode int code
     * @param error      Throwable error
     */
    private void setError(int statusCode, Throwable error) {
        APIError = new Error();
        APIError.setStatusCode(statusCode);
        String message = statusCode + " - " + error.getMessage();
        try {
            JSONObject jsonObject = new JSONObject(error.getMessage());
            String errorM = jsonObject.get("error").toString();
            message = "Error " + statusCode + " : " + errorM;
        } catch (JSONException e) {
            if (error.getMessage().split(".").length > 0) {
                String errorM = error.getMessage().split(".")[0];
                message = "Error " + statusCode + " : " + errorM;
            }
        }
        APIError.setError(message);
        apiResponse.setError(APIError);
    }

    private void setDefaultError(Exception e) {
        APIError = new Error();
        if (e.getLocalizedMessage() != null && e.getLocalizedMessage().trim().length() > 0)
            APIError.setError(e.getLocalizedMessage());
        else if (e.getMessage() != null && e.getMessage().trim().length() > 0)
            APIError.setError(e.getMessage());
        else
            APIError.setError(context.getString(R.string.toast_error));
        apiResponse.setError(APIError);
    }


    public Error getError() {
        return APIError;
    }


    private String getAbsoluteUrl(String action) {
        return Helper.instanceWithProtocol(this.instance) + "/api" + action;
    }


    private String getAbsoluteUrlRemoteInstance(String instanceName) {
        return "https://" + instanceName + "/notes/local-timeline";
    }

    private String getAbsoluteUrlCommunitywiki(String action) {
        return "https://communitywiki.org/trunk" + action;
    }

}
