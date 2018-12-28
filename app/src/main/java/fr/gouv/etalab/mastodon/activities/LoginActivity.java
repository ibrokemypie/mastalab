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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.elconfidencial.bubbleshowcase.BubbleShowCase;
import com.elconfidencial.bubbleshowcase.BubbleShowCaseBuilder;
import com.elconfidencial.bubbleshowcase.BubbleShowCaseListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.asynctasks.UpdateAccountInfoAsyncTask;
import fr.gouv.etalab.mastodon.client.HttpsConnection;
import fr.gouv.etalab.mastodon.helper.Helper;

import static fr.gouv.etalab.mastodon.helper.Helper.THEME_LIGHT;
import static fr.gouv.etalab.mastodon.helper.Helper.changeDrawableColor;
import static fr.gouv.etalab.mastodon.helper.Helper.convertDpToPixel;


/**
 * Created by Thomas on 23/04/2017.
 * Login activity class which handles the connection
 */

public class LoginActivity extends BaseActivity {

    private static String app_id;
    private static String app_secret;
    private TextView login_two_step;
    private static boolean client_id_for_webview = false;
    private static String instance;
    private AutoCompleteTextView login_instance;
    private EditText login_uid;
    private EditText login_passwd;
    boolean isLoadingInstance = false;
    private String oldSearch;
    private ImageView info_uid, info_instance, info_pwd, info_2FA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
            int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
            switch (theme) {
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

            setContentView(R.layout.activity_login);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
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
                if (theme == THEME_LIGHT) {
                    Toolbar toolbar = actionBar.getCustomView().findViewById(R.id.toolbar);
                    Helper.colorizeToolbar(toolbar, R.color.black, LoginActivity.this);
                }
            }
            if (theme == Helper.THEME_DARK) {
                changeDrawableColor(getApplicationContext(), R.drawable.mastodon_icon, R.color.mastodonC2);
            } else {
                changeDrawableColor(getApplicationContext(), R.drawable.mastodon_icon, R.color.mastodonC3);
            }
            final Button connectionButton = findViewById(R.id.login_button);
            login_instance = findViewById(R.id.login_instance);
            login_uid = findViewById(R.id.login_uid);
            login_passwd = findViewById(R.id.login_passwd);
            info_uid = findViewById(R.id.info_uid);
            info_instance = findViewById(R.id.info_instance);
            info_pwd = findViewById(R.id.info_pwd);
            info_2FA = findViewById(R.id.info_2FA);

