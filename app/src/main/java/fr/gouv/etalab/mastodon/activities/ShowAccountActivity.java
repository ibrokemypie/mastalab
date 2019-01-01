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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.asynctasks.PostActionAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveAccountAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveAccountsAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveRelationshipAsyncTask;
import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Attachment;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.client.Entities.Relationship;
import fr.gouv.etalab.mastodon.client.Entities.RemoteInstance;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.HttpsConnection;
import fr.gouv.etalab.mastodon.drawers.StatusListAdapter;
import fr.gouv.etalab.mastodon.fragments.DisplayAccountsFragment;
import fr.gouv.etalab.mastodon.fragments.DisplayStatusFragment;
import fr.gouv.etalab.mastodon.fragments.TabLayoutTootsFragment;
import fr.gouv.etalab.mastodon.helper.CrossActions;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnPostActionInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveAccountInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveEmojiAccountInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsAccountInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveRelationshipInterface;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.InstancesDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;
import fr.gouv.etalab.mastodon.sqlite.TempMuteDAO;

import static fr.gouv.etalab.mastodon.helper.Helper.INSTANCE_NAME;
import static fr.gouv.etalab.mastodon.helper.Helper.INTENT_ACTION;
import static fr.gouv.etalab.mastodon.helper.Helper.SEARCH_INSTANCE;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_BLACK;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_DARK;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_LIGHT;
import static fr.gouv.etalab.mastodon.helper.Helper.changeDrawableColor;
import static fr.gouv.etalab.mastodon.helper.Helper.getLiveInstance;
import static fr.gouv.etalab.mastodon.helper.Helper.getLiveInstanceWithProtocol;
import static fr.gouv.etalab.mastodon.helper.Helper.withSuffix;


/**
 * Created by Thomas on 01/05/2017.
 * Show account activity class
 */

public class ShowAccountActivity extends BaseActivity implements OnPostActionInterface, OnRetrieveAccountInterface, OnRetrieveFeedsAccountInterface, OnRetrieveRelationshipInterface, OnRetrieveFeedsInterface, OnRetrieveEmojiAccountInterface {


    private List<Status> statuses;
    private StatusListAdapter statusListAdapter;
    private FloatingActionButton account_follow;

    private ViewPager mPager;
    private TabLayout tabLayout;
    private TextView account_note, account_follow_request, account_type, account_bot;
    private String userId;
    private Relationship relationship;
    private FloatingActionButton header_edit_profile;
    private List<Status> pins;
    private String accountUrl;
    private int maxScrollSize;
    private boolean avatarShown = true;
    private DisplayStatusFragment displayStatusFragment;
    private ImageView account_pp;
    private TextView account_dn;
    private TextView account_un;
    private Account account, oldaccount;
    private boolean show_boosts, show_replies;
    private boolean showMediaOnly, showPinned;
    private boolean peertubeAccount;
    private ImageView pp_actionBar;
    private String accountId;

    public enum action {
        FOLLOW,
        UNFOLLOW,
        UNBLOCK,
        NOTHING
    }

