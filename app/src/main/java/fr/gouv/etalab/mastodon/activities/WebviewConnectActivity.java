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

package fr.gouv.etalab.mastodon.activities;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.asynctasks.UpdateAccountInfoAsyncTask;
import fr.gouv.etalab.mastodon.client.HttpsConnection;
import fr.gouv.etalab.mastodon.helper.Helper;

import static fr.gouv.etalab.mastodon.helper.Helper.THEME_LIGHT;

/**
 * Created by Thomas on 24/04/2017.
 * Webview to connect accounts
 */
public class WebviewConnectActivity extends BaseActivity {


    private WebView webView;
    private AlertDialog alert;
    private String auth_url, app_token, instance, app_secret;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        switch (theme){
            case Helper.THEME_LIGHT:
                setTheme(R.style.AppTheme);
                break;
            case Helper.THEME_DARK:
                setTheme(R.style.AppThemeDark);
                break;
            case Helper.THEME_BLACK:
                setTheme(R.style.AppThemeBlack);
                break;
            default:
                setTheme(R.style.AppThemeDark);
        }

        setContentView(R.layout.activity_webview_connect);
        Bundle b = getIntent().getExtras();
        if(b != null)
            instance = b.getString("instance");
        if( instance == null)
            finish();

        ActionBar actionBar = getSupportActionBar();
        if( actionBar != null ) {
            LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            assert inflater != null;
            @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.simple_bar, null);
            actionBar.setCustomView(view, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            ImageView toolbar_close = actionBar.getCustomView().findViewById(R.id.toolbar_close);
            TextView toolbar_title = actionBar.getCustomView().findViewById(R.id.toolbar_title);
            toolbar_close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            toolbar_title.setText(R.string.add_account);
            if (theme == THEME_LIGHT){
                Toolbar toolbar = actionBar.getCustomView().findViewById(R.id.toolbar);
                Helper.colorizeToolbar(toolbar, R.color.black, WebviewConnectActivity.this);
            }
        }
        webView = findViewById(R.id.webviewConnect);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        clearCookies(getApplicationContext());
        final ProgressBar pbar = findViewById(R.id.progress_bar);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100 && pbar.getVisibility() == ProgressBar.GONE) {
                    pbar.setVisibility(ProgressBar.VISIBLE);
                }
                pbar.setProgress(progress);
                if (progress == 100) {
                    pbar.setVisibility(ProgressBar.GONE);
                }
            }
        });
        auth_url = sharedpreferences.getString(Helper.AUTH_URL, null);
        app_token = sharedpreferences.getString(Helper.APP_TOKEN, null);
        app_secret = sharedpreferences.getString(Helper.APP_SECRET,null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                if (url.startsWith(Helper.REDIRECT_CONTENT_WEB))
                {
                    // user granted permission
                    // get /api/auth/session/userkey
                    // get the accessToken
                    final String action = "/api/auth/session/userKey";

                    final JSONObject parameters = new JSONObject();
                    try {
                        parameters.put(Helper.APP_SECRET, app_secret);
                        parameters.put(Helper.APP_TOKEN, app_token);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final String response = new HttpsConnection(WebviewConnectActivity.this).post(Helper.instanceWithProtocol(instance) + action, 30, parameters, null);
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        JSONObject resobj;
                                        try {
                                            SharedPreferences.Editor editor = sharedpreferences.edit();

                                            resobj = new JSONObject(response);
                                            String access_token = resobj.get(Helper.ACCESS_TOKEN).toString();

                                            editor.putString(Helper.ACCESS_TOKEN, access_token);

                                            // this is how you get the auth parameter "i" for misskey, hash the accessToken + appSecret
                                            String i_auth = access_token + app_secret;

                                            MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                            byte[] hash = digest.digest(i_auth.getBytes(Charset.forName("UTF-8")));

                                            StringBuffer hash_buffer = new StringBuffer();
                                            for (byte aHash : hash) {
                                                String hex = Integer.toHexString(0xff & aHash);
                                                if (hex.length() == 1) hash_buffer.append('0');
                                                hash_buffer.append(hex);
                                            }
                                            String i_string = hash_buffer.toString();

                                            editor.putString(Helper.PREF_KEY_OAUTH_TOKEN, i_string);

                                            editor.apply();
                                            new UpdateAccountInfoAsyncTask(WebviewConnectActivity.this, i_string, instance).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            } catch (final Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();

                }
                else
                {
                    // Load the page via the webview
                    view.loadUrl(url);
                }
                return true;
            }
        });
        webView.loadUrl(auth_url);
    }


    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (alert != null) {
            alert.dismiss();
            alert = null;
        }
    }

    @SuppressWarnings("deprecation")
    public static void clearCookies(Context context)
    {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            CookieSyncManager cookieSyncMngr=CookieSyncManager.createInstance(context);
            cookieSyncMngr.startSync();
            CookieManager cookieManager=CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }
    }
}