package fr.gouv.etalab.mastodon.services;
/* Copyright 2018 Thomas Schneider
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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;

import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.TLSSocketFactory;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;


/**
 * Created by Thomas on 18/12/2018.
 * Manage service for streaming api for home and notifications
 */

public class StreamingHomeTimelineService extends IntentService {

    static {
        Helper.installProvider();
    }
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    @SuppressWarnings("unused")
    public StreamingHomeTimelineService(String name) {
        super(name);
    }
    @SuppressWarnings("unused")
    public StreamingHomeTimelineService() {
        super("StreamingHomeTimelineService");
    }

    private static HttpsURLConnection httpsURLConnection;
    protected Account account;

    public void onCreate() {
        super.onCreate();
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean display_global = sharedpreferences.getBoolean(Helper.SET_DISPLAY_GLOBAL, true);
        if( !display_global){
            stopSelf();
        }
        SharedPreferences.Editor editor = sharedpreferences.edit();
        String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        String instance = sharedpreferences.getString(Helper.PREF_INSTANCE, Helper.getLiveInstance(getApplicationContext()));
        editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + userId + instance, true);
        editor.apply();
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        Account accountStream = null;
        if( userId != null) {
            SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            accountStream = new AccountDAO(getApplicationContext(), db).getAccountByID(userId);
        }
        if( accountStream != null) {
            Headers headers = new Headers();
            headers.add("Authorization", "Bearer " + accountStream.getToken());
            headers.add("Connection", "Keep-Alive");
            headers.add("method", "GET");
            headers.add("scheme", "https");
            Uri url = Uri.parse("wss://" + accountStream.getInstance() + "/api/v1/streaming/?stream=user&access_token="+ accountStream.getToken());
            AsyncHttpRequest.setDefaultHeaders(headers, url);
            Account finalAccountStream = accountStream;
            if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
                try {
                    AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(new TLSSocketFactory().getSSLContext());
                    AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setConnectAllAddresses(true);
                } catch (KeyManagementException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
            AsyncHttpClient.getDefaultInstance().websocket("wss://" + accountStream.getInstance() + "/api/v1/streaming/?stream=user&access_token="+ accountStream.getToken(),"wss", new AsyncHttpClient.WebSocketConnectCallback() {
                @Override
                public void onCompleted(Exception ex, WebSocket webSocket) {
                    if (ex != null) {
                        ex.printStackTrace();
                        return;
                    }
                    webSocket.setStringCallback(new WebSocket.StringCallback() {
                        public void onStringAvailable(String s) {
                            if (!sharedpreferences.getBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + finalAccountStream.getId() + finalAccountStream.getInstance(), true)) {
                                stopSelf();
                                return;
                            }
                            try {
                                JSONObject eventJson = new JSONObject(s);

                                onRetrieveStreaming(finalAccountStream, eventJson);
                            } catch (JSONException ignored) {}
                        }
                    });
                }
            });
        }
    }

    public void onRetrieveStreaming(Account account, JSONObject response) {
        String instance = "";
        if( Helper.getLiveInstance(this) != null)
            instance = Helper.getLiveInstance(this);
        if(  response == null )
            return;
        Status status ;
        Bundle b = new Bundle();
        try {
            if( response.get("event").toString().equals("update")){
                status = API.parseStatuses(getApplicationContext(), new JSONObject(response.get("payload").toString()),instance);
                status.setNew(true);
                b.putParcelable("data", status);
                if( account != null)
                    b.putString("userIdService",account.getId());
                Intent intentBC = new Intent(Helper.RECEIVE_HOME_DATA);
                intentBC.putExtras(b);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentBC);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