    private action doAction;
    private API.StatusAction doActionAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        switch (theme) {
            case Helper.THEME_LIGHT:
                setTheme(R.style.AppTheme_NoActionBar);
                break;
            case Helper.THEME_DARK:
                setTheme(R.style.AppThemeDark_NoActionBar);
                break;
            case Helper.THEME_BLACK:
                setTheme(R.style.AppThemeBlack_NoActionBar);
                break;
            default:
                setTheme(R.style.AppThemeDark_NoActionBar);
        }
        setContentView(R.layout.activity_show_account);
        setTitle("");
        pins = new ArrayList<>();
        Bundle b = getIntent().getExtras();
        account_follow = findViewById(R.id.account_follow);
        account_follow_request = findViewById(R.id.account_follow_request);
        header_edit_profile = findViewById(R.id.header_edit_profile);
        account_follow.setEnabled(false);
        account_pp = findViewById(R.id.account_pp);
        account_dn = findViewById(R.id.account_dn);
        account_un = findViewById(R.id.account_un);
        account_type = findViewById(R.id.account_type);
        account_bot = findViewById(R.id.account_bot);
        if (b != null) {
            oldaccount = b.getParcelable("account");
            if (oldaccount == null) {
                accountId = b.getString("accountId");
            } else {
                accountId = oldaccount.getId();
            }
//            try {
//                JSONObject params = new JSONObject();
//                params.put("userId", accountId);
//                runOnUiThread(new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            String response = new HttpsConnection(getApplicationContext()).post(getLiveInstanceWithProtocol(getApplicationContext()) + "/api/users/show", 60, params, null);
//                            account = API.parseAccountResponse(getApplicationContext(), new JSONObject(response), getLiveInstance(getApplicationContext()));
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }));
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            peertubeAccount = b.getBoolean("peertubeAccount", false);
            if (account == null) {
                new RetrieveAccountAsyncTask(getApplicationContext(), accountId, ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);

        } else {
            Toasty.error(this, getString(R.string.toast_error_loading_account), Toast.LENGTH_LONG).show();
        }
        accountUrl = null;
        show_boosts = true;
        show_replies = true;

        statuses = new ArrayList<>();
        boolean isOnWifi = Helper.isOnWIFI(getApplicationContext());
        statusListAdapter = new StatusListAdapter(getApplicationContext(), RetrieveFeedsAsyncTask.Type.USER, accountId, isOnWifi, this.statuses);

        showMediaOnly = false;
        showPinned = false;


        tabLayout = findViewById(R.id.account_tabLayout);

        account_note = findViewById(R.id.account_note);


        header_edit_profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ShowAccountActivity.this, EditProfileActivity.class);
                startActivity(intent);
            }
        });


        final ImageButton account_menu = findViewById(R.id.account_menu);
        ImageButton action_more = findViewById(R.id.action_more);
        if (theme == THEME_LIGHT)
            changeDrawableColor(getApplicationContext(), action_more, R.color.dark_icon);
        account_menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(account_menu);
            }
        });
        action_more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(account_menu);
            }
        });
        if (account != null) {
            ManageAccount();
        }
    }


    private void ManageAccount() {
        SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        accountUrl = account.getUrl();
        if (theme == Helper.THEME_BLACK) {
            changeDrawableColor(getApplicationContext(), R.drawable.ic_lock_outline, R.color.dark_icon);
        } else {
            changeDrawableColor(getApplicationContext(), R.drawable.ic_lock_outline, R.color.mastodonC4);
        }
        new RetrieveRelationshipAsyncTask(getApplicationContext(), accountId, ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        String urlHeader = account.getHeader();
        if (urlHeader != null && urlHeader.startsWith("/")) {
            urlHeader = Helper.getLiveInstanceWithProtocol(ShowAccountActivity.this) + account.getHeader();
        }
        if (urlHeader != null && !urlHeader.contains("missing.png")) {

            Glide.with(getApplicationContext())
                    .asBitmap()
                    .load(urlHeader)
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            ImageView banner_pp = findViewById(R.id.banner_pp);
                            banner_pp.setImageBitmap(resource);
                            if (theme == THEME_LIGHT) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                    banner_pp.setImageAlpha(80);
                                } else {
                                    banner_pp.setAlpha(80);
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                    banner_pp.setImageAlpha(60);
                                } else {
                                    banner_pp.setAlpha(60);
                                }
                            }

                        }
                    });

        }
        //Redraws icon for locked accounts
        final float scale = getResources().getDisplayMetrics().density;
        if (account.isLocked()) {
            Drawable img = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_lock_outline);
            assert img != null;
            img.setBounds(0, 0, (int) (20 * scale + 0.5f), (int) (20 * scale + 0.5f));
            account_dn.setCompoundDrawables(img, null, null, null);
        } else {
            account_dn.setCompoundDrawables(null, null, null, null);
        }

        //Peertube account
        if (peertubeAccount) {
            account_type.setVisibility(View.VISIBLE);
        }
        //Bot account
        if (account.isBot()) {
            account_bot.setVisibility(View.VISIBLE);
        }

        TextView actionbar_title = findViewById(R.id.show_account_title);
        if (account.getAcct() != null)
            actionbar_title.setText(account.getAcct());
        pp_actionBar = findViewById(R.id.pp_actionBar);
        if (account.getAvatar() != null) {
            String url = account.getAvatar();
            if (url.startsWith("/")) {
                url = Helper.getLiveInstanceWithProtocol(getApplicationContext()) + account.getAvatar();
            }
            Helper.loadGiF(getApplicationContext(), url, pp_actionBar);

        }
        final AppBarLayout appBar = findViewById(R.id.appBar);
        maxScrollSize = appBar.getTotalScrollRange();

        final TextView warning_message = findViewById(R.id.warning_message);
        final SpannableString content = new SpannableString(getString(R.string.disclaimer_full));
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        if (theme == THEME_DARK)
            content.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ShowAccountActivity.this, R.color.dark_link_toot)), 0, content.length(),
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        else if (theme == THEME_BLACK)
            content.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ShowAccountActivity.this, R.color.black_link_toot)), 0, content.length(),
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        else if (theme == THEME_LIGHT)
            content.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ShowAccountActivity.this, R.color.mastodonC4)), 0, content.length(),
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        warning_message.setText(content);
        warning_message.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!accountUrl.startsWith("http://") && !accountUrl.startsWith("https://"))
                    accountUrl = "http://" + accountUrl;
                Helper.openBrowser(ShowAccountActivity.this, accountUrl);
            }
        });
        //Timed muted account
        String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        final SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
        final Account authenticatedAccount = new AccountDAO(getApplicationContext(), db).getAccountByID(userId);
        boolean isTimedMute = new TempMuteDAO(getApplicationContext(), db).isTempMuted(authenticatedAccount, accountId);
        if (isTimedMute) {
            String date_mute = new TempMuteDAO(getApplicationContext(), db).getMuteDateByID(authenticatedAccount, accountId);
            if (date_mute != null) {
                final TextView temp_mute = findViewById(R.id.temp_mute);
                temp_mute.setVisibility(View.VISIBLE);
                SpannableString content_temp_mute = new SpannableString(getString(R.string.timed_mute_profile, account.getAcct(), date_mute));
                content_temp_mute.setSpan(new UnderlineSpan(), 0, content_temp_mute.length(), 0);
                temp_mute.setText(content_temp_mute);
                temp_mute.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        new TempMuteDAO(getApplicationContext(), db).remove(authenticatedAccount, accountId);
                        Toasty.success(getApplicationContext(), getString(R.string.toast_unmute), Toast.LENGTH_LONG).show();
                        temp_mute.setVisibility(View.GONE);
                    }
                });
            }
        }
        //This account was moved to another one
        if (account.getMoved_to_account() != null) {
            TextView account_moved = findViewById(R.id.account_moved);
            account_moved.setVisibility(View.VISIBLE);
            if (theme == THEME_DARK || theme == THEME_BLACK)
                changeDrawableColor(ShowAccountActivity.this, R.drawable.ic_card_travel, R.color.dark_icon);
            else
                changeDrawableColor(ShowAccountActivity.this, R.drawable.ic_card_travel, R.color.black);
            Drawable imgTravel = ContextCompat.getDrawable(ShowAccountActivity.this, R.drawable.ic_card_travel);
            assert imgTravel != null;
            imgTravel.setBounds(0, 0, (int) (20 * scale + 0.5f), (int) (20 * scale + 0.5f));
            account_moved.setCompoundDrawables(imgTravel, null, null, null);
            //Retrieves content and make account names clickable
            SpannableString spannableString = account.moveToText(ShowAccountActivity.this);
            account_moved.setText(spannableString, TextView.BufferType.SPANNABLE);
            account_moved.setMovementMethod(LinkMovementMethod.getInstance());
        }


        if (account.getAcct().contains("@"))
            warning_message.setVisibility(View.VISIBLE);
        else
            warning_message.setVisibility(View.GONE);
        appBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                LinearLayout toolbarContent = findViewById(R.id.toolbar_content);
                if (toolbarContent != null) {
                    if (Math.abs(verticalOffset) - appBar.getTotalScrollRange() == 0) {
                        if (toolbarContent.getVisibility() == View.GONE)
                            toolbarContent.setVisibility(View.VISIBLE);
                    } else {
                        if (toolbarContent.getVisibility() == View.VISIBLE)
                            toolbarContent.setVisibility(View.GONE);
                    }
                }
                if (maxScrollSize == 0)
                    maxScrollSize = appBarLayout.getTotalScrollRange();

                int percentage = (Math.abs(verticalOffset)) * 100 / maxScrollSize;

                if (percentage >= 40 && avatarShown) {
                    avatarShown = false;

                    account_pp.animate()
                            .scaleY(0).scaleX(0)
                            .setDuration(400)
                            .start();
                    warning_message.setVisibility(View.GONE);
                }
                if (percentage <= 40 && !avatarShown) {
                    avatarShown = true;
                    account_pp.animate()
                            .scaleY(1).scaleX(1)
                            .start();
                    if (account.getAcct().contains("@"))
                        warning_message.setVisibility(View.VISIBLE);
                    else
                        warning_message.setVisibility(View.GONE);
                }
            }
        });
        mPager = findViewById(R.id.account_viewpager);
        if (!peertubeAccount) {
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.toots)));
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.following)));
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.followers)));
            mPager.setOffscreenPageLimit(3);
        } else {
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.videos)));
            tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.channels)));
            mPager.setOffscreenPageLimit(2);
        }


        PagerAdapter mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                TabLayout.Tab tab = tabLayout.getTabAt(position);
                if (tab != null)
                    tab.select();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                Fragment fragment = null;
                if (mPager.getAdapter() != null)
                    fragment = (Fragment) mPager.getAdapter().instantiateItem(mPager, tab.getPosition());
                switch (tab.getPosition()) {
                    case 0:
                        if (displayStatusFragment != null)
                            displayStatusFragment.scrollToTop();
                        break;
                    case 1:
                    case 2:
                        if (fragment != null) {
                            DisplayAccountsFragment displayAccountsFragment = ((DisplayAccountsFragment) fragment);
                            displayAccountsFragment.scrollToTop();
                        }
                        break;
                }
            }
        });


        if (account.getFields() != null && account.getFields().size() > 0) {
            Log.d("fields", account.getFields().toString());
            LinkedHashMap<String, String> fields = account.getFields();
            LinkedHashMap<String, Boolean> fieldsVerified = account.getFieldsVerified();
            Iterator it = fields.entrySet().iterator();
            int i = 1;
            LinearLayout fields_container = findViewById(R.id.fields_container);
            if (fields_container != null)
                fields_container.setVisibility(View.VISIBLE);
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                String label = (String) pair.getKey();
                String value = (String) pair.getValue();
                boolean verified = fieldsVerified.get(label);

                LinearLayout field;
                TextView labelView;
                TextView valueView;
                LinearLayout verifiedView;
                switch (i) {
                    case 1:
                        field = findViewById(R.id.field1);
                        labelView = findViewById(R.id.label1);
                        valueView = findViewById(R.id.value1);
                        verifiedView = findViewById(R.id.value1BG);
                        break;
                    case 2:
                        field = findViewById(R.id.field2);
                        labelView = findViewById(R.id.label2);
                        valueView = findViewById(R.id.value2);
                        verifiedView = findViewById(R.id.value2BG);
                        break;
                    case 3:
                        field = findViewById(R.id.field3);
                        labelView = findViewById(R.id.label3);
                        valueView = findViewById(R.id.value3);
                        verifiedView = findViewById(R.id.value3BG);
                        break;
                    case 4:
                        field = findViewById(R.id.field4);
                        labelView = findViewById(R.id.label4);
                        valueView = findViewById(R.id.value4);
                        verifiedView = findViewById(R.id.value4BG);
                        break;
                    default:
                        field = findViewById(R.id.field1);
                        labelView = findViewById(R.id.label1);
                        valueView = findViewById(R.id.value1);
                        verifiedView = findViewById(R.id.value1BG);
                        break;
                }
                if (field != null && labelView != null && valueView != null) {
                    switch (theme) {
                        case Helper.THEME_LIGHT:
                            labelView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_light_2));
                            valueView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_light_4));
                            break;
                        case Helper.THEME_DARK:
                            labelView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_dark_2));
                            valueView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_dark_4));
                            break;
                        case Helper.THEME_BLACK:
                            labelView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_black_2));
                            valueView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_black_4));
                            break;
                        default:
                            labelView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_dark_2));
                            valueView.setBackgroundColor(ContextCompat.getColor(ShowAccountActivity.this, R.color.notif_dark_4));
                    }
                    field.setVisibility(View.VISIBLE);
                    SpannableString spannableValueString;
                    if (verified) {
                        value = "✓ " + value;
                        verifiedView.setBackgroundResource(R.drawable.verified);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            valueView.setBackground(null);
                        }
                        spannableValueString = Helper.clickableElementsDescription(ShowAccountActivity.this, value);
                        spannableValueString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ShowAccountActivity.this, R.color.verified_text)), 0, spannableValueString.toString().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        spannableValueString = Helper.clickableElementsDescription(ShowAccountActivity.this, value);
                    }
                    valueView.setText(spannableValueString, TextView.BufferType.SPANNABLE);
                    valueView.setMovementMethod(LinkMovementMethod.getInstance());
                    labelView.setText(label);
                }
                i++;
            }
        }

        account_dn.setText(Helper.shortnameToUnicode(account.getDisplay_name(), true));
        account_un.setText(String.format("@%s", account.getAcct()));
        account_un.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                String account_id = account.getAcct();
                if (account_id.split("@").length == 1)
                    account_id += "@" + Helper.getLiveInstance(getApplicationContext());
                ClipData clip = ClipData.newPlainText("mastodon_account_id", "@" + account_id);
                Toasty.info(getApplicationContext(), getString(R.string.account_id_clipbloard), Toast.LENGTH_SHORT).show();
                assert clipboard != null;
                clipboard.setPrimaryClip(clip);
                return false;
            }
        });
//        SpannableString spannableString = Helper.clickableElementsDescription(ShowAccountActivity.this, account.getNote());
//        account.setNoteSpan(spannableString);
        account.makeEmojisAccountProfile(ShowAccountActivity.this, ShowAccountActivity.this, account);
        account_note.setText(account.getNote(), TextView.BufferType.SPANNABLE);
        account_note.setMovementMethod(LinkMovementMethod.getInstance());
        if (!peertubeAccount && tabLayout.getTabAt(0) != null && tabLayout.getTabAt(1) != null && tabLayout.getTabAt(2) != null) {
            //noinspection ConstantConditions
            tabLayout.getTabAt(0).setText(getString(R.string.status_cnt, withSuffix(account.getStatuses_count())));
            //noinspection ConstantConditions
            tabLayout.getTabAt(1).setText(getString(R.string.following_cnt, withSuffix(account.getFollowing_count())));
            //noinspection ConstantConditions
            tabLayout.getTabAt(2).setText(getString(R.string.followers_cnt, withSuffix(account.getFollowers_count())));

            //Allows to filter by long click
            final LinearLayout tabStrip = (LinearLayout) tabLayout.getChildAt(0);
            tabStrip.getChildAt(0).setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    PopupMenu popup = new PopupMenu(ShowAccountActivity.this, tabStrip.getChildAt(0));
                    popup.getMenuInflater()
                            .inflate(R.menu.option_filter_toots_account, popup.getMenu());
                    Menu menu = popup.getMenu();

                    if (!Helper.canPin) {
                        popup.getMenu().findItem(R.id.action_show_pinned).setVisible(false);
                    }
                    final MenuItem itemShowPined = menu.findItem(R.id.action_show_pinned);
                    final MenuItem itemShowMedia = menu.findItem(R.id.action_show_media);
                    final MenuItem itemShowBoosts = menu.findItem(R.id.action_show_boosts);
                    final MenuItem itemShowReplies = menu.findItem(R.id.action_show_replies);

                    itemShowMedia.setChecked(showMediaOnly);
                    itemShowPined.setChecked(showPinned);
                    itemShowBoosts.setChecked(show_boosts);
                    itemShowReplies.setChecked(show_replies);

                    popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                        @Override
                        public void onDismiss(PopupMenu menu) {
                            if (displayStatusFragment != null)
                                displayStatusFragment.refreshFilter();
                        }
                    });
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                            item.setActionView(new View(getApplicationContext()));
                            item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                                @Override
                                public boolean onMenuItemActionExpand(MenuItem item) {
                                    return false;
                                }

                                @Override
                                public boolean onMenuItemActionCollapse(MenuItem item) {
                                    return false;
                                }
                            });
                            switch (item.getItemId()) {
                                case R.id.action_show_pinned:
                                    showPinned = !showPinned;
                                    break;
                                case R.id.action_show_media:
                                    showMediaOnly = !showMediaOnly;
                                    break;
                                case R.id.action_show_boosts:
                                    show_boosts = !show_boosts;

                                    break;
                                case R.id.action_show_replies:
                                    show_replies = !show_replies;
                                    break;
                            }
                            if (tabLayout.getTabAt(0) != null)
                                //noinspection ConstantConditions
                                tabLayout.getTabAt(0).select();
                            PagerAdapter mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
                            mPager.setAdapter(mPagerAdapter);
                            itemShowMedia.setChecked(showMediaOnly);
                            itemShowPined.setChecked(showPinned);
                            itemShowReplies.setChecked(show_replies);
                            itemShowBoosts.setChecked(show_boosts);
                            return true;
                        }
                    });
                    popup.show();
                    return true;
                }
            });


        }
        boolean disableGif = sharedpreferences.getBoolean(Helper.SET_DISABLE_GIF, false);
        if (!disableGif)
            Glide.with(getApplicationContext()).load(account.getAvatar()).apply(RequestOptions.circleCropTransform()).into(account_pp);
        else
            Glide.with(getApplicationContext())
                    .asBitmap()
                    .load(account.getAvatar())
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                            RoundedBitmapDrawable circularBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), Helper.addBorder(resource, account_pp.getContext()));
                            circularBitmapDrawable.setCircular(true);
                            account_pp.setImageDrawable(circularBitmapDrawable);
                        }
                    });
        account_pp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ShowAccountActivity.this, MediaActivity.class);
                Bundle b = new Bundle();
                Attachment attachment = new Attachment();
                attachment.setDescription(account.getAcct());
                attachment.setPreview_url(account.getAvatar());
                attachment.setUrl(account.getAvatar());
                attachment.setRemote_url(account.getAvatar());
                attachment.setType("image");
                ArrayList<Attachment> attachments = new ArrayList<>();
                attachments.add(attachment);
                intent.putParcelableArrayListExtra("mediaArray", attachments);
                b.putInt("position", 1);
                intent.putExtras(b);
                startActivity(intent);
            }
        });
        //Follow button
        account_follow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doAction == action.NOTHING) {
                    Toasty.info(getApplicationContext(), getString(R.string.nothing_to_do), Toast.LENGTH_LONG).show();
                } else if (doAction == action.FOLLOW) {
                    account_follow.setEnabled(false);
                    new PostActionAsyncTask(getApplicationContext(), API.StatusAction.FOLLOW, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else if (doAction == action.UNFOLLOW) {
                    account_follow.setEnabled(false);
                    new PostActionAsyncTask(getApplicationContext(), API.StatusAction.UNFOLLOW, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else if (doAction == action.UNBLOCK) {
                    account_follow.setEnabled(false);
                    new PostActionAsyncTask(getApplicationContext(), API.StatusAction.UNBLOCK, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        });
        account_follow.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                CrossActions.doCrossAction(ShowAccountActivity.this, null, null, account, API.StatusAction.FOLLOW, null, ShowAccountActivity.this, false);
                return false;
            }
        });
    }


    @Override
    public void onRetrieveFeedsAccount(List<Status> statuses) {
        if (statuses != null) {
            this.statuses.addAll(statuses);
            statusListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onRetrieveFeeds(APIResponse apiResponse) {
        if (apiResponse.getError() != null) {
            Toasty.error(getApplicationContext(), apiResponse.getError().getError(), Toast.LENGTH_LONG).show();
            return;
        }

        pins = apiResponse.getStatuses();
        if (pins != null && pins.size() > 0) {
            if (pins.get(0).isPinned()) {
                this.statuses.addAll(pins);
                //noinspection ConstantConditions
                tabLayout.getTabAt(3).setText(getString(R.string.pins_cnt, pins.size()));
                statusListAdapter.notifyDataSetChanged();
            }
        }
    }


    @Override
    public void onRetrieveRelationship(Relationship relationship, Error error) {

        if (error != null) {
            Toasty.error(getApplicationContext(), error.getError(), Toast.LENGTH_LONG).show();
            return;
        }
        this.relationship = relationship;
        manageButtonVisibility();


        //The authenticated account is followed by the account
        if (relationship != null && relationship.isFollowed_by()) {
            TextView account_followed_by = findViewById(R.id.account_followed_by);
            account_followed_by.setVisibility(View.VISIBLE);
        }
        invalidateOptionsMenu();

    }

    //Manages the visibility of the button
    private void manageButtonVisibility() {
        if (relationship == null)
            return;
        account_follow.setEnabled(true);
        if (account.getId() != null && account.getId().equals(userId)) {
            account_follow.hide();
            header_edit_profile.show();
            header_edit_profile.bringToFront();
        } else if (relationship.isBlocking()) {
            account_follow.setImageResource(R.drawable.ic_lock_open);
            doAction = action.UNBLOCK;
            account_follow.show();
        } else if (relationship.isRequested()) {
            account_follow_request.setVisibility(View.VISIBLE);
            account_follow.hide();
            doAction = action.NOTHING;
        } else if (relationship.isFollowing()) {
            account_follow.setImageResource(R.drawable.ic_user_times);
            doAction = action.UNFOLLOW;
            account_follow.show();
        } else if (!relationship.isFollowing()) {
            account_follow.setImageResource(R.drawable.ic_user_plus);
            doAction = action.FOLLOW;
            account_follow.show();
        } else {
            account_follow.hide();
            doAction = action.NOTHING;
        }
    }

    /**
     * Pager adapter for the 4 fragments
     */
    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {

        ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Bundle bundle = new Bundle();
            switch (position) {
                case 0:
                    if (!peertubeAccount) {
                        TabLayoutTootsFragment tabLayoutTootsFragment = new TabLayoutTootsFragment();
                        bundle.putString("targetedId", account.getId());
                        tabLayoutTootsFragment.setArguments(bundle);
                        return tabLayoutTootsFragment;
                    } else {
                        DisplayStatusFragment displayStatusFragment = new DisplayStatusFragment();
                        bundle = new Bundle();
                        bundle.putSerializable("type", RetrieveFeedsAsyncTask.Type.USER);
                        bundle.putString("targetedId", account.getId());
                        bundle.putBoolean("showReply", false);
                        displayStatusFragment.setArguments(bundle);
                        return displayStatusFragment;
                    }
                case 1:
                    if (peertubeAccount) {
                        DisplayAccountsFragment displayAccountsFragment = new DisplayAccountsFragment();
                        bundle.putSerializable("type", RetrieveAccountsAsyncTask.Type.CHANNELS);
                        bundle.putString("targetedId", account.getId());
                        bundle.putString("instance", account.getAcct().split("@")[1]);
                        bundle.putString("name", account.getAcct().split("@")[0]);
                        displayAccountsFragment.setArguments(bundle);
                        return displayAccountsFragment;
                    } else {
                        DisplayAccountsFragment displayAccountsFragment = new DisplayAccountsFragment();
                        bundle.putSerializable("type", RetrieveAccountsAsyncTask.Type.FOLLOWING);
                        bundle.putString("targetedId", account.getId());
                        displayAccountsFragment.setArguments(bundle);
                        return displayAccountsFragment;
                    }

                case 2:
                    DisplayAccountsFragment displayAccountsFragment = new DisplayAccountsFragment();
                    bundle.putSerializable("type", RetrieveAccountsAsyncTask.Type.FOLLOWERS);
                    bundle.putString("targetedId", account.getId());
                    displayAccountsFragment.setArguments(bundle);
                    return displayAccountsFragment;

            }
            return null;
        }


        @Override
        public int getCount() {
            if (peertubeAccount)
                return 2;
            else
                return 3;
        }
    }

    @Override
    public void onRetrieveEmojiAccount(Account account) {
        account_note.setText(account.getNote(), TextView.BufferType.SPANNABLE);
        account_dn.setText(account.getdisplayNameSpan(), TextView.BufferType.SPANNABLE);
        ;
        if (account.getFieldsSpan() != null && account.getFieldsSpan().size() > 0) {
            HashMap<SpannableString, SpannableString> fieldsSpan = account.getFieldsSpan();
            Iterator it = fieldsSpan.entrySet().iterator();
            int i = 1;
            LinearLayout fields_container = findViewById(R.id.fields_container);
            if (fields_container != null)
                fields_container.setVisibility(View.VISIBLE);
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                SpannableString label = (SpannableString) pair.getKey();
                SpannableString value = (SpannableString) pair.getValue();
                LinearLayout field;
                TextView labelView;
                TextView valueView;
                switch (i) {
                    case 1:
                        field = findViewById(R.id.field1);
                        labelView = findViewById(R.id.label1);
                        valueView = findViewById(R.id.value1);
                        break;
                    case 2:
                        field = findViewById(R.id.field2);
                        labelView = findViewById(R.id.label2);
                        valueView = findViewById(R.id.value2);
                        break;
                    case 3:
                        field = findViewById(R.id.field3);
                        labelView = findViewById(R.id.label3);
                        valueView = findViewById(R.id.value3);
                        break;
                    case 4:
                        field = findViewById(R.id.field4);
                        labelView = findViewById(R.id.label4);
                        valueView = findViewById(R.id.value4);
                        break;
                    default:
                        field = findViewById(R.id.field1);
                        labelView = findViewById(R.id.label1);
                        valueView = findViewById(R.id.value1);
                        break;
                }
                if (field != null && labelView != null && valueView != null) {
                    field.setVisibility(View.VISIBLE);
                    valueView.setText(value, TextView.BufferType.SPANNABLE);
                    valueView.setMovementMethod(LinkMovementMethod.getInstance());
                    labelView.setText(label);
                }
                i++;
            }
        }
    }

    private void showMenu(View account_menu) {
        if (account == null)
            return;
        final PopupMenu popup = new PopupMenu(ShowAccountActivity.this, account_menu);
        popup.getMenuInflater()
                .inflate(R.menu.main_showaccount, popup.getMenu());

        final String[] stringArrayConf;
        final boolean isOwner = account.getId().equals(userId);
        String[] splitAcct = account.getAcct().split("@");

        if (splitAcct.length <= 1) {
            popup.getMenu().findItem(R.id.action_follow_instance).setVisible(false);
            popup.getMenu().findItem(R.id.action_block_instance).setVisible(false);

        }
        if (isOwner) {
            popup.getMenu().findItem(R.id.action_block).setVisible(false);
            popup.getMenu().findItem(R.id.action_mute).setVisible(false);
            popup.getMenu().findItem(R.id.action_mention).setVisible(false);
            popup.getMenu().findItem(R.id.action_follow_instance).setVisible(false);
            popup.getMenu().findItem(R.id.action_block_instance).setVisible(false);
            popup.getMenu().findItem(R.id.action_hide_boost).setVisible(false);
            popup.getMenu().findItem(R.id.action_endorse).setVisible(false);
            popup.getMenu().findItem(R.id.action_direct_message).setVisible(false);
            stringArrayConf = getResources().getStringArray(R.array.more_action_owner_confirm);
        } else {
            popup.getMenu().findItem(R.id.action_block).setVisible(true);
            popup.getMenu().findItem(R.id.action_mute).setVisible(true);
            popup.getMenu().findItem(R.id.action_mention).setVisible(true);
            stringArrayConf = getResources().getStringArray(R.array.more_action_confirm);
        }
        if (peertubeAccount) {
            popup.getMenu().findItem(R.id.action_hide_boost).setVisible(false);
            popup.getMenu().findItem(R.id.action_endorse).setVisible(false);
            popup.getMenu().findItem(R.id.action_direct_message).setVisible(false);
        }
        if (relationship != null) {
            if (!relationship.isFollowing()) {
                popup.getMenu().findItem(R.id.action_hide_boost).setVisible(false);
                popup.getMenu().findItem(R.id.action_endorse).setVisible(false);
            }
            if (relationship.isEndorsed()) {
                popup.getMenu().findItem(R.id.action_endorse).setTitle(R.string.unendorse);
            } else {
                popup.getMenu().findItem(R.id.action_endorse).setTitle(R.string.endorse);
            }
            if (relationship.isShowing_reblogs()) {
                popup.getMenu().findItem(R.id.action_hide_boost).setTitle(getString(R.string.hide_boost, account.getUsername()));
            } else {
                popup.getMenu().findItem(R.id.action_hide_boost).setTitle(getString(R.string.show_boost, account.getUsername()));
            }
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                AlertDialog.Builder builderInner;
                SharedPreferences sharedpreferences = getSharedPreferences(Helper.APP_PREFS, android.content.Context.MODE_PRIVATE);
                int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
                int style;
                if (theme == Helper.THEME_DARK) {
                    style = R.style.DialogDark;
                } else if (theme == Helper.THEME_BLACK) {
                    style = R.style.DialogBlack;
                } else {
                    style = R.style.Dialog;
                }
                switch (item.getItemId()) {
                    case R.id.action_follow_instance:
                        String finalInstanceName = splitAcct[1];
                        final SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                        List<RemoteInstance> remoteInstances = new InstancesDAO(ShowAccountActivity.this, db).getInstanceByName(finalInstanceName);
                        if (remoteInstances != null && remoteInstances.size() > 0) {
                            Toasty.info(getApplicationContext(), getString(R.string.toast_instance_already_added), Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putInt(INTENT_ACTION, SEARCH_INSTANCE);
                            bundle.putString(INSTANCE_NAME, finalInstanceName);
                            intent.putExtras(bundle);
                            startActivity(intent);
                            return true;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (!peertubeAccount) {
                                        //Here we can't know if the instance is a Mastodon one or not
                                        try { //Testing Mastodon
                                            new HttpsConnection(ShowAccountActivity.this).get("https://" + finalInstanceName + "/api/v1/timelines/public?local=true", 10, null, null);
                                        } catch (Exception ignored) {
                                            new HttpsConnection(ShowAccountActivity.this).get("https://" + finalInstanceName + "/api/v1/videos/", 10, null, null);
                                            peertubeAccount = true;
                                        }
                                    } else
                                        new HttpsConnection(ShowAccountActivity.this).get("https://" + finalInstanceName + "/api/v1/videos/", 10, null, null);

                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            final SQLiteDatabase db = Sqlite.getInstance(getApplicationContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                                            if (!peertubeAccount)
                                                new InstancesDAO(ShowAccountActivity.this, db).insertInstance(finalInstanceName, "MASTODON");
                                            else
                                                new InstancesDAO(ShowAccountActivity.this, db).insertInstance(finalInstanceName, "PEERTUBE");
                                            Toasty.success(getApplicationContext(), getString(R.string.toast_instance_followed), Toast.LENGTH_LONG).show();
                                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                            Bundle bundle = new Bundle();
                                            bundle.putInt(INTENT_ACTION, SEARCH_INSTANCE);
                                            bundle.putString(INSTANCE_NAME, finalInstanceName);
                                            intent.putExtras(bundle);
                                            startActivity(intent);
                                        }
                                    });
                                } catch (final Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            Toasty.warning(getApplicationContext(), getString(R.string.toast_instance_unavailable), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                        }).start();
                        return true;
                    case R.id.action_endorse:
                        if (relationship != null)
                            if (relationship.isEndorsed()) {
                                new PostActionAsyncTask(getApplicationContext(), API.StatusAction.UNENDORSE, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            } else {
                                new PostActionAsyncTask(getApplicationContext(), API.StatusAction.ENDORSE, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        return true;
                    case R.id.action_hide_boost:
                        if (relationship != null)
                            if (relationship.isShowing_reblogs()) {
                                new PostActionAsyncTask(getApplicationContext(), API.StatusAction.HIDE_BOOST, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            } else {
                                new PostActionAsyncTask(getApplicationContext(), API.StatusAction.SHOW_BOOST, account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        return true;
                    case R.id.action_direct_message:
                        Intent intent = new Intent(getApplicationContext(), TootActivity.class);
                        Bundle b = new Bundle();
                        b.putString("mentionAccount", account.getAcct());
                        b.putString("visibility", "direct");
                        intent.putExtras(b);
                        startActivity(intent);
                        return true;
                    case R.id.action_open_browser:
                        if (accountUrl != null) {
                            if (!accountUrl.startsWith("http://") && !accountUrl.startsWith("https://"))
                                accountUrl = "http://" + accountUrl;
                            Helper.openBrowser(ShowAccountActivity.this, accountUrl);
                        }
                        return true;
                    case R.id.action_mention:
                        intent = new Intent(getApplicationContext(), TootActivity.class);
                        b = new Bundle();
                        b.putString("mentionAccount", account.getAcct());
                        intent.putExtras(b);
                        startActivity(intent);
                        return true;
                    case R.id.action_mute:
                        builderInner = new AlertDialog.Builder(ShowAccountActivity.this, style);
                        builderInner.setTitle(stringArrayConf[0]);
                        doActionAccount = API.StatusAction.MUTE;
                        break;
                    case R.id.action_block:
                        builderInner = new AlertDialog.Builder(ShowAccountActivity.this, style);
                        builderInner.setTitle(stringArrayConf[1]);
                        doActionAccount = API.StatusAction.BLOCK;
                        break;
                    case R.id.action_block_instance:
                        builderInner = new AlertDialog.Builder(ShowAccountActivity.this, style);
                        doActionAccount = API.StatusAction.BLOCK_DOMAIN;
                        String domain = account.getAcct().split("@")[1];
                        builderInner.setMessage(getString(R.string.block_domain_confirm_message, domain));
                        break;
                    default:
                        return true;
                }
                builderInner.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builderInner.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String targetedId;
                        if (item.getItemId() == R.id.action_block_instance) {
                            targetedId = account.getAcct().split("@")[1];
                        } else {
                            targetedId = account.getId();
                        }
                        new PostActionAsyncTask(getApplicationContext(), doActionAccount, targetedId, ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        dialog.dismiss();
                    }
                });
                builderInner.show();
                return true;
            }
        });
        popup.show();
    }

    @Override
    public void onPostAction(int statusCode, API.StatusAction statusAction, String targetedId, Error error) {
        if (error != null) {
            Toasty.error(getApplicationContext(), error.getError(), Toast.LENGTH_LONG).show();
            return;
        }
        Helper.manageMessageStatusCode(getApplicationContext(), statusCode, statusAction);
        new RetrieveRelationshipAsyncTask(getApplicationContext(), account.getId(), ShowAccountActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    @Override
    public void onRetrieveAccount(final Account account, Error error) {
        if (error != null || account.getAcct() == null) {
            if (error == null)
                Toasty.error(ShowAccountActivity.this, getString(R.string.toast_error), Toast.LENGTH_LONG).show();
            else
                Toasty.error(ShowAccountActivity.this, error.getError(), Toast.LENGTH_LONG).show();
            return;
        }
        this.account = account;
        ManageAccount();
    }


    public boolean showReplies() {
        return show_replies;
    }

    public boolean showBoosts() {
        return show_boosts;
    }

}