            info_instance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showcaseInstance(false);
                }
            });

            info_uid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCaseLogin(false);
                }
            });
            info_pwd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCasePassword(false);
                }
            });
            info_2FA.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCase2FA(false);
                }
            });

            showcaseInstance(true);

            login_instance.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    oldSearch = parent.getItemAtPosition(position).toString().trim();
                }
            });
            login_instance.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 2 && !isLoadingInstance) {
                        final String action = "/instances/search";
                        final HashMap<String, String> parameters = new HashMap<>();
                        parameters.put("q", s.toString().trim());
                        parameters.put("count", String.valueOf(1000));
                        parameters.put("name", String.valueOf(true));
                        isLoadingInstance = true;
                        if (oldSearch == null || !oldSearch.equals(s.toString().trim()))
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        final String response = new HttpsConnection(LoginActivity.this).get("https://instances.social/api/1.0" + action, 30, parameters, Helper.THEKINRAR_SECRET_TOKEN);
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                isLoadingInstance = false;
                                                String[] instances;
                                                try {
                                                    JSONObject jsonObject = new JSONObject(response);
                                                    JSONArray jsonArray = jsonObject.getJSONArray("instances");
                                                    if (jsonArray != null) {
                                                        int length = 0;
                                                        for (int i = 0; i < jsonArray.length(); i++) {
                                                            if (!jsonArray.getJSONObject(i).get("name").toString().contains("@"))
                                                                length++;
                                                        }
                                                        instances = new String[length];
                                                        int j = 0;
                                                        for (int i = 0; i < jsonArray.length(); i++) {
                                                            if (!jsonArray.getJSONObject(i).get("name").toString().contains("@")) {
                                                                instances[j] = jsonArray.getJSONObject(i).get("name").toString();
                                                                j++;
                                                            }
                                                        }
                                                    } else {
                                                        instances = new String[]{};
                                                    }
                                                    login_instance.setAdapter(null);
                                                    ArrayAdapter<String> adapter =
                                                            new ArrayAdapter<>(LoginActivity.this, android.R.layout.simple_list_item_1, instances);
                                                    login_instance.setAdapter(adapter);
                                                    if (login_instance.hasFocus() && !LoginActivity.this.isFinishing())
                                                        login_instance.showDropDown();
                                                    oldSearch = s.toString().trim();

                                                } catch (JSONException ignored) {
                                                    isLoadingInstance = false;
                                                }
                                            }
                                        });

                                    } catch (HttpsConnection.HttpsConnectionException e) {
                                        isLoadingInstance = false;
                                    } catch (Exception e) {
                                        isLoadingInstance = false;
                                    }
                                }
                            }).start();
                    }
                }
            });


            connectionButton.setEnabled(false);
            login_two_step = findViewById(R.id.login_two_step);
            login_two_step.setVisibility(View.GONE);
            info_2FA.setVisibility(View.GONE);
            login_two_step.setPaintFlags(login_two_step.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            login_two_step.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    client_id_for_webview = true;
                    retrievesClientId();
                }
            });
            TextView instances_social = findViewById(R.id.instances_social);
            instances_social.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://instances.social"));
                    startActivity(browserIntent);
                }
            });
            login_instance.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    connectionButton.setEnabled(false);
                    login_two_step.setVisibility(View.INVISIBLE);
                    info_2FA.setVisibility(View.INVISIBLE);
                    TextInputLayout login_instance_layout = findViewById(R.id.login_instance_layout);
                    if (!hasFocus) {
                        retrievesClientId();
                        if (login_instance.getText() == null || login_instance.getText().toString().length() == 0) {
                            login_instance_layout.setError(getString(R.string.toast_error_instance));
                            login_instance_layout.setErrorEnabled(true);
                        }
                    } else {
                        login_instance_layout.setErrorEnabled(false);
                    }
                }
            });


            final TextView login_issue = findViewById(R.id.login_issue);
            SpannableString content = new SpannableString(getString(R.string.issue_login_title));
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
            login_issue.setText(content);
            login_issue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    int style;
                    if (theme == Helper.THEME_DARK) {
                        style = R.style.DialogDark;
                    } else if (theme == Helper.THEME_BLACK) {
                        style = R.style.DialogBlack;
                    } else {
                        style = R.style.Dialog;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this, style);
                    builder.setTitle(R.string.issue_login_title);
                    TextView message = new TextView(LoginActivity.this);
                    final SpannableString s =
                            new SpannableString(getText(R.string.issue_login_message));
                    Linkify.addLinks(s, Linkify.WEB_URLS);
                    message.setText(s);
                    message.setPadding((int) convertDpToPixel(10, LoginActivity.this), (int) convertDpToPixel(10, LoginActivity.this), (int) convertDpToPixel(10, LoginActivity.this), (int) convertDpToPixel(10, LoginActivity.this));
                    message.setMovementMethod(LinkMovementMethod.getInstance());
                    builder.setView(message);
                    builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.setIcon(android.R.drawable.ic_dialog_alert).show();
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button connectionButton = findViewById(R.id.login_button);
        if (login_instance != null && login_instance.getText() != null && login_instance.getText().toString().length() > 0 && client_id_for_webview) {
            connectionButton.setEnabled(false);
            client_id_for_webview = false;
            retrievesClientId();
        }
    }


    // create app
    // returns:
    //      createdAt: new Date(),
    //		userId: user && user._id,
    //		name: ps.name,
    //		description: ps.description,
    //		permission: ps.permission,
    //		callbackUrl: ps.callbackUrl,
    //		secret: secret
    private void retrievesClientId() {
        final Button connectionButton = findViewById(R.id.login_button);
        try {
            instance = URLEncoder.encode(login_instance.getText().toString().trim(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            Toasty.error(LoginActivity.this, getString(R.string.client_error), Toast.LENGTH_LONG).show();
        }
        final String action = "/api/app/create";
        final JSONArray perms = new JSONArray();
        for (String perm : Helper.OAUTH_SCOPES) {
            perms.put(perm);
        }

        final JSONObject parameters = new JSONObject();
        try {
            parameters.put(Helper.CLIENT_NAME, Helper.CLIENT_NAME_VALUE);
            parameters.put(Helper.CLIENT_DESCRIPTION, Helper.CLIENT_DESCRIPTION_VALUE);
            parameters.put(Helper.REDIRECT_URIS, Helper.REDIRECT_CONTENT_WEB);
            parameters.put(Helper.SCOPES, perms);
            parameters.put(Helper.WEBSITE, Helper.WEBSITE_VALUE);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String response = new HttpsConnection(LoginActivity.this).post(Helper.instanceWithProtocol(instance) + action, 30, parameters, null);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            JSONObject resobj;
                            try {
                                resobj = new JSONObject(response);
                                app_secret = resobj.get(Helper.SECRET).toString();

                                SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = sharedpreferences.edit();

                                editor.putString(Helper.APP_SECRET, app_secret);
                                editor.apply();

                                final String action = "/api/auth/session/generate";

                                final JSONObject parameters = new JSONObject();
                                try {
                                    parameters.put(Helper.APP_SECRET, sharedpreferences.getString(Helper.APP_SECRET, null));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            final String response = new HttpsConnection(LoginActivity.this).post(Helper.instanceWithProtocol(instance) + action, 30, parameters, null);
                                            runOnUiThread(new Runnable() {
                                                public void run() {
                                                    JSONObject resobj;
                                                    try {
                                                        resobj = new JSONObject(response);
                                                        String app_token = resobj.get(Helper.APP_TOKEN).toString();
                                                        String auth_url = resobj.get(Helper.AUTH_URL).toString();

                                                        editor.putString(Helper.APP_TOKEN, app_token);
                                                        editor.putString(Helper.AUTH_URL, auth_url);
                                                        editor.apply();
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            connectionButton.setEnabled(true);
                                                        }
                                                    });
                                                }
                                            });
                                        } catch (final Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            } catch (JSONException ignored) {
                                ignored.printStackTrace();
                            }
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            String message;
                            if (e.getLocalizedMessage() != null && e.getLocalizedMessage().trim().length() > 0)
                                message = e.getLocalizedMessage();
                            else if (e.getMessage() != null && e.getMessage().trim().length() > 0)
                                message = e.getMessage();
                            else
                                message = getString(R.string.client_error);
                            Toasty.error(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();



        // open the auth_url in WebviewConnectActivity
        connectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectionButton.setEnabled(false);

                Intent i = new Intent(LoginActivity.this, WebviewConnectActivity.class);
                i.putExtra("instance", instance);
                startActivity(i);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_login, menu);
        CheckBox checkBox = (CheckBox) menu.findItem(R.id.action_custom_tabs).getActionView();
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
        boolean embedded_browser = sharedpreferences.getBoolean(Helper.SET_EMBEDDED_BROWSER, true);
        checkBox.setChecked(!embedded_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_privacy) {
            Intent intent = new Intent(getApplicationContext(), PrivacyActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_proxy) {
            Intent intent = new Intent(getApplicationContext(), ProxyActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_custom_tabs) {
            item.setChecked(!item.isChecked());
            SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putBoolean(Helper.SET_EMBEDDED_BROWSER, !item.isChecked());
            editor.apply();
            return false;
        }
        return super.onOptionsItemSelected(item);
    }


    public static String redirectUserToAuthorizeAndLogin(String clientId, String instance) {
        String queryString = Helper.APP_ID + "=" + clientId;
        queryString += "&" + Helper.REDIRECT_URI + "=" + Uri.encode(Helper.REDIRECT_CONTENT_WEB);
        queryString += "&" + Helper.RESPONSE_TYPE + "=code";
        queryString += "&" + Helper.SCOPE + "=" + Helper.OAUTH_SCOPES;
        return Helper.instanceWithProtocol(instance) + Helper.EP_AUTHORIZE + "?" + queryString;
    }


    private void showcaseInstance(final boolean loop) {

        BubbleShowCaseBuilder showCaseBuilder = new BubbleShowCaseBuilder(LoginActivity.this)
                .title(getString(R.string.instance))
                .description(getString(R.string.showcase_instance))
                .arrowPosition(BubbleShowCase.ArrowPosition.TOP)
                .backgroundColor(ContextCompat.getColor(LoginActivity.this, R.color.mastodonC4))
                .textColor(Color.WHITE)
                .titleTextSize(17)
                .descriptionTextSize(15);
        if (loop)
            showCaseBuilder.showOnce("BUBBLE_SHOW_CASE_INSTANCE_ID");
        showCaseBuilder.listener(new BubbleShowCaseListener() {
            @Override
            public void onTargetClick(BubbleShowCase bubbleShowCase) {
                if (loop) {
                    bubbleShowCase.finishSequence();
                    showCaseLogin(true);
                }
            }

            @Override
            public void onCloseActionImageClick(BubbleShowCase bubbleShowCase) {
                if (loop) {
                    bubbleShowCase.finishSequence();
                    showCaseLogin(true);
                }
            }

            @Override
            public void onBubbleClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBackgroundDimClick(BubbleShowCase bubbleShowCase) {

            }

        })
                .targetView(login_instance)
                .show();
    }

    private void showCaseLogin(final boolean loop) {
        BubbleShowCaseBuilder showCaseBuilder = new BubbleShowCaseBuilder(LoginActivity.this) //Activity instance
                .title(getString(R.string.login))
                .description(getString(R.string.showcase_uid))
                .arrowPosition(BubbleShowCase.ArrowPosition.TOP)
                .backgroundColor(ContextCompat.getColor(LoginActivity.this, R.color.mastodonC4))
                .textColor(Color.WHITE)
                .titleTextSize(17)
                .descriptionTextSize(15);
        if (loop)
            showCaseBuilder.showOnce("BUBBLE_SHOW_CASE_UID_ID");
        showCaseBuilder.listener(new BubbleShowCaseListener() {
            @Override
            public void onTargetClick(BubbleShowCase bubbleShowCase) {
                if (loop) {
                    bubbleShowCase.finishSequence();
                    showCasePassword(true);
                }
            }

            @Override
            public void onCloseActionImageClick(BubbleShowCase bubbleShowCase) {
                if (loop) {
                    bubbleShowCase.finishSequence();
                    showCasePassword(true);
                }
            }

            @Override
            public void onBubbleClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBackgroundDimClick(BubbleShowCase bubbleShowCase) {
            }

        })
                .targetView(login_uid)
                .show();
    }

    private void showCasePassword(boolean loop) {
        BubbleShowCaseBuilder showCaseBuilder = new BubbleShowCaseBuilder(LoginActivity.this)
                .title(getString(R.string.password))
                .description(getString(R.string.showcase_pwd))
                .arrowPosition(BubbleShowCase.ArrowPosition.BOTTOM)
                .backgroundColor(ContextCompat.getColor(LoginActivity.this, R.color.mastodonC4))
                .textColor(Color.WHITE)
                .titleTextSize(17)
                .descriptionTextSize(15);
        if (loop)
            showCaseBuilder.showOnce("BUBBLE_SHOW_CASE_PASSWORD_ID");
        showCaseBuilder.listener(new BubbleShowCaseListener() {
            @Override
            public void onTargetClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onCloseActionImageClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBubbleClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBackgroundDimClick(BubbleShowCase bubbleShowCase) {

            }

        })
                .targetView(login_passwd)
                .show();
    }


    private void showCase2FA(boolean loop) {
        BubbleShowCaseBuilder showCaseBuilder = new BubbleShowCaseBuilder(LoginActivity.this)
                .title(getString(R.string.two_factor_authentification))
                .description(getString(R.string.showcase_2FA))
                .arrowPosition(BubbleShowCase.ArrowPosition.BOTTOM)
                .backgroundColor(ContextCompat.getColor(LoginActivity.this, R.color.mastodonC4))
                .textColor(Color.WHITE)
                .titleTextSize(17)
                .descriptionTextSize(15);
        if (loop)
            showCaseBuilder.showOnce("BUBBLE_SHOW_CASE_2FA_ID");
        showCaseBuilder.listener(new BubbleShowCaseListener() {
            @Override
            public void onTargetClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onCloseActionImageClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBubbleClick(BubbleShowCase bubbleShowCase) {

            }

            @Override
            public void onBackgroundDimClick(BubbleShowCase bubbleShowCase) {

            }

        })
                .targetView(login_two_step)
                .show();
    }

}