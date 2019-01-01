package fr.gouv.etalab.mastodon.drawers;
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.stom79.mytransl.MyTransL;
import com.github.stom79.mytransl.client.HttpsConnectionException;
import com.github.stom79.mytransl.client.Results;
import com.github.stom79.mytransl.translate.Translate;
import com.varunest.sparkbutton.SparkButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.activities.BaseMainActivity;
import fr.gouv.etalab.mastodon.activities.MediaActivity;
import fr.gouv.etalab.mastodon.activities.ShowAccountActivity;
import fr.gouv.etalab.mastodon.activities.ShowConversationActivity;
import fr.gouv.etalab.mastodon.activities.TootActivity;
import fr.gouv.etalab.mastodon.activities.TootInfoActivity;
import fr.gouv.etalab.mastodon.asynctasks.PostActionAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Application;
import fr.gouv.etalab.mastodon.client.Entities.Attachment;
import fr.gouv.etalab.mastodon.client.Entities.Card;
import fr.gouv.etalab.mastodon.client.Entities.Emojis;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.Entities.TagTimeline;
import fr.gouv.etalab.mastodon.fragments.DisplayStatusFragment;
import fr.gouv.etalab.mastodon.helper.CrossActions;
import fr.gouv.etalab.mastodon.helper.CustomTextView;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnPostActionInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveCardInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveEmojiInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveRepliesInterface;
import fr.gouv.etalab.mastodon.jobs.ScheduledBoostsSyncJob;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;
import fr.gouv.etalab.mastodon.sqlite.StatusCacheDAO;
import fr.gouv.etalab.mastodon.sqlite.StatusStoredDAO;
import fr.gouv.etalab.mastodon.sqlite.TempMuteDAO;

import static fr.gouv.etalab.mastodon.activities.MainActivity.currentLocale;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_BLACK;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_DARK;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_LIGHT;
import static fr.gouv.etalab.mastodon.helper.Helper.changeDrawableColor;
import static fr.gouv.etalab.mastodon.helper.Helper.getLiveInstance;


/**
 * Created by Thomas on 24/04/2017.
 * Adapter for Status
 */
public class StatusListAdapter extends RecyclerView.Adapter implements OnPostActionInterface, OnRetrieveFeedsInterface, OnRetrieveEmojiInterface, OnRetrieveRepliesInterface, OnRetrieveCardInterface {

    private Context context;
    private List<Status> statuses;
    private LayoutInflater layoutInflater;
    private boolean isOnWifi;
    private StatusListAdapter statusListAdapter;
    private RetrieveFeedsAsyncTask.Type type;
    private String targetedId;
    private final int HIDDEN_STATUS = 0;
    private static final int DISPLAYED_STATUS = 1;
    static final int FOCUSED_STATUS = 2;
    private static final int COMPACT_STATUS = 3;
    private int conversationPosition;
    private List<String> timedMute;
    private boolean redraft;
    private Status toot;
    private TagTimeline tagTimeline;

    public StatusListAdapter(Context context, RetrieveFeedsAsyncTask.Type type, String targetedId, boolean isOnWifi, List<Status> statuses) {
        super();
        this.context = context;
        this.statuses = statuses;
        this.isOnWifi = isOnWifi;
        layoutInflater = LayoutInflater.from(this.context);
        statusListAdapter = this;
        this.type = type;
        this.targetedId = targetedId;
        redraft = false;
    }

    public StatusListAdapter(Context context, TagTimeline tagTimeline, String targetedId, boolean isOnWifi, List<Status> statuses) {
        super();
        this.context = context;
        this.statuses = statuses;
        this.isOnWifi = isOnWifi;
        layoutInflater = LayoutInflater.from(this.context);
        statusListAdapter = this;
        this.type = RetrieveFeedsAsyncTask.Type.TAG;
        this.targetedId = targetedId;
        redraft = false;
        this.tagTimeline = tagTimeline;
    }

    public StatusListAdapter(Context context, int position, String targetedId, boolean isOnWifi, List<Status> statuses) {
        this.context = context;
        this.statuses = statuses;
        this.isOnWifi = isOnWifi;
        layoutInflater = LayoutInflater.from(this.context);
        statusListAdapter = this;
        this.type = RetrieveFeedsAsyncTask.Type.CONTEXT;
        this.conversationPosition = position;
        this.targetedId = targetedId;
        redraft = false;
    }

    public void updateMuted(List<String> timedMute) {
        this.timedMute = timedMute;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return statuses.size();
    }

    private Status getItemAt(int position) {
        if (statuses.size() > position)
            return statuses.get(position);
        else
            return null;
    }

    @Override
    public void onRetrieveReplies(APIResponse apiResponse) {
        if (apiResponse.getError() != null || apiResponse.getStatuses() == null || apiResponse.getStatuses().size() == 0) {
            return;
        }
        List<Status> modifiedStatus = apiResponse.getStatuses();
        notifyStatusChanged(modifiedStatus.get(0));
    }


    private class ViewHolderEmpty extends RecyclerView.ViewHolder {
        ViewHolderEmpty(View itemView) {
            super(itemView);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (type != RetrieveFeedsAsyncTask.Type.ART && (tagTimeline == null || !tagTimeline.isART()) && (holder.getItemViewType() == DISPLAYED_STATUS || holder.getItemViewType() == COMPACT_STATUS)) {
            final ViewHolder viewHolder = (ViewHolder) holder;
            // Bug workaround for losing text selection ability, see:
            // https://code.google.com/p/android/issues/detail?id=208169
            viewHolder.status_content.setEnabled(false);
            viewHolder.status_content.setEnabled(true);
            viewHolder.status_spoiler.setEnabled(false);
            viewHolder.status_spoiler.setEnabled(true);
        }

    }


    private class ViewHolderArt extends RecyclerView.ViewHolder {
        ImageView art_media, art_pp;
        TextView art_username, art_acct;
        LinearLayout art_author;

        ViewHolderArt(View itemView) {
            super(itemView);
            art_media = itemView.findViewById(R.id.art_media);
            art_pp = itemView.findViewById(R.id.art_pp);
            art_username = itemView.findViewById(R.id.art_username);
            art_acct = itemView.findViewById(R.id.art_acct);
            art_author = itemView.findViewById(R.id.art_author);
        }
    }


    class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout status_content_container;
        LinearLayout status_spoiler_container;
        CustomTextView status_spoiler;
        Button status_spoiler_button;
        CustomTextView status_content;
        TextView status_content_translated;
        LinearLayout status_content_translated_container;
        TextView status_account_username;
        TextView status_account_displayname, status_account_displayname_owner;
        ImageView status_account_profile;
        ImageView status_account_profile_boost;
        ImageView status_account_profile_boost_by;
        TextView status_favorite_count;
        TextView status_reblog_count;
        TextView status_toot_date;
        Button status_show_more;
        ImageView status_more;
        LinearLayout status_document_container;
        RelativeLayout status_horizontal_document_container;
        ImageView status_prev1;
        ImageView status_prev2;
        ImageView status_prev3;
        ImageView status_prev4;
        ImageView status_prev1_play;
        ImageView status_prev2_play;
        ImageView status_prev3_play;
        ImageView status_prev4_play;
        ImageView status_prev1_h;
        ImageView status_prev2_h;
        ImageView status_prev3_h;
        ImageView status_prev4_h;
        LinearLayout conversation_pp_2_container, conversation_pp_3_container;
        ImageView status_prev1_play_h;
        ImageView status_prev2_play_h;
        ImageView status_prev3_play_h;
        ImageView status_prev4_play_h;
        ImageView conversation_pp_1;
        ImageView conversation_pp_2;
        ImageView conversation_pp_3;
        ImageView conversation_pp_4;
        LinearLayout conversation_pp;
        LinearLayout vertical_content;
        RelativeLayout status_prev4_container;
        TextView status_reply;
        ImageView status_pin;
        ImageView status_privacy;
        ImageButton status_translate, status_bookmark;
        LinearLayout status_container2;
        LinearLayout status_container3;
        LinearLayout main_container;
        TextView yandex_translate;
        LinearLayout status_action_container;
        Button fetch_more;
        ImageView new_element;
        LinearLayout status_spoiler_mention_container;
        TextView status_mention_spoiler;
        LinearLayout status_cardview;
        ImageView status_cardview_image;
        TextView status_cardview_title, status_cardview_content, status_cardview_url;
        FrameLayout status_cardview_video;
        WebView status_cardview_webview;
        ImageView hide_preview, hide_preview_h;
        TextView status_toot_app;
        RelativeLayout webview_preview;
        ImageView webview_preview_card;
        LinearLayout left_buttons;
        Button status_show_more_content;
        SparkButton spark_button_fav, spark_button_reblog;
        RelativeLayout horizontal_second_image;

        public View getView() {
            return itemView;
        }

        ViewHolder(View itemView) {
            super(itemView);
            fetch_more = itemView.findViewById(R.id.fetch_more);
            webview_preview_card = itemView.findViewById(R.id.webview_preview_card);
            webview_preview = itemView.findViewById(R.id.webview_preview);
            status_horizontal_document_container = itemView.findViewById(R.id.status_horizontal_document_container);
            status_document_container = itemView.findViewById(R.id.status_document_container);
            status_horizontal_document_container = itemView.findViewById(R.id.status_horizontal_document_container);
            status_content = itemView.findViewById(R.id.status_content);
            status_content_translated = itemView.findViewById(R.id.status_content_translated);
            status_account_username = itemView.findViewById(R.id.status_account_username);
            status_account_displayname = itemView.findViewById(R.id.status_account_displayname);
            status_account_displayname_owner = itemView.findViewById(R.id.status_account_displayname_owner);
            status_account_profile = itemView.findViewById(R.id.status_account_profile);
            status_account_profile_boost = itemView.findViewById(R.id.status_account_profile_boost);
            status_account_profile_boost_by = itemView.findViewById(R.id.status_account_profile_boost_by);
            status_favorite_count = itemView.findViewById(R.id.status_favorite_count);
            status_reblog_count = itemView.findViewById(R.id.status_reblog_count);
            status_pin = itemView.findViewById(R.id.status_pin);
            status_toot_date = itemView.findViewById(R.id.status_toot_date);
            status_show_more = itemView.findViewById(R.id.status_show_more);
            status_more = itemView.findViewById(R.id.status_more);
            status_prev1 = itemView.findViewById(R.id.status_prev1);
            status_prev2 = itemView.findViewById(R.id.status_prev2);
            status_prev3 = itemView.findViewById(R.id.status_prev3);
            status_prev4 = itemView.findViewById(R.id.status_prev4);
            status_prev1_play = itemView.findViewById(R.id.status_prev1_play);
            status_prev2_play = itemView.findViewById(R.id.status_prev2_play);
            status_prev3_play = itemView.findViewById(R.id.status_prev3_play);
            status_prev4_play = itemView.findViewById(R.id.status_prev4_play);
            status_prev1_h = itemView.findViewById(R.id.status_prev1_h);
            status_prev2_h = itemView.findViewById(R.id.status_prev2_h);
            status_prev3_h = itemView.findViewById(R.id.status_prev3_h);
            status_prev4_h = itemView.findViewById(R.id.status_prev4_h);
            status_prev1_play_h = itemView.findViewById(R.id.status_prev1_play_h);
            status_prev2_play_h = itemView.findViewById(R.id.status_prev2_play_h);
            status_prev3_play_h = itemView.findViewById(R.id.status_prev3_play_h);
            status_prev4_play_h = itemView.findViewById(R.id.status_prev4_play_h);
            status_container2 = itemView.findViewById(R.id.status_container2);
            status_container3 = itemView.findViewById(R.id.status_container3);
            status_prev4_container = itemView.findViewById(R.id.status_prev4_container);
            status_reply = itemView.findViewById(R.id.status_reply);
            status_privacy = itemView.findViewById(R.id.status_privacy);
            status_translate = itemView.findViewById(R.id.status_translate);
            status_bookmark = itemView.findViewById(R.id.status_bookmark);
            status_content_translated_container = itemView.findViewById(R.id.status_content_translated_container);
            main_container = itemView.findViewById(R.id.main_container);
            status_spoiler_container = itemView.findViewById(R.id.status_spoiler_container);
            status_content_container = itemView.findViewById(R.id.status_content_container);
            status_spoiler = itemView.findViewById(R.id.status_spoiler);
            status_spoiler_button = itemView.findViewById(R.id.status_spoiler_button);
            yandex_translate = itemView.findViewById(R.id.yandex_translate);
            new_element = itemView.findViewById(R.id.new_element);
            status_action_container = itemView.findViewById(R.id.status_action_container);
            status_spoiler_mention_container = itemView.findViewById(R.id.status_spoiler_mention_container);
            status_mention_spoiler = itemView.findViewById(R.id.status_mention_spoiler);
            status_cardview = itemView.findViewById(R.id.status_cardview);
            status_cardview_image = itemView.findViewById(R.id.status_cardview_image);
            status_cardview_title = itemView.findViewById(R.id.status_cardview_title);
            status_cardview_content = itemView.findViewById(R.id.status_cardview_content);
            status_cardview_url = itemView.findViewById(R.id.status_cardview_url);
            status_cardview_video = itemView.findViewById(R.id.status_cardview_video);
            status_cardview_webview = itemView.findViewById(R.id.status_cardview_webview);
            hide_preview = itemView.findViewById(R.id.hide_preview);
            hide_preview_h = itemView.findViewById(R.id.hide_preview_h);
            status_toot_app = itemView.findViewById(R.id.status_toot_app);
            conversation_pp = itemView.findViewById(R.id.conversation_pp);
            conversation_pp_1 = itemView.findViewById(R.id.conversation_pp_1);
            conversation_pp_2 = itemView.findViewById(R.id.conversation_pp_2);
            conversation_pp_3 = itemView.findViewById(R.id.conversation_pp_3);
            conversation_pp_4 = itemView.findViewById(R.id.conversation_pp_4);
            conversation_pp_2_container = itemView.findViewById(R.id.conversation_pp_2_container);
            conversation_pp_3_container = itemView.findViewById(R.id.conversation_pp_3_container);
            vertical_content = itemView.findViewById(R.id.vertical_content);
            left_buttons = itemView.findViewById(R.id.left_buttons);
            status_show_more_content = itemView.findViewById(R.id.status_show_more_content);
            spark_button_fav = itemView.findViewById(R.id.spark_button_fav);
            spark_button_reblog = itemView.findViewById(R.id.spark_button_reblog);
            horizontal_second_image = itemView.findViewById(R.id.horizontal_second_image);
        }
    }

    public Status getItem(int position) {
        if (statuses.size() > position && position >= 0)
            return statuses.get(position);
        else return null;
    }

    @Override
    public int getItemViewType(int position) {

        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean isCompactMode = sharedpreferences.getBoolean(Helper.SET_COMPACT_MODE, false);
        if (type == RetrieveFeedsAsyncTask.Type.CONTEXT && position == conversationPosition)
            return FOCUSED_STATUS;
        else if (!Helper.filterToots(context, statuses.get(position), timedMute, type))
            return HIDDEN_STATUS;
        else
            return isCompactMode ? COMPACT_STATUS : DISPLAYED_STATUS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (type == RetrieveFeedsAsyncTask.Type.ART || (tagTimeline != null && tagTimeline.isART()))
            return new ViewHolderArt(layoutInflater.inflate(R.layout.drawer_art, parent, false));
        else if (viewType == DISPLAYED_STATUS)
            return new ViewHolder(layoutInflater.inflate(R.layout.drawer_status, parent, false));
        else if (viewType == COMPACT_STATUS)
            return new ViewHolder(layoutInflater.inflate(R.layout.drawer_status_compact, parent, false));
        else if (viewType == FOCUSED_STATUS)
            return new ViewHolder(layoutInflater.inflate(R.layout.drawer_status_focused, parent, false));
        else
            return new ViewHolderEmpty(layoutInflater.inflate(R.layout.drawer_empty, parent, false));
    }


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder viewHolder, int i) {
        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        final String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        if ((type == RetrieveFeedsAsyncTask.Type.ART || (tagTimeline != null && tagTimeline.isART())) && viewHolder.getItemViewType() != HIDDEN_STATUS) {
            final ViewHolderArt holder = (ViewHolderArt) viewHolder;
            final Status status = statuses.get(viewHolder.getAdapterPosition());

            if (!status.isClickable())
                Status.transform(context, status);
            if (!status.isEmojiFound())
                Status.makeEmojis(context, this, status);
            if (status.getArt_attachment() != null)
                Glide.with(context)
                        .load(status.getArt_attachment().getPreview_url())
                        .into(holder.art_media);
            if (status.getAccount() != null && status.getAccount().getAvatar() != null)
                Glide.with(context)
                        .load(status.getAccount().getAvatar())
                        .apply(new RequestOptions().transforms(new FitCenter(), new RoundedCorners(10)))
                        .into(holder.art_pp);
            holder.art_pp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ShowAccountActivity.class);
                    Bundle b = new Bundle();
                    b.putParcelable("account", status.getAccount());
                    intent.putExtras(b);
                    context.startActivity(intent);
                }
            });
            holder.art_media.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, MediaActivity.class);
                    Bundle b = new Bundle();
                    ArrayList<Attachment> attachments = new ArrayList<>();
                    attachments.add(status.getArt_attachment());
                    intent.putParcelableArrayListExtra("mediaArray", attachments);
                    b.putInt("position", 0);
                    intent.putExtras(b);
                    context.startActivity(intent);
                }
            });
            holder.art_author.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ShowConversationActivity.class);
                    Bundle b = new Bundle();
                    b.putParcelable("status", status);
                    intent.putExtras(b);
                    if (type == RetrieveFeedsAsyncTask.Type.CONTEXT)
                        ((Activity) context).finish();
                    context.startActivity(intent);
                }
            });
            if (status.getDisplayNameSpan() != null && status.getDisplayNameSpan().toString().trim().length() > 0)
                holder.art_username.setText(status.getDisplayNameSpan(), TextView.BufferType.SPANNABLE);
            else
                holder.art_username.setText(status.getAccount().getUsername());

            holder.art_acct.setText(String.format("@%s", status.getAccount().getAcct()));

        } else if (viewHolder.getItemViewType() == DISPLAYED_STATUS || viewHolder.getItemViewType() == FOCUSED_STATUS || viewHolder.getItemViewType() == COMPACT_STATUS) {
            final ViewHolder holder = (ViewHolder) viewHolder;
            final Status status = statuses.get(viewHolder.getAdapterPosition());

            if (status == null)
                return;
            status.setItemViewType(viewHolder.getItemViewType());

            boolean displayBookmarkButton = sharedpreferences.getBoolean(Helper.SET_SHOW_BOOKMARK, false);
            boolean fullAttachement = sharedpreferences.getBoolean(Helper.SET_FULL_PREVIEW, false);
            boolean isCompactMode = sharedpreferences.getBoolean(Helper.SET_COMPACT_MODE, false);
            int iconSizePercent = sharedpreferences.getInt(Helper.SET_ICON_SIZE, 130);
            int textSizePercent = sharedpreferences.getInt(Helper.SET_TEXT_SIZE, 110);
            final boolean trans_forced = sharedpreferences.getBoolean(Helper.SET_TRANS_FORCED, false);
            int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
            boolean expand_cw = sharedpreferences.getBoolean(Helper.SET_EXPAND_CW, false);
            boolean expand_media = sharedpreferences.getBoolean(Helper.SET_EXPAND_MEDIA, false);
            boolean display_card = sharedpreferences.getBoolean(Helper.SET_DISPLAY_CARD, false);
            boolean display_video_preview = sharedpreferences.getBoolean(Helper.SET_DISPLAY_VIDEO_PREVIEWS, true);
            int truncate_toots_size = sharedpreferences.getInt(Helper.SET_TRUNCATE_TOOTS_SIZE, 0);
            final int timeout = sharedpreferences.getInt(Helper.SET_NSFW_TIMEOUT, 5);
            boolean share_details = sharedpreferences.getBoolean(Helper.SET_SHARE_DETAILS, true);
            boolean confirmFav = sharedpreferences.getBoolean(Helper.SET_NOTIF_VALIDATION_FAV, false);
            boolean confirmBoost = sharedpreferences.getBoolean(Helper.SET_NOTIF_VALIDATION, true);
            int translator = sharedpreferences.getInt(Helper.SET_TRANSLATOR, Helper.TRANS_YANDEX);
            int behaviorWithAttachments = sharedpreferences.getInt(Helper.SET_ATTACHMENT_ACTION, Helper.ATTACHMENT_ALWAYS);
            if (type != RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE && !isCompactMode && displayBookmarkButton)
                holder.status_bookmark.setVisibility(View.VISIBLE);
            else
                holder.status_bookmark.setVisibility(View.GONE);

            holder.status_reply.setText("");
            //Display a preview for accounts that have replied *if enabled and only for home timeline*


            final SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            Status statusBookmarked = new StatusCacheDAO(context, db).getStatus(StatusCacheDAO.BOOKMARK_CACHE, status.getId());
            if (statusBookmarked != null)
                status.setBookmarked(true);
            else
                status.setBookmarked(false);

            if (status.isNew())
                holder.new_element.setVisibility(View.VISIBLE);
            else
                holder.new_element.setVisibility(View.GONE);


            holder.status_more.getLayoutParams().height = (int) Helper.convertDpToPixel((20 * iconSizePercent / 100), context);
            holder.status_more.getLayoutParams().width = (int) Helper.convertDpToPixel((20 * iconSizePercent / 100), context);
            holder.status_privacy.getLayoutParams().height = (int) Helper.convertDpToPixel((20 * iconSizePercent / 100), context);
            holder.status_privacy.getLayoutParams().width = (int) Helper.convertDpToPixel((20 * iconSizePercent / 100), context);


            if (isCompactMode && type == RetrieveFeedsAsyncTask.Type.CONTEXT && getItemViewType(viewHolder.getAdapterPosition()) != FOCUSED_STATUS && viewHolder.getAdapterPosition() != 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins((int) Helper.convertDpToPixel(25, context), 0, 0, 0);
                holder.main_container.setLayoutParams(params);
            } else if (isCompactMode && type == RetrieveFeedsAsyncTask.Type.CONTEXT && getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS && viewHolder.getAdapterPosition() != 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins((int) Helper.convertDpToPixel(20, context), 0, 0, 0);
                holder.main_container.setLayoutParams(params);
            }


            if (getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS) {
                holder.status_content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * textSizePercent / 100);
                holder.status_account_displayname.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * textSizePercent / 100);
                holder.status_account_username.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);
                holder.status_toot_date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);
                holder.status_content_translated.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * textSizePercent / 100);
            } else {
                holder.status_account_displayname.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);
                holder.status_account_username.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * textSizePercent / 100);
                holder.status_content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);
                holder.status_toot_date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * textSizePercent / 100);
                holder.status_content_translated.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);
            }

            holder.status_spoiler.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * textSizePercent / 100);

            switch (translator) {
                case Helper.TRANS_NONE:
                    holder.yandex_translate.setVisibility(View.GONE);
                    break;
                case Helper.TRANS_YANDEX:
                    holder.yandex_translate.setVisibility(View.VISIBLE);
                    break;
                default:
                    holder.yandex_translate.setVisibility(View.GONE);
            }

            //Manages theme for icon colors


            if (theme == Helper.THEME_BLACK)
                changeDrawableColor(context, R.drawable.ic_fiber_new, R.color.dark_icon);
            else
                changeDrawableColor(context, R.drawable.ic_fiber_new, R.color.mastodonC4);

            if (getItemViewType(viewHolder.getAdapterPosition()) == COMPACT_STATUS)
                holder.status_privacy.setVisibility(View.GONE);
            else
                holder.status_privacy.setVisibility(View.VISIBLE);


            changeDrawableColor(context, R.drawable.video_preview, R.color.white);
            if (theme == Helper.THEME_BLACK) {
                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_black);
                changeDrawableColor(context, holder.status_more, R.color.action_black);
                changeDrawableColor(context, holder.status_privacy, R.color.action_black);
                changeDrawableColor(context, R.drawable.ic_repeat, R.color.action_black);
                changeDrawableColor(context, R.drawable.ic_conversation, R.color.action_black);
                changeDrawableColor(context, R.drawable.ic_plus_one, R.color.action_black);
                changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_black);
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_black));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_black));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_black));


                changeDrawableColor(context, R.drawable.ic_photo, R.color.dark_text);
                changeDrawableColor(context, R.drawable.ic_remove_red_eye, R.color.dark_text);
                changeDrawableColor(context, R.drawable.ic_repeat_head_toot, R.color.black_text_toot_header);

                changeDrawableColor(context, R.drawable.ic_fetch_more, R.color.dark_icon);
                holder.status_cardview_title.setTextColor(ContextCompat.getColor(context, R.color.black_text_toot_header));
                holder.status_cardview_content.setTextColor(ContextCompat.getColor(context, R.color.dark_icon));
                holder.status_cardview_url.setTextColor(ContextCompat.getColor(context, R.color.black_text_toot_header));

                changeDrawableColor(context, R.drawable.ic_bookmark, R.color.black);
                changeDrawableColor(context, R.drawable.ic_bookmark_border, R.color.black);
                changeDrawableColor(context, R.drawable.ic_translate, R.color.black);
                holder.status_cardview.setBackgroundResource(R.drawable.card_border_black);
            } else if (theme == Helper.THEME_DARK) {

                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_dark);
                changeDrawableColor(context, holder.status_more, R.color.action_dark);
                changeDrawableColor(context, R.drawable.ic_repeat, R.color.action_dark);
                changeDrawableColor(context, holder.status_privacy, R.color.action_dark);
                changeDrawableColor(context, R.drawable.ic_plus_one, R.color.action_dark);
                changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_dark);
                changeDrawableColor(context, R.drawable.ic_conversation, R.color.action_dark);
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_dark));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_dark));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_dark));

                changeDrawableColor(context, R.drawable.ic_repeat_head_toot, R.color.dark_text_toot_header);

                changeDrawableColor(context, R.drawable.ic_photo, R.color.mastodonC4);
                changeDrawableColor(context, R.drawable.ic_remove_red_eye, R.color.mastodonC4);
                changeDrawableColor(context, R.drawable.ic_fetch_more, R.color.mastodonC4);


                holder.status_cardview_title.setTextColor(ContextCompat.getColor(context, R.color.dark_text_toot_header));
                holder.status_cardview_content.setTextColor(ContextCompat.getColor(context, R.color.dark_icon));
                holder.status_cardview_url.setTextColor(ContextCompat.getColor(context, R.color.dark_text_toot_header));
                holder.status_cardview.setBackgroundResource(R.drawable.card_border_dark);
                changeDrawableColor(context, R.drawable.ic_bookmark, R.color.mastodonC1);
                changeDrawableColor(context, R.drawable.ic_bookmark_border, R.color.mastodonC1);
                changeDrawableColor(context, R.drawable.ic_translate, R.color.mastodonC1);
            } else {
                changeDrawableColor(context, R.drawable.ic_fetch_more, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_conversation, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_more_horiz, R.color.action_light);
                changeDrawableColor(context, holder.status_more, R.color.action_light);
                changeDrawableColor(context, holder.status_privacy, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_repeat, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_plus_one, R.color.action_light);
                changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_light);
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_light));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_light));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_light));

                holder.status_cardview.setBackgroundResource(R.drawable.card_border_light);
                changeDrawableColor(context, R.drawable.ic_photo, R.color.mastodonC4);
                changeDrawableColor(context, R.drawable.ic_remove_red_eye, R.color.mastodonC4);

                changeDrawableColor(context, R.drawable.ic_repeat_head_toot, R.color.action_light_header);


                holder.status_cardview_title.setTextColor(ContextCompat.getColor(context, R.color.light_black));
                holder.status_cardview_content.setTextColor(ContextCompat.getColor(context, R.color.light_black));
                holder.status_cardview_url.setTextColor(ContextCompat.getColor(context, R.color.light_black));

                changeDrawableColor(context, R.drawable.ic_bookmark, R.color.white);
                changeDrawableColor(context, R.drawable.ic_bookmark_border, R.color.white);
                changeDrawableColor(context, R.drawable.ic_translate, R.color.white);
            }
            if (theme == THEME_DARK) {
                holder.status_account_displayname.setTextColor(ContextCompat.getColor(context, R.color.dark_text_toot_header));
                holder.status_toot_date.setTextColor(ContextCompat.getColor(context, R.color.dark_text_toot_header));
            } else if (theme == THEME_BLACK) {
                holder.status_account_displayname.setTextColor(ContextCompat.getColor(context, R.color.black_text_toot_header));
                holder.status_toot_date.setTextColor(ContextCompat.getColor(context, R.color.black_text_toot_header));
            } else if (theme == THEME_LIGHT) {
                holder.status_account_displayname.setTextColor(ContextCompat.getColor(context, R.color.action_light_header));
                holder.status_toot_date.setTextColor(ContextCompat.getColor(context, R.color.light_black));
            }
            if (status.isBookmarked())
                holder.status_bookmark.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_bookmark));
            else
                holder.status_bookmark.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_border));


            //Redraws top icons (boost/reply)
            final float scale = context.getResources().getDisplayMetrics().density;
            holder.spark_button_fav.pressOnTouch(false);
            holder.spark_button_reblog.pressOnTouch(false);
            holder.spark_button_fav.setActiveImage(R.drawable.ic_star);
            holder.spark_button_fav.setInactiveImage(R.drawable.ic_star_border);
            holder.spark_button_fav.setDisableCircle(true);
            holder.spark_button_reblog.setDisableCircle(true);
            holder.spark_button_fav.setActiveImageTint(R.color.marked_icon);
            holder.spark_button_reblog.setActiveImageTint(R.color.boost_icon);
            if (theme == THEME_DARK) {
                holder.spark_button_fav.setInActiveImageTint(R.color.action_dark);
                holder.spark_button_reblog.setInActiveImageTint(R.color.action_dark);
            } else if (theme == THEME_BLACK) {
                holder.spark_button_fav.setInActiveImageTint(R.color.action_black);
                holder.spark_button_reblog.setInActiveImageTint(R.color.action_black);
            } else {
                holder.spark_button_fav.setInActiveImageTint(R.color.action_light);
                holder.spark_button_reblog.setInActiveImageTint(R.color.action_light);
            }
            holder.spark_button_fav.setColors(R.color.marked_icon, R.color.marked_icon);
            holder.spark_button_fav.setImageSize((int) (20 * iconSizePercent / 100 * scale + 0.5f));
            holder.spark_button_fav.setMinimumWidth((int) Helper.convertDpToPixel((20 * iconSizePercent / 100 * scale + 0.5f), context));

            holder.spark_button_reblog.setColors(R.color.boost_icon, R.color.boost_icon);
            holder.spark_button_reblog.setImageSize((int) (20 * iconSizePercent / 100 * scale + 0.5f));
            holder.spark_button_reblog.setMinimumWidth((int) Helper.convertDpToPixel((20 * iconSizePercent / 100 * scale + 0.5f), context));

            Drawable imgConversation = null;
            if (type != RetrieveFeedsAsyncTask.Type.CONTEXT && ((status.getIn_reply_to_account_id() != null && status.getIn_reply_to_account_id().equals(status.getAccount().getId()))
                    || (status.getReblog() != null && status.getReblog().getIn_reply_to_account_id() != null && status.getReblog().getIn_reply_to_account_id().equals(status.getReblog().getAccount().getId())))) {
                imgConversation = ContextCompat.getDrawable(context, R.drawable.ic_conversation);
                imgConversation.setBounds(0, 0, (int) (15 * iconSizePercent / 100 * scale + 0.5f), (int) (15 * iconSizePercent / 100 * scale + 0.5f));
            }
            if (status.getReblog() != null) {
                Drawable img = ContextCompat.getDrawable(context, R.drawable.ic_repeat_head_toot);
                assert img != null;
                img.setBounds(0, 0, (int) (20 * iconSizePercent / 100 * scale + 0.5f), (int) (15 * iconSizePercent / 100 * scale + 0.5f));
                holder.status_account_displayname.setCompoundDrawables(img, null, null, null);
                holder.status_account_displayname_owner.setCompoundDrawables(null, null, imgConversation, null);
            } else {
                holder.status_account_displayname.setCompoundDrawables(null, null, null, null);
                holder.status_account_displayname_owner.setCompoundDrawables(null, null, imgConversation, null);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            LinearLayout.LayoutParams paramsB = new LinearLayout.LayoutParams((int) Helper.convertDpToPixel(60, context), LinearLayout.LayoutParams.WRAP_CONTENT);
            if (status.getReblog() == null && !isCompactMode && getItemViewType(viewHolder.getAdapterPosition()) != FOCUSED_STATUS) {
                params.setMargins(0, -(int) Helper.convertDpToPixel(10, context), 0, 0);
                if (status.getSpoiler_text() != null && status.getSpoiler_text().trim().length() > 0)
                    paramsB.setMargins(0, (int) Helper.convertDpToPixel(10, context), 0, 0);
                else
                    paramsB.setMargins(0, (int) Helper.convertDpToPixel(15, context), 0, 0);
            } else if (!isCompactMode && getItemViewType(viewHolder.getAdapterPosition()) != FOCUSED_STATUS) {
                if (status.getContent() == null || status.getContent().trim().equals("")) {
                    params.setMargins(0, -(int) Helper.convertDpToPixel(20, context), 0, 0);
                    paramsB.setMargins(0, (int) Helper.convertDpToPixel(20, context), 0, 0);
                } else {
                    params.setMargins(0, 0, 0, 0);
                    paramsB.setMargins(0, 0, 0, 0);
                }

            }


            holder.vertical_content.setLayoutParams(params);
            holder.left_buttons.setLayoutParams(paramsB);
            if (!status.isClickable())
                Status.transform(context, status);
            if (!status.isEmojiFound())
                Status.makeEmojis(context, this, status);
            holder.status_content.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_UP && !view.hasFocus()) {
                        try {
                            view.requestFocus();
                        } catch (Exception ignored) {
                        }
                    }
                    return false;
                }
            });
            //Click on a conversation
            if ((getItemViewType(viewHolder.getAdapterPosition()) == DISPLAYED_STATUS || getItemViewType(viewHolder.getAdapterPosition()) == COMPACT_STATUS)) {
                holder.status_content.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (type != RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE) {
                            Intent intent = new Intent(context, ShowConversationActivity.class);
                            Bundle b = new Bundle();
                            if (status.getReblog() == null)
                                b.putParcelable("status", status);
                            else
                                b.putParcelable("status", status.getReblog());
                            intent.putExtras(b);
                            if (type == RetrieveFeedsAsyncTask.Type.CONTEXT)
                                ((Activity) context).finish();
                            context.startActivity(intent);
                        } else {
                            CrossActions.doCrossConversation(context, status);
                        }
                    }
                });
                holder.main_container.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (type != RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE) {
                            Intent intent = new Intent(context, ShowConversationActivity.class);
                            Bundle b = new Bundle();
                            if (status.getReblog() == null)
                                b.putParcelable("status", status);
                            else
                                b.putParcelable("status", status.getReblog());
                            intent.putExtras(b);
                            if (type == RetrieveFeedsAsyncTask.Type.CONTEXT)
                                ((Activity) context).finish();
                            context.startActivity(intent);
                        } else {
                            CrossActions.doCrossConversation(context, status);
                        }
                    }
                });
            }
            holder.status_content.setText(status.getContentSpan(), TextView.BufferType.SPANNABLE);

            if (truncate_toots_size > 0) {
                holder.status_content.setMaxLines(truncate_toots_size);
                if (status.getNumberLines() == -1) {
                    status.setNumberLines(-2);
                    holder.status_show_more_content.setVisibility(View.GONE);
                    holder.status_content.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setNumberLines(holder.status_content.getLineCount());
                            if (status.getNumberLines() > truncate_toots_size) {
                                notifyStatusChanged(status);
                            }
                        }
                    });
                } else if (status.getNumberLines() > truncate_toots_size) {
                    holder.status_show_more_content.setVisibility(View.VISIBLE);
                    if (status.isExpanded()) {
                        holder.status_content.setMaxLines(Integer.MAX_VALUE);
                        holder.status_show_more_content.setText(R.string.hide_toot_truncate);
                    } else {
                        holder.status_content.setMaxLines(truncate_toots_size);
                        holder.status_show_more_content.setText(R.string.display_toot_truncate);
                    }
                } else {
                    holder.status_show_more_content.setVisibility(View.GONE);
                }
            } else {
                holder.status_show_more_content.setVisibility(View.GONE);
            }
            holder.status_show_more_content.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    status.setExpanded(!status.isExpanded());
                    notifyStatusChanged(status);
                }
            });
            holder.status_spoiler.setText(status.getContentSpanCW(), TextView.BufferType.SPANNABLE);

            holder.status_content.setMovementMethod(LinkMovementMethod.getInstance());
            holder.status_spoiler.setMovementMethod(LinkMovementMethod.getInstance());

            holder.status_translate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    translateToot(status);
                }
            });

            holder.status_bookmark.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (type != RetrieveFeedsAsyncTask.Type.CACHE_BOOKMARKS) {
                        status.setBookmarked(!status.isBookmarked());
                        try {
                            if (status.isBookmarked()) {
                                new StatusCacheDAO(context, db).insertStatus(StatusCacheDAO.BOOKMARK_CACHE, status);
                                Toasty.success(context, context.getString(R.string.status_bookmarked), Toast.LENGTH_LONG).show();
                            } else {
                                new StatusCacheDAO(context, db).remove(StatusCacheDAO.BOOKMARK_CACHE, status);
                                Toasty.success(context, context.getString(R.string.status_unbookmarked), Toast.LENGTH_LONG).show();
                            }
                            notifyStatusChanged(status);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toasty.error(context, context.getString(R.string.toast_error), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        int position = 0;
                        for (Status statustmp : statuses) {
                            if (statustmp.getId().equals(status.getId())) {
                                statuses.remove(status);
                                statusListAdapter.notifyItemRemoved(position);
                                new StatusCacheDAO(context, db).remove(StatusCacheDAO.BOOKMARK_CACHE, statustmp);
                                Toasty.success(context, context.getString(R.string.status_unbookmarked), Toast.LENGTH_LONG).show();
                                break;
                            }
                            position++;
                        }
                    }
                }
            });
            holder.status_bookmark.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    CrossActions.doCrossBookmark(context, status, statusListAdapter);
                    return false;
                }
            });
            holder.status_content_translated.setMovementMethod(LinkMovementMethod.getInstance());
            //-------- END -> Manages translations

            if (status.getAccount() == null) {
                Account account = new AccountDAO(context, db).getAccountByID(userId);
                status.setAccount(account);
            }
            //Displays name & emoji in toot header
            final String ppurl;
            if (status.getReblog() != null) {
                ppurl = status.getReblog().getAccount().getAvatar();
                holder.status_account_displayname.setVisibility(View.VISIBLE);
                holder.status_account_displayname.setText(context.getResources().getString(R.string.reblog_by, status.getAccount().getUsername()));
                if (status.getReblog().getAccount().getDisplay_name().length() > 0)
                    holder.status_account_displayname_owner.setText(status.getDisplayNameSpan(), TextView.BufferType.SPANNABLE);
                else
                    holder.status_account_displayname_owner.setText(status.getReblog().getAccount().getAcct().replace("@", ""));
                holder.status_account_displayname_owner.setVisibility(View.VISIBLE);

            } else {
                ppurl = status.getAccount().getAvatar();
                holder.status_account_displayname.setVisibility(View.GONE);
                if (status.getAccount().getdisplayNameSpan() == null || status.getAccount().getdisplayNameSpan().toString().trim().length() == 0)
                    holder.status_account_displayname_owner.setText(status.getAccount().getUsername().replace("@", ""), TextView.BufferType.SPANNABLE);
                else
                    holder.status_account_displayname_owner.setText(status.getAccount().getdisplayNameSpan(), TextView.BufferType.SPANNABLE);
            }
            //-------- END -> Displays name & emoji in toot header

            //Change the color in gray for accounts in DARK Theme only
            Spannable wordtoSpan;
            Pattern hashAcct;
            if (status.getReblog() != null) {
                wordtoSpan = new SpannableString("@" + status.getReblog().getAccount().getAcct());
                hashAcct = Pattern.compile("(@" + status.getReblog().getAccount().getAcct() + ")");
            } else {
                wordtoSpan = new SpannableString("@" + status.getAccount().getAcct());
                hashAcct = Pattern.compile("(@" + status.getAccount().getAcct() + ")");
            }
            if (hashAcct != null) {
                Matcher matcherAcct = hashAcct.matcher(wordtoSpan);
                while (matcherAcct.find()) {
                    int matchStart = matcherAcct.start(1);
                    int matchEnd = matcherAcct.end();
                    if (wordtoSpan.length() >= matchEnd && matchStart < matchEnd) {
                        if (theme == THEME_LIGHT)
                            wordtoSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.action_light_header)), matchStart, matchEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                        else if (theme == THEME_DARK)
                            wordtoSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.dark_text_toot_header)), matchStart, matchEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                        else if (theme == THEME_BLACK)
                            wordtoSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.black_text_toot_header)), matchStart, matchEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    }

                }
            }
            holder.status_account_username.setText(wordtoSpan);

            //-------- END -> Change the color in gray for accounts in DARK Theme only

            if (status.isFetchMore()) {
                holder.fetch_more.setVisibility(View.VISIBLE);
                holder.fetch_more.setEnabled(true);
                holder.fetch_more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        status.setFetchMore(false);
                        holder.fetch_more.setEnabled(false);
                        holder.fetch_more.setVisibility(View.GONE);
                        DisplayStatusFragment homeFragment = ((BaseMainActivity) context).getHomeFragment();
                        if (homeFragment != null)
                            homeFragment.fetchMore(status.getId());
                    }
                });
            } else {
                holder.fetch_more.setVisibility(View.GONE);

            }


            holder.status_mention_spoiler.setText(Helper.makeMentionsClick(context, status.getMentions()), TextView.BufferType.SPANNABLE);
            holder.status_mention_spoiler.setMovementMethod(LinkMovementMethod.getInstance());

            if (getItemViewType(viewHolder.getAdapterPosition()) != COMPACT_STATUS) {
                if (status.getReblog() == null)
                    holder.status_favorite_count.setText(String.valueOf(status.getFavourites_count()));
                else
                    holder.status_favorite_count.setText(String.valueOf(status.getReblog().getFavourites_count()));
                if (status.getReblog() == null)
                    holder.status_reblog_count.setText(String.valueOf(status.getReblogs_count()));
                else
                    holder.status_reblog_count.setText(String.valueOf(status.getReblog().getReblogs_count()));
            }
            if (getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS) {
                String fullDate_tmp = Helper.dateDiffFull(status.getCreated_at());
                String fullDate = "";
                if (!fullDate_tmp.equals(""))
                    fullDate = fullDate_tmp.substring(0, 1).toUpperCase() + fullDate_tmp.substring(1);
                holder.status_toot_date.setText(fullDate);
            } else {
                holder.status_toot_date.setText(Helper.dateDiff(context, status.getCreated_at()));
                Helper.absoluteDateTimeReveal(context, holder.status_toot_date, status.getCreated_at());
            }

            if (status.getReblog() != null) {
                Helper.loadGiF(context, ppurl, holder.status_account_profile_boost);
                Helper.loadGiF(context, status.getAccount().getAvatar(), holder.status_account_profile_boost_by);
                holder.status_account_profile_boost.setVisibility(View.VISIBLE);
                holder.status_account_profile_boost_by.setVisibility(View.VISIBLE);
                holder.status_account_profile.setVisibility(View.GONE);
            } else {
                Helper.loadGiF(context, ppurl, holder.status_account_profile);
                holder.status_account_profile_boost.setVisibility(View.GONE);
                holder.status_account_profile_boost_by.setVisibility(View.GONE);
                holder.status_account_profile.setVisibility(View.VISIBLE);
            }
            if (type == RetrieveFeedsAsyncTask.Type.CONVERSATION && status.getConversationProfilePicture() != null) {
                holder.status_account_profile.setVisibility(View.GONE);
                holder.conversation_pp.setVisibility(View.VISIBLE);
                if (status.getConversationProfilePicture().size() == 1) {
                    holder.conversation_pp_1.setVisibility(View.VISIBLE);
                    holder.conversation_pp_1.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    holder.conversation_pp_2_container.setVisibility(View.GONE);
                    holder.conversation_pp_3_container.setVisibility(View.GONE);
                    holder.conversation_pp_2.setVisibility(View.GONE);
                    holder.conversation_pp_3.setVisibility(View.GONE);
                    holder.conversation_pp_4.setVisibility(View.GONE);
                    Glide.with(context)
                            .load(status.getConversationProfilePicture().get(0))
                            .apply(new RequestOptions().transforms(new FitCenter(), new RoundedCorners(10)))
                            .into(holder.conversation_pp_1);
                } else if (status.getConversationProfilePicture().size() == 2) {
                    holder.conversation_pp_2_container.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3_container.setVisibility(View.GONE);
                    holder.conversation_pp_1.setVisibility(View.VISIBLE);
                    holder.conversation_pp_2.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3.setVisibility(View.GONE);
                    holder.conversation_pp_4.setVisibility(View.GONE);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(0), holder.conversation_pp_1);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(1), holder.conversation_pp_2);
                } else if (status.getConversationProfilePicture().size() == 3) {
                    holder.conversation_pp_4.setVisibility(View.GONE);
                    holder.conversation_pp_1.setVisibility(View.VISIBLE);
                    holder.conversation_pp_2.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3.setVisibility(View.VISIBLE);
                    holder.conversation_pp_4.setVisibility(View.GONE);
                    holder.conversation_pp_2_container.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3_container.setVisibility(View.VISIBLE);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(0), holder.conversation_pp_1);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(1), holder.conversation_pp_2);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(2), holder.conversation_pp_3);
                } else if (status.getConversationProfilePicture().size() == 4) {
                    holder.conversation_pp_1.setVisibility(View.VISIBLE);
                    holder.conversation_pp_2.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3.setVisibility(View.VISIBLE);
                    holder.conversation_pp_4.setVisibility(View.VISIBLE);
                    holder.conversation_pp_2_container.setVisibility(View.VISIBLE);
                    holder.conversation_pp_3_container.setVisibility(View.VISIBLE);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(0), holder.conversation_pp_1);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(1), holder.conversation_pp_2);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(2), holder.conversation_pp_3);
                    Helper.loadGiF(context, status.getConversationProfilePicture().get(3), holder.conversation_pp_4);
                }
            }
            holder.status_action_container.setVisibility(View.VISIBLE);
            boolean differentLanguage = false;
            if (status.getReblog() == null)
                differentLanguage = status.getLanguage() != null && !status.getLanguage().trim().equals(currentLocale);
            else
                differentLanguage = status.getReblog().getLanguage() != null && !status.getReblog().getLanguage().trim().equals(currentLocale);

            if ((getItemViewType(viewHolder.getAdapterPosition()) != COMPACT_STATUS) && (trans_forced || (translator != Helper.TRANS_NONE && currentLocale != null && differentLanguage))) {
                if (status.getSpoiler_text() != null && status.getSpoiler_text().length() > 0) {
                    if (status.isSpoilerShown() || getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS) {
                        holder.status_translate.setVisibility(View.VISIBLE);
                    } else {
                        holder.status_translate.setVisibility(View.GONE);
                    }
                } else if (status.getReblog() != null && status.getReblog().getSpoiler_text() != null && status.getReblog().getSpoiler_text().length() > 0) {
                    if (status.isSpoilerShown() || getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS) {
                        holder.status_translate.setVisibility(View.VISIBLE);
                    } else {
                        holder.status_translate.setVisibility(View.GONE);
                    }
                } else {
                    holder.status_translate.setVisibility(View.VISIBLE);
                }
            } else {
                holder.status_translate.setVisibility(View.GONE);
            }
            if (expand_cw)
                holder.status_spoiler_button.setVisibility(View.GONE);
            if (status.getReblog() == null) {
                if (status.getSpoiler_text() != null && status.getSpoiler_text().trim().length() > 0) {
                    holder.status_spoiler_container.setVisibility(View.VISIBLE);
                    if (!status.isSpoilerShown() && !expand_cw) {
                        holder.status_content_container.setVisibility(View.GONE);
                        if (status.getMentions().size() > 0)
                            holder.status_spoiler_mention_container.setVisibility(View.VISIBLE);
                        else
                            holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler));
                    } else {
                        holder.status_content_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler_less));
                    }
                } else {
                    holder.status_spoiler_container.setVisibility(View.GONE);
                    holder.status_spoiler_mention_container.setVisibility(View.GONE);
                    holder.status_content_container.setVisibility(View.VISIBLE);
                }
            } else {
                if (status.getReblog().getSpoiler_text() != null && status.getReblog().getSpoiler_text().trim().length() > 0) {
                    holder.status_spoiler_container.setVisibility(View.VISIBLE);
                    if (!status.isSpoilerShown() && !expand_cw) {
                        holder.status_content_container.setVisibility(View.GONE);
                        if (status.getMentions().size() > 0)
                            holder.status_spoiler_mention_container.setVisibility(View.VISIBLE);
                        else
                            holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler));
                    } else {
                        holder.status_content_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler_less));
                    }
                } else {
                    holder.status_spoiler_container.setVisibility(View.GONE);
                    holder.status_spoiler_mention_container.setVisibility(View.GONE);
                    holder.status_content_container.setVisibility(View.VISIBLE);
                }
            }
            if (status.getReblog() == null) {
                if (status.getMedia_attachments().size() < 1) {
                    holder.status_horizontal_document_container.setVisibility(View.GONE);
                    holder.status_document_container.setVisibility(View.GONE);
                    holder.status_show_more.setVisibility(View.GONE);
                } else {
                    //If medias are loaded without any conditions or if device is on wifi
                    if (expand_media || !status.isSensitive() && (behaviorWithAttachments == Helper.ATTACHMENT_ALWAYS || (behaviorWithAttachments == Helper.ATTACHMENT_WIFI && isOnWifi))) {
                        loadAttachments(status, holder);
                        holder.status_show_more.setVisibility(View.GONE);
                        status.setAttachmentShown(true);
                    } else {
                        //Text depending if toots is sensitive or not
                        String textShowMore = (status.isSensitive()) ? context.getString(R.string.load_sensitive_attachment) : context.getString(R.string.load_attachment);
                        holder.status_show_more.setText(textShowMore);
                        if (!status.isAttachmentShown()) {
                            holder.status_show_more.setVisibility(View.VISIBLE);
                            if (fullAttachement)
                                holder.status_horizontal_document_container.setVisibility(View.GONE);
                            else
                                holder.status_document_container.setVisibility(View.GONE);
                        } else {
                            loadAttachments(status, holder);
                        }
                    }
                }
            } else { //Attachments for reblogs

                if (status.getReblog().getMedia_attachments().size() < 1) {
                    if (fullAttachement)
                        holder.status_horizontal_document_container.setVisibility(View.GONE);
                    else
                        holder.status_document_container.setVisibility(View.GONE);
                    holder.status_show_more.setVisibility(View.GONE);
                } else {
                    //If medias are loaded without any conditions or if device is on wifi
                    if (expand_media || !status.getReblog().isSensitive() && (behaviorWithAttachments == Helper.ATTACHMENT_ALWAYS || (behaviorWithAttachments == Helper.ATTACHMENT_WIFI && isOnWifi))) {
                        loadAttachments(status.getReblog(), holder);
                        holder.status_show_more.setVisibility(View.GONE);
                        status.setAttachmentShown(true);
                    } else {
                        //Text depending if toots is sensitive or not
                        String textShowMore = (status.getReblog().isSensitive()) ? context.getString(R.string.load_sensitive_attachment) : context.getString(R.string.load_attachment);
                        holder.status_show_more.setText(textShowMore);
                        if (!status.isAttachmentShown()) {
                            holder.status_show_more.setVisibility(View.VISIBLE);
                            if (fullAttachement)
                                holder.status_horizontal_document_container.setVisibility(View.GONE);
                            else
                                holder.status_document_container.setVisibility(View.GONE);
                        } else {
                            loadAttachments(status.getReblog(), holder);
                        }
                    }
                }
            }
            if (theme == Helper.THEME_BLACK) {
                changeDrawableColor(context, R.drawable.ic_photo, R.color.dark_text);
                changeDrawableColor(context, R.drawable.ic_more_toot_content, R.color.dark_text);
            } else {
                changeDrawableColor(context, R.drawable.ic_photo, R.color.mastodonC4);
                changeDrawableColor(context, R.drawable.ic_more_toot_content, R.color.mastodonC4);
            }
            if (!fullAttachement)
                holder.hide_preview.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        status.setAttachmentShown(!status.isAttachmentShown());
                        if (status.getReblog() != null)
                            status.getReblog().setSensitive(true);
                        else
                            status.setSensitive(true);
                        notifyStatusChanged(status);
                    }
                });
            else
                holder.hide_preview_h.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        status.setAttachmentShown(!status.isAttachmentShown());
                        if (status.getReblog() != null)
                            status.getReblog().setSensitive(true);
                        else
                            status.setSensitive(true);
                        notifyStatusChanged(status);
                    }
                });

            //Toot was translated and user asked to see it

            if (status.isTranslationShown() && status.getContentSpanTranslated() != null) {
                holder.status_content_translated.setText(status.getContentSpanTranslated(), TextView.BufferType.SPANNABLE);
                holder.status_content.setVisibility(View.GONE);
                holder.status_content_translated_container.setVisibility(View.VISIBLE);
            } else { //Toot is not translated
                holder.status_content.setVisibility(View.VISIBLE);
                holder.status_content_translated_container.setVisibility(View.GONE);
            }

            //TODO:It sounds that sometimes this value is null - need deeper investigation
            if (status.getVisibility() == null)
                status.setVisibility("public");

            switch (status.getVisibility()) {
                case "direct":
                    holder.status_reblog_count.setVisibility(View.GONE);
                    holder.spark_button_reblog.setVisibility(View.GONE);
                    break;
                case "private":
                    boolean isOwner = status.getAccount().getId().equals(userId);
                    if (isOwner) {
                        holder.status_reblog_count.setVisibility(View.VISIBLE);
                        holder.spark_button_reblog.setVisibility(View.VISIBLE);
                    } else {
                        holder.status_reblog_count.setVisibility(View.GONE);
                        holder.spark_button_reblog.setVisibility(View.GONE);
                    }
                    break;
                case "public":
                case "unlisted":
                    holder.status_reblog_count.setVisibility(View.VISIBLE);
                    holder.spark_button_reblog.setVisibility(View.VISIBLE);
                    break;
                default:
                    holder.status_reblog_count.setVisibility(View.VISIBLE);
                    holder.spark_button_reblog.setVisibility(View.VISIBLE);
            }

            switch (status.getVisibility()) {
                case "public":
                    holder.status_privacy.setImageResource(R.drawable.ic_public);
                    break;
                case "unlisted":
                    holder.status_privacy.setImageResource(R.drawable.ic_lock_open);
                    break;
                case "private":
                    holder.status_privacy.setImageResource(R.drawable.ic_lock_outline);
                    break;
                case "direct":
                    holder.status_privacy.setImageResource(R.drawable.ic_mail_outline);
                    break;
            }

            Drawable imgReply;

            if (!status.isFavAnimated()) {
                if (status.isFavourited() || (status.getReblog() != null && status.getReblog().isFavourited())) {
                    holder.spark_button_fav.setChecked(true);
                } else {
                    holder.spark_button_fav.setChecked(false);
                }
            } else {
                status.setFavAnimated(false);
                holder.spark_button_fav.setChecked(true);
                holder.spark_button_fav.setAnimationSpeed(1.0f);
                holder.spark_button_fav.playAnimation();
            }
            if (!status.isBoostAnimated()) {
                if (status.isReblogged() || (status.getReblog() != null && status.getReblog().isReblogged())) {
                    holder.spark_button_reblog.setChecked(true);
                } else {
                    holder.spark_button_reblog.setChecked(false);
                }
            } else {
                status.setBoostAnimated(false);
                holder.spark_button_reblog.setChecked(true);
                holder.spark_button_reblog.setAnimationSpeed(1.0f);
                holder.spark_button_reblog.playAnimation();
            }

            if (theme == THEME_DARK)
                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_dark);
            else if (theme == THEME_BLACK)
                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_black);
            else
                changeDrawableColor(context, R.drawable.ic_reply, R.color.action_light);
            imgReply = ContextCompat.getDrawable(context, R.drawable.ic_reply);


            assert imgReply != null;
            imgReply.setBounds(0, 0, (int) (20 * iconSizePercent / 100 * scale + 0.5f), (int) (20 * iconSizePercent / 100 * scale + 0.5f));


            if (isCompactMode && ((status.getReblog() == null && status.getReplies_count() > 1) || (status.getReblog() != null && status.getReblog().getReplies_count() > 1))) {
                Drawable img = context.getResources().getDrawable(R.drawable.ic_plus_one);
                holder.status_reply.setCompoundDrawablesWithIntrinsicBounds(imgReply, null, img, null);
            } else {
                holder.status_reply.setCompoundDrawablesWithIntrinsicBounds(imgReply, null, null, null);
            }
            if (isCompactMode) {
                if (((status.getReblog() == null && status.getReplies_count() == 1) || (status.getReblog() != null && status.getReblog().getReplies_count() == 1)))
                    holder.status_reply.setText(String.valueOf(status.getReblog() != null ? status.getReblog().getReplies_count() : status.getReplies_count()));
            } else {
                if (status.getReplies_count() > 0 || (status.getReblog() != null && status.getReblog().getReplies_count() > 0))
                    holder.status_reply.setText(String.valueOf(status.getReblog() != null ? status.getReblog().getReplies_count() : status.getReplies_count()));
            }

            boolean isOwner = status.getAccount().getId().equals(userId);

            // Pinning toots is only available on Mastodon 1._6_.0 instances.
            if (isOwner && Helper.canPin && (status.getVisibility().equals("public") || status.getVisibility().equals("unlisted")) && status.getReblog() == null) {
                Drawable imgPin;
                if (status.isPinned() || (status.getReblog() != null && status.getReblog().isPinned())) {
                    changeDrawableColor(context, R.drawable.ic_pin_drop_p, R.color.marked_icon);
                    imgPin = ContextCompat.getDrawable(context, R.drawable.ic_pin_drop_p);
                } else {
                    if (theme == THEME_DARK)
                        changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_dark);
                    else if (theme == THEME_BLACK)
                        changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_black);
                    else
                        changeDrawableColor(context, R.drawable.ic_pin_drop, R.color.action_light);
                    imgPin = ContextCompat.getDrawable(context, R.drawable.ic_pin_drop);
                }
                assert imgPin != null;
                imgPin.setBounds(0, 0, (int) (20 * iconSizePercent / 100 * scale + 0.5f), (int) (20 * iconSizePercent / 100 * scale + 0.5f));
                holder.status_pin.setImageDrawable(imgPin);

                holder.status_pin.setVisibility(View.VISIBLE);
            } else {
                holder.status_pin.setVisibility(View.GONE);
            }


            if (status.getWebviewURL() != null) {
                holder.status_cardview_webview.loadUrl(status.getWebviewURL());
                holder.status_cardview_webview.setVisibility(View.VISIBLE);
                holder.status_cardview_video.setVisibility(View.VISIBLE);
                holder.webview_preview.setVisibility(View.GONE);
            } else {
                holder.status_cardview_webview.setVisibility(View.GONE);
                holder.status_cardview_video.setVisibility(View.GONE);
                holder.webview_preview.setVisibility(View.VISIBLE);
            }

            if ((type == RetrieveFeedsAsyncTask.Type.CONTEXT && viewHolder.getAdapterPosition() == conversationPosition) || display_card || display_video_preview) {

                if (type == RetrieveFeedsAsyncTask.Type.CONTEXT & viewHolder.getAdapterPosition() == conversationPosition)
                    holder.status_cardview_content.setVisibility(View.VISIBLE);
                else
                    holder.status_cardview_content.setVisibility(View.GONE);

                if (viewHolder.getAdapterPosition() == conversationPosition || display_card || display_video_preview) {
                    Card card = status.getReblog() != null ? status.getReblog().getCard() : status.getCard();
                    if (card != null) {
                        holder.status_cardview_content.setText(card.getDescription());
                        holder.status_cardview_title.setText(card.getTitle());
                        holder.status_cardview_url.setText(card.getUrl());
                        if (card.getImage() != null && card.getImage().length() > 10) {
                            holder.status_cardview_image.setVisibility(View.VISIBLE);
                            if (!((Activity) context).isFinishing())
                                Glide.with(holder.status_cardview_image.getContext())
                                        .load(card.getImage())
                                        .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners((int) Helper.convertDpToPixel(3, context))))
                                        .into(holder.status_cardview_image);
                        } else
                            holder.status_cardview_image.setVisibility(View.GONE);
                        if (!card.getType().equals("video") && (display_card || viewHolder.getAdapterPosition() == conversationPosition)) {
                            holder.status_cardview.setVisibility(View.VISIBLE);
                            holder.status_cardview_video.setVisibility(View.GONE);
                            holder.status_cardview.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Helper.openBrowser(context, card.getUrl());
                                }
                            });
                        } else if (card.getType().equals("video") && (display_video_preview || viewHolder.getAdapterPosition() == conversationPosition)) {
                            Glide.with(holder.status_cardview_image.getContext())
                                    .load(card.getImage())
                                    .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(10)))
                                    .into(holder.webview_preview_card);
                            holder.status_cardview.setVisibility(View.GONE);
                            holder.status_cardview_video.setVisibility(View.VISIBLE);
                            holder.status_cardview_webview.getSettings().setJavaScriptEnabled(true);
                            String html = card.getHtml();
                            String src = card.getUrl();
                            if (html != null) {
                                Matcher matcher = Pattern.compile("src=\"([^\"]+)\"").matcher(html);
                                if (matcher.find())
                                    src = matcher.group(1);
                            }
                            final String finalSrc = src;
                            holder.status_cardview_webview.setWebViewClient(new WebViewClient() {
                                @Override
                                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                                    holder.status_cardview_video.setVisibility(View.GONE);
                                }
                            });
                            holder.webview_preview.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    status.setWebviewURL(finalSrc);
                                    notifyStatusChanged(status);
                                }
                            });
                        }
                    } else {
                        holder.status_cardview.setVisibility(View.GONE);
                        holder.status_cardview_video.setVisibility(View.GONE);
                    }

                } else {
                    holder.status_cardview.setVisibility(View.GONE);
                    holder.status_cardview_video.setVisibility(View.GONE);
                }
            } else {
                holder.status_cardview.setVisibility(View.GONE);
                holder.status_cardview_video.setVisibility(View.GONE);
            }

            holder.status_reply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CrossActions.doCrossReply(context, status, type, true);
                }
            });


            holder.spark_button_fav.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!status.isFavourited() && confirmFav)
                        status.setFavAnimated(true);
                    if (!status.isFavourited() && !confirmFav) {
                        status.setFavAnimated(true);
                        notifyStatusChanged(status);
                    }
                    CrossActions.doCrossAction(context, type, status, null, (status.isFavourited() || (status.getReblog() != null && status.getReblog().isFavourited())) ? API.StatusAction.UNFAVOURITE : API.StatusAction.FAVOURITE, statusListAdapter, StatusListAdapter.this, true);
                }
            });

            holder.spark_button_reblog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!status.isReblogged() && confirmBoost)
                        status.setBoostAnimated(true);
                    if (!status.isReblogged() && !confirmBoost) {
                        status.setBoostAnimated(true);
                        notifyStatusChanged(status);
                    }
                    CrossActions.doCrossAction(context, type, status, null, (status.isReblogged() || (status.getReblog() != null && status.getReblog().isReblogged())) ? API.StatusAction.UNREBLOG : API.StatusAction.REBLOG, statusListAdapter, StatusListAdapter.this, true);
                }
            });
            holder.status_pin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CrossActions.doCrossAction(context, type, status, null, (status.isPinned() || (status.getReblog() != null && status.getReblog().isPinned())) ? API.StatusAction.UNPIN : API.StatusAction.PIN, statusListAdapter, StatusListAdapter.this, true);
                }
            });

            if (!status.getVisibility().equals("direct"))
                holder.spark_button_fav.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        CrossActions.doCrossAction(context, type, status, null, API.StatusAction.FAVOURITE, statusListAdapter, StatusListAdapter.this, false);
                        return true;
                    }
                });
            if (!status.getVisibility().equals("direct"))
                holder.spark_button_reblog.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        CrossActions.doCrossAction(context, type, status, null, API.StatusAction.REBLOG, statusListAdapter, StatusListAdapter.this, false);
                        return true;
                    }
                });
            if (!status.getVisibility().equals("direct"))
                holder.status_reply.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        CrossActions.doCrossReply(context, status, type, false);
                        return true;
                    }
                });

            holder.yandex_translate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://translate.yandex.com/"));
                    context.startActivity(browserIntent);
                }
            });
            //Spoiler opens
            holder.status_spoiler_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    status.setSpoilerShown(!status.isSpoilerShown());
                    notifyStatusChanged(status);
                }
            });

            holder.status_show_more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    status.setAttachmentShown(true);
                    notifyStatusChanged(status);
                /*
                    Added a Countdown Timer, so that Sensitive (NSFW)
                    images only get displayed for user set time,
                    giving the user time to click on them to expand them,
                    if they want. Images are then hidden again.
                    -> Default value is set to 5 seconds
                 */

                    if (timeout > 0) {
                        new CountDownTimer((timeout * 1000), 1000) {
                            public void onTick(long millisUntilFinished) {
                            }

                            public void onFinish() {
                                status.setAttachmentShown(false);
                                notifyStatusChanged(status);
                            }
                        }.start();
                    }
                }
            });
            int style;
            if (theme == Helper.THEME_DARK) {
                style = R.style.DialogDark;
            } else if (theme == Helper.THEME_BLACK) {
                style = R.style.DialogBlack;
            } else {
                style = R.style.Dialog;
            }

            if (type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE)
                holder.status_more.setVisibility(View.GONE);

            final View attached = holder.status_more;
            holder.status_more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popup = new PopupMenu(context, attached);
                    final boolean isOwner = status.getAccount().getId().equals(userId);
                    popup.getMenuInflater()
                            .inflate(R.menu.option_toot, popup.getMenu());
                    if (status.getVisibility().equals("private") || status.getVisibility().equals("direct")) {
                        popup.getMenu().findItem(R.id.action_mention).setVisible(false);
                    }
                    if (status.isBookmarked())
                        popup.getMenu().findItem(R.id.action_bookmark).setTitle(R.string.bookmark_remove);
                    else
                        popup.getMenu().findItem(R.id.action_bookmark).setTitle(R.string.bookmark_add);
                    final String[] stringArrayConf;
                    if (status.getVisibility().equals("direct") || (status.getVisibility().equals("private") && !isOwner))
                        popup.getMenu().findItem(R.id.action_schedule_boost).setVisible(false);
                    if (isOwner) {
                        popup.getMenu().findItem(R.id.action_block).setVisible(false);
                        popup.getMenu().findItem(R.id.action_mute).setVisible(false);
                        popup.getMenu().findItem(R.id.action_report).setVisible(false);
                        popup.getMenu().findItem(R.id.action_timed_mute).setVisible(false);
                        popup.getMenu().findItem(R.id.action_block_domain).setVisible(false);
                        stringArrayConf = context.getResources().getStringArray(R.array.more_action_owner_confirm);
                    } else {
                        popup.getMenu().findItem(R.id.action_redraft).setVisible(false);
                        popup.getMenu().findItem(R.id.action_remove).setVisible(false);
                        //Same instance
                        if (status.getAccount().getAcct().split("@").length < 2)
                            popup.getMenu().findItem(R.id.action_block_domain).setVisible(false);
                        stringArrayConf = context.getResources().getStringArray(R.array.more_action_confirm);
                        if (type != RetrieveFeedsAsyncTask.Type.HOME) {
                            popup.getMenu().findItem(R.id.action_timed_mute).setVisible(false);
                        }
                    }

                    MenuItem itemBookmark = popup.getMenu().findItem(R.id.action_bookmark);
                    if (itemBookmark.getActionView() != null)
                        itemBookmark.getActionView().setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                CrossActions.doCrossBookmark(context, status, statusListAdapter);
                                return true;
                            }
                        });
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            AlertDialog.Builder builderInner;
                            final API.StatusAction doAction;
                            switch (item.getItemId()) {
                                case R.id.action_redraft:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[1]);
                                    redraft = true;
                                    doAction = API.StatusAction.UNSTATUS;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                        builderInner.setMessage(Html.fromHtml(status.getContent(), Html.FROM_HTML_MODE_LEGACY));
                                    else
                                        //noinspection deprecation
                                        builderInner.setMessage(Html.fromHtml(status.getContent()));
                                    break;
                                case R.id.action_schedule_boost:
                                    AlertDialog.Builder dialogBuilderBoost = new AlertDialog.Builder(context, style);
                                    LayoutInflater inflaterBoost = ((Activity) context).getLayoutInflater();
                                    @SuppressLint("InflateParams") View dialogViewBoost = inflaterBoost.inflate(R.layout.datetime_picker, null);
                                    dialogBuilderBoost.setView(dialogViewBoost);
                                    final AlertDialog alertDialogBoost = dialogBuilderBoost.create();

                                    final DatePicker datePickerBoost = dialogViewBoost.findViewById(R.id.date_picker);
                                    final TimePicker timePickerBoost = dialogViewBoost.findViewById(R.id.time_picker);
                                    timePickerBoost.setIs24HourView(true);
                                    Button date_time_cancelBoost = dialogViewBoost.findViewById(R.id.date_time_cancel);
                                    final ImageButton date_time_previousBoost = dialogViewBoost.findViewById(R.id.date_time_previous);
                                    final ImageButton date_time_nextBoost = dialogViewBoost.findViewById(R.id.date_time_next);
                                    final ImageButton date_time_setBoost = dialogViewBoost.findViewById(R.id.date_time_set);

                                    //Buttons management
                                    date_time_cancelBoost.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            alertDialogBoost.dismiss();
                                        }
                                    });
                                    date_time_nextBoost.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            datePickerBoost.setVisibility(View.GONE);
                                            timePickerBoost.setVisibility(View.VISIBLE);
                                            date_time_previousBoost.setVisibility(View.VISIBLE);
                                            date_time_nextBoost.setVisibility(View.GONE);
                                            date_time_setBoost.setVisibility(View.VISIBLE);
                                        }
                                    });
                                    date_time_previousBoost.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            datePickerBoost.setVisibility(View.VISIBLE);
                                            timePickerBoost.setVisibility(View.GONE);
                                            date_time_previousBoost.setVisibility(View.GONE);
                                            date_time_nextBoost.setVisibility(View.VISIBLE);
                                            date_time_setBoost.setVisibility(View.GONE);
                                        }
                                    });
                                    date_time_setBoost.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            int hour, minute;
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                hour = timePickerBoost.getHour();
                                                minute = timePickerBoost.getMinute();
                                            } else {
                                                //noinspection deprecation
                                                hour = timePickerBoost.getCurrentHour();
                                                //noinspection deprecation
                                                minute = timePickerBoost.getCurrentMinute();
                                            }
                                            Calendar calendar = new GregorianCalendar(datePickerBoost.getYear(),
                                                    datePickerBoost.getMonth(),
                                                    datePickerBoost.getDayOfMonth(),
                                                    hour,
                                                    minute);
                                            long time = calendar.getTimeInMillis();
                                            if ((time - new Date().getTime()) < 60000) {
                                                Toasty.warning(context, context.getString(R.string.toot_scheduled_date), Toast.LENGTH_LONG).show();
                                            } else {
                                                //Schedules the toot
                                                ScheduledBoostsSyncJob.schedule(context, status, time);
                                                //Clear content
                                                Toasty.info(context, context.getString(R.string.boost_scheduled), Toast.LENGTH_LONG).show();
                                                alertDialogBoost.dismiss();
                                            }
                                        }
                                    });
                                    alertDialogBoost.show();

                                    return true;
                                case R.id.action_info:
                                    Intent intent = new Intent(context, TootInfoActivity.class);
                                    Bundle b = new Bundle();
                                    if (status.getReblog() != null) {
                                        b.putString("toot_id", status.getReblog().getId());
                                        b.putInt("toot_reblogs_count", status.getReblog().getReblogs_count());
                                        b.putInt("toot_favorites_count", status.getReblog().getFavourites_count());
                                    } else {
                                        b.putString("toot_id", status.getId());
                                        b.putInt("toot_reblogs_count", status.getReblogs_count());
                                        b.putInt("toot_favorites_count", status.getFavourites_count());
                                    }
                                    intent.putExtras(b);
                                    context.startActivity(intent);
                                    return true;
                                case R.id.action_open_browser:
                                    Helper.openBrowser(context, status.getReblog() != null ? status.getReblog().getUrl() : status.getUrl());
                                    return true;
                                case R.id.action_remove:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[0]);
                                    doAction = API.StatusAction.UNSTATUS;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                        builderInner.setMessage(Html.fromHtml(status.getContent(), Html.FROM_HTML_MODE_LEGACY));
                                    else
                                        //noinspection deprecation
                                        builderInner.setMessage(Html.fromHtml(status.getContent()));
                                    break;
                                case R.id.action_block_domain:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[3]);
                                    doAction = API.StatusAction.BLOCK_DOMAIN;
                                    String domain = status.getAccount().getAcct().split("@")[1];
                                    builderInner.setMessage(context.getString(R.string.block_domain_confirm_message, domain));
                                    break;
                                case R.id.action_mute:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[0]);
                                    doAction = API.StatusAction.MUTE;
                                    break;
                                case R.id.action_bookmark:
                                    if (type != RetrieveFeedsAsyncTask.Type.CACHE_BOOKMARKS) {
                                        status.setBookmarked(!status.isBookmarked());
                                        try {
                                            if (status.isBookmarked()) {
                                                new StatusCacheDAO(context, db).insertStatus(StatusCacheDAO.BOOKMARK_CACHE, status);
                                                Toasty.success(context, context.getString(R.string.status_bookmarked), Toast.LENGTH_LONG).show();
                                            } else {
                                                new StatusCacheDAO(context, db).remove(StatusCacheDAO.BOOKMARK_CACHE, status);
                                                Toasty.success(context, context.getString(R.string.status_unbookmarked), Toast.LENGTH_LONG).show();
                                            }
                                            notifyStatusChanged(status);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Toasty.error(context, context.getString(R.string.toast_error), Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        int position = 0;
                                        for (Status statustmp : statuses) {
                                            if (statustmp.getId().equals(status.getId())) {
                                                statuses.remove(status);
                                                statusListAdapter.notifyItemRemoved(position);
                                                new StatusCacheDAO(context, db).remove(StatusCacheDAO.BOOKMARK_CACHE, statustmp);
                                                Toasty.success(context, context.getString(R.string.status_unbookmarked), Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            position++;
                                        }
                                    }
                                    return true;
                                case R.id.action_timed_mute:
                                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context, style);
                                    LayoutInflater inflater = ((Activity) context).getLayoutInflater();
                                    @SuppressLint("InflateParams") View dialogView = inflater.inflate(R.layout.datetime_picker, null);
                                    dialogBuilder.setView(dialogView);
                                    final AlertDialog alertDialog = dialogBuilder.create();
                                    final DatePicker datePicker = dialogView.findViewById(R.id.date_picker);
                                    final TimePicker timePicker = dialogView.findViewById(R.id.time_picker);
                                    timePicker.setIs24HourView(true);
                                    Button date_time_cancel = dialogView.findViewById(R.id.date_time_cancel);
                                    final ImageButton date_time_previous = dialogView.findViewById(R.id.date_time_previous);
                                    final ImageButton date_time_next = dialogView.findViewById(R.id.date_time_next);
                                    final ImageButton date_time_set = dialogView.findViewById(R.id.date_time_set);

                                    //Buttons management
                                    date_time_cancel.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            alertDialog.dismiss();
                                        }
                                    });
                                    date_time_next.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            datePicker.setVisibility(View.GONE);
                                            timePicker.setVisibility(View.VISIBLE);
                                            date_time_previous.setVisibility(View.VISIBLE);
                                            date_time_next.setVisibility(View.GONE);
                                            date_time_set.setVisibility(View.VISIBLE);
                                        }
                                    });
                                    date_time_previous.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            datePicker.setVisibility(View.VISIBLE);
                                            timePicker.setVisibility(View.GONE);
                                            date_time_previous.setVisibility(View.GONE);
                                            date_time_next.setVisibility(View.VISIBLE);
                                            date_time_set.setVisibility(View.GONE);
                                        }
                                    });
                                    date_time_set.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            int hour, minute;
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                hour = timePicker.getHour();
                                                minute = timePicker.getMinute();
                                            } else {
                                                //noinspection deprecation
                                                hour = timePicker.getCurrentHour();
                                                //noinspection deprecation
                                                minute = timePicker.getCurrentMinute();
                                            }
                                            Calendar calendar = new GregorianCalendar(datePicker.getYear(),
                                                    datePicker.getMonth(),
                                                    datePicker.getDayOfMonth(),
                                                    hour,
                                                    minute);
                                            long time = calendar.getTimeInMillis();
                                            if ((time - new Date().getTime()) < 60000) {
                                                Toasty.error(context, context.getString(R.string.timed_mute_date_error), Toast.LENGTH_LONG).show();
                                            } else {
                                                //Store the toot as draft first
                                                String targeted_id = status.getAccount().getId();
                                                Date date_mute = new Date(time);
                                                SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                                                Account account = new AccountDAO(context, db).getAccountByID(userId);
                                                new TempMuteDAO(context, db).insert(account, targeted_id, new Date(time));
                                                if (timedMute != null && !timedMute.contains(account.getId()))
                                                    timedMute.add(targeted_id);
                                                else if (timedMute == null) {
                                                    timedMute = new ArrayList<>();
                                                    timedMute.add(targeted_id);
                                                }
                                                Toasty.success(context, context.getString(R.string.timed_mute_date, status.getAccount().getAcct(), Helper.dateToString(date_mute)), Toast.LENGTH_LONG).show();
                                                alertDialog.dismiss();
                                                notifyDataSetChanged();
                                            }
                                        }
                                    });
                                    alertDialog.show();
                                    return true;
                                case R.id.action_block:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[1]);
                                    doAction = API.StatusAction.BLOCK;
                                    break;
                                case R.id.action_translate:
                                    translateToot(status);
                                    return true;
                                case R.id.action_report:
                                    builderInner = new AlertDialog.Builder(context, style);
                                    builderInner.setTitle(stringArrayConf[2]);
                                    doAction = API.StatusAction.REPORT;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                        builderInner.setMessage(Html.fromHtml(status.getContent(), Html.FROM_HTML_MODE_LEGACY));
                                    else
                                        //noinspection deprecation
                                        builderInner.setMessage(Html.fromHtml(status.getContent()));
                                    break;
                                case R.id.action_copy:
                                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    final String content;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                        content = Html.fromHtml(status.getContent(), Html.FROM_HTML_MODE_LEGACY).toString();
                                    else
                                        //noinspection deprecation
                                        content = Html.fromHtml(status.getContent()).toString();
                                    ClipData clip = ClipData.newPlainText(Helper.CLIP_BOARD, content);
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(clip);
                                        Toasty.info(context, context.getString(R.string.clipboard), Toast.LENGTH_LONG).show();
                                    }
                                    return true;
                                case R.id.action_share:
                                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.shared_via));
                                    String url;

                                    if (status.getReblog() != null) {
                                        if (status.getReblog().getUri().startsWith("http"))
                                            url = status.getReblog().getUri();
                                        else
                                            url = status.getReblog().getUrl();
                                    } else {
                                        if (status.getUri().startsWith("http"))
                                            url = status.getUri();
                                        else
                                            url = status.getUrl();
                                    }
                                    String extra_text;

                                    if (share_details) {
                                        extra_text = (status.getReblog() != null) ? status.getReblog().getAccount().getAcct() : status.getAccount().getAcct();
                                        if (extra_text.split("@").length == 1)
                                            extra_text = "@" + extra_text + "@" + Helper.getLiveInstance(context);
                                        else
                                            extra_text = "@" + extra_text;
                                        extra_text += " " + Helper.shortnameToUnicode(":link:", true) + " " + url + "\r\n-\n";
                                        final String contentToot;
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                            contentToot = Html.fromHtml((status.getReblog() != null) ? status.getReblog().getContent() : status.getContent(), Html.FROM_HTML_MODE_LEGACY).toString();
                                        else
                                            //noinspection deprecation
                                            contentToot = Html.fromHtml((status.getReblog() != null) ? status.getReblog().getContent() : status.getContent()).toString();
                                        extra_text += contentToot;
                                    } else {
                                        extra_text = url;
                                    }
                                    sendIntent.putExtra(Intent.EXTRA_TEXT, extra_text);
                                    sendIntent.setType("text/plain");
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_with)));
                                    return true;
                                case R.id.action_mention:
                                    // Get a handler that can be used to post to the main thread
                                    final Handler handler = new Handler();
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            String name = "@" + (status.getReblog() != null ? status.getReblog().getAccount().getAcct() : status.getAccount().getAcct());
                                            if (name.split("@", -1).length - 1 == 1)
                                                name = name + "@" + getLiveInstance(context);
                                            Bitmap bitmap = Helper.convertTootIntoBitmap(context, name, holder.status_content);
                                            Intent intent = new Intent(context, TootActivity.class);
                                            Bundle b = new Bundle();
                                            String fname = "tootmention_" + status.getId() + ".jpg";
                                            File file = new File(context.getCacheDir() + "/", fname);
                                            if (file.exists()) //noinspection ResultOfMethodCallIgnored
                                                file.delete();
                                            try {
                                                FileOutputStream out = new FileOutputStream(file);
                                                assert bitmap != null;
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                                                out.flush();
                                                out.close();
                                            } catch (Exception ignored) {
                                            }
                                            b.putString("fileMention", fname);
                                            b.putString("tootMention", (status.getReblog() != null) ? status.getReblog().getAccount().getAcct() : status.getAccount().getAcct());
                                            b.putString("urlMention", (status.getReblog() != null) ? status.getReblog().getUrl() : status.getUrl());
                                            intent.putExtras(b);
                                            context.startActivity(intent);
                                        }
                                    }, 500);
                                    return true;
                                default:
                                    return true;
                            }

                            //Text for report
                            EditText input = null;
                            if (doAction == API.StatusAction.REPORT) {
                                input = new EditText(context);
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                                input.setLayoutParams(lp);
                                builderInner.setView(input);
                            }
                            builderInner.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            final EditText finalInput = input;
                            builderInner.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (doAction == API.StatusAction.UNSTATUS) {
                                        String targetedId = status.getId();
                                        new PostActionAsyncTask(context, doAction, targetedId, StatusListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                        if (redraft) {
                                            if (status.getIn_reply_to_id() != null && !status.getIn_reply_to_id().trim().equals("null")) {
                                                toot = new Status();
                                                toot.setIn_reply_to_id(status.getIn_reply_to_id());
                                                toot.setSensitive(status.isSensitive());
                                                toot.setMedia_attachments(status.getMedia_attachments());
                                                if (status.getSpoiler_text() != null && status.getSpoiler_text().length() > 0)
                                                    toot.setSpoiler_text(status.getSpoiler_text().trim());
                                                toot.setContent(status.getContent());
                                                toot.setVisibility(status.getVisibility());
                                                new RetrieveFeedsAsyncTask(context, RetrieveFeedsAsyncTask.Type.ONESTATUS, status.getIn_reply_to_id(), null, false, false, StatusListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                            } else {
                                                toot = new Status();
                                                toot.setSensitive(status.isSensitive());
                                                toot.setMedia_attachments(status.getMedia_attachments());
                                                if (status.getSpoiler_text() != null && status.getSpoiler_text().length() > 0)
                                                    toot.setSpoiler_text(status.getSpoiler_text().trim());
                                                toot.setVisibility(status.getVisibility());
                                                toot.setContent(status.getContent());
                                                long id = new StatusStoredDAO(context, db).insertStatus(toot, null);
                                                Intent intentToot = new Intent(context, TootActivity.class);
                                                Bundle b = new Bundle();
                                                b.putLong("restored", id);
                                                b.putBoolean("removed", true);
                                                intentToot.putExtras(b);
                                                context.startActivity(intentToot);
                                            }
                                        }
                                    } else if (doAction == API.StatusAction.REPORT) {
                                        String comment = null;
                                        if (finalInput.getText() != null)
                                            comment = finalInput.getText().toString();
                                        new PostActionAsyncTask(context, doAction, status.getId(), status, comment, StatusListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    } else {
                                        String targetedId;
                                        if (item.getItemId() == R.id.action_block_domain) {
                                            targetedId = status.getAccount().getAcct().split("@")[1];
                                        } else {
                                            targetedId = status.getAccount().getId();
                                        }
                                        new PostActionAsyncTask(context, doAction, targetedId, StatusListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    }
                                    dialog.dismiss();
                                }
                            });
                            builderInner.show();
                            return true;
                        }
                    });
                    popup.show();
                }
            });


            if (type != RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE) {
                holder.status_account_profile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (targetedId == null || !targetedId.equals(status.getAccount().getId())) {
                            Intent intent = new Intent(context, ShowAccountActivity.class);
                            Bundle b = new Bundle();
                            b.putParcelable("account", status.getAccount());
                            intent.putExtras(b);
                            context.startActivity(intent);
                        }
                    }
                });

                holder.status_account_profile_boost.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (targetedId == null || !targetedId.equals(status.getReblog().getAccount().getId())) {
                            Intent intent = new Intent(context, ShowAccountActivity.class);
                            Bundle b = new Bundle();
                            b.putParcelable("account", status.getReblog().getAccount());
                            intent.putExtras(b);
                            context.startActivity(intent);
                        }
                    }
                });
            } else {
                holder.status_account_profile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (targetedId == null || !targetedId.equals(status.getAccount().getId())) {
                            Account account = status.getAccount();
                            Pattern instanceHost = Pattern.compile("https?:\\/\\/([\\da-z\\.-]+\\.[a-z\\.]{2,10})");
                            Matcher matcher = instanceHost.matcher(status.getUrl());
                            String instance = null;
                            while (matcher.find()) {
                                instance = matcher.group(1);
                            }
                            account.setInstance(instance);
                            CrossActions.doCrossProfile(context, account);
                        }
                    }
                });
                holder.status_account_profile_boost.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (targetedId == null || !targetedId.equals(status.getReblog().getAccount().getId())) {
                            Account account = status.getReblog().getAccount();
                            Pattern instanceHost = Pattern.compile("https?:\\/\\/([\\da-z\\.-]+\\.[a-z\\.]{2,10})");
                            Matcher matcher = instanceHost.matcher(status.getUrl());
                            String instance = null;
                            while (matcher.find()) {
                                instance = matcher.group(1);
                            }
                            account.setInstance(instance);
                            CrossActions.doCrossProfile(context, account);
                        }
                    }
                });
            }

            if (getItemViewType(viewHolder.getAdapterPosition()) == FOCUSED_STATUS && status.getApplication() != null && status.getApplication().getName() != null && status.getApplication().getName().length() > 0) {
                Application application = status.getApplication();
                holder.status_toot_app.setText(application.getName());
                if (application.getWebsite() != null && !application.getWebsite().trim().equals("null") && application.getWebsite().trim().length() == 0) {
                    holder.status_toot_app.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Helper.openBrowser(context, application.getWebsite());
                        }
                    });
                }
                holder.status_toot_app.setVisibility(View.VISIBLE);
            } else {
                holder.status_toot_app.setVisibility(View.GONE);
            }
        }
    }


    private void loadAttachments(final Status status, final ViewHolder holder) {
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean fullAttachement = sharedpreferences.getBoolean(Helper.SET_FULL_PREVIEW, false);
        List<Attachment> attachments = status.getMedia_attachments();

        if (attachments != null && attachments.size() > 0) {
            int i = 0;
            holder.horizontal_second_image.setVisibility(View.VISIBLE);
            if (fullAttachement)
                holder.status_horizontal_document_container.setVisibility(View.VISIBLE);
            else
                holder.status_document_container.setVisibility(View.VISIBLE);
            if (attachments.size() == 1) {
                if (!fullAttachement)
                    holder.status_container2.setVisibility(View.GONE);
                else {
                    holder.status_prev1_h.setVisibility(View.VISIBLE);
                    holder.status_prev2_h.setVisibility(View.GONE);
                    holder.status_prev3_h.setVisibility(View.GONE);
                    holder.status_prev4_h.setVisibility(View.GONE);
                    holder.horizontal_second_image.setVisibility(View.GONE);
                }
                if (attachments.get(0).getUrl().trim().contains("missing.png"))
                    if (fullAttachement)
                        holder.status_horizontal_document_container.setVisibility(View.GONE);
                    else
                        holder.status_document_container.setVisibility(View.GONE);
            } else if (attachments.size() == 2) {
                if (!fullAttachement) {
                    holder.status_container2.setVisibility(View.VISIBLE);
                    holder.status_container3.setVisibility(View.GONE);
                    holder.status_prev4_container.setVisibility(View.GONE);
                    if (attachments.get(1).getUrl().trim().contains("missing.png"))
                        holder.status_container2.setVisibility(View.GONE);
                } else {
                    holder.status_prev1_h.setVisibility(View.VISIBLE);
                    holder.status_prev2_h.setVisibility(View.VISIBLE);
                    holder.status_prev3_h.setVisibility(View.GONE);
                    holder.status_prev4_h.setVisibility(View.GONE);
                    if (attachments.get(1).getUrl().trim().contains("missing.png"))
                        holder.status_prev2_h.setVisibility(View.GONE);
                }
            } else if (attachments.size() == 3) {
                if (!fullAttachement) {
                    holder.status_container2.setVisibility(View.VISIBLE);
                    holder.status_container3.setVisibility(View.VISIBLE);
                    holder.status_prev4_container.setVisibility(View.GONE);
                    if (attachments.get(2).getUrl().trim().contains("missing.png"))
                        holder.status_container3.setVisibility(View.GONE);
                } else {
                    holder.status_prev1_h.setVisibility(View.VISIBLE);
                    holder.status_prev2_h.setVisibility(View.VISIBLE);
                    holder.status_prev3_h.setVisibility(View.VISIBLE);
                    holder.status_prev4_h.setVisibility(View.GONE);
                    if (attachments.get(2).getUrl().trim().contains("missing.png"))
                        holder.status_prev3_h.setVisibility(View.GONE);
                }
            } else {
                if (!fullAttachement) {
                    holder.status_container2.setVisibility(View.VISIBLE);
                    holder.status_container3.setVisibility(View.VISIBLE);
                    holder.status_prev4_container.setVisibility(View.VISIBLE);
                    if (attachments.get(2).getUrl().trim().contains("missing.png"))
                        holder.status_prev4_container.setVisibility(View.GONE);
                } else {
                    holder.status_prev1_h.setVisibility(View.VISIBLE);
                    holder.status_prev2_h.setVisibility(View.VISIBLE);
                    holder.status_prev3_h.setVisibility(View.VISIBLE);
                    holder.status_prev4_h.setVisibility(View.VISIBLE);
                    if (attachments.get(2).getUrl().trim().contains("missing.png"))
                        holder.status_prev3_h.setVisibility(View.GONE);
                }
            }
            int position = 1;
            for (final Attachment attachment : attachments) {
                ImageView imageView;
                if (i == 0) {
                    imageView = fullAttachement ? holder.status_prev1_h : holder.status_prev1;
                    if (attachment.getType().equals("image") || attachment.getType().equals("unknown"))
                        if (fullAttachement)
                            holder.status_prev1_play_h.setVisibility(View.GONE);
                        else
                            holder.status_prev1_play.setVisibility(View.GONE);
                    else if (fullAttachement)
                        holder.status_prev1_play_h.setVisibility(View.VISIBLE);
                    else
                        holder.status_prev1_play.setVisibility(View.VISIBLE);
                } else if (i == 1) {
                    imageView = fullAttachement ? holder.status_prev2_h : holder.status_prev2;
                    if (attachment.getType().equals("image") || attachment.getType().equals("unknown"))
                        if (fullAttachement)
                            holder.status_prev2_play_h.setVisibility(View.GONE);
                        else
                            holder.status_prev2_play.setVisibility(View.GONE);
                    else if (fullAttachement)
                        holder.status_prev2_play_h.setVisibility(View.VISIBLE);
                    else
                        holder.status_prev2_play.setVisibility(View.VISIBLE);
                } else if (i == 2) {
                    imageView = fullAttachement ? holder.status_prev3_h : holder.status_prev3;
                    if (attachment.getType().equals("image") || attachment.getType().equals("unknown"))
                        if (fullAttachement)
                            holder.status_prev3_play_h.setVisibility(View.GONE);
                        else
                            holder.status_prev3_play.setVisibility(View.GONE);
                    else if (fullAttachement)
                        holder.status_prev3_play_h.setVisibility(View.VISIBLE);
                    else
                        holder.status_prev3_play.setVisibility(View.VISIBLE);
                } else {
                    imageView = fullAttachement ? holder.status_prev4_h : holder.status_prev4;
                    if (attachment.getType().equals("image") || attachment.getType().equals("unknown"))
                        if (fullAttachement)
                            holder.status_prev4_play_h.setVisibility(View.GONE);
                        else
                            holder.status_prev4_play.setVisibility(View.GONE);
                    else if (fullAttachement)
                        holder.status_prev4_play_h.setVisibility(View.VISIBLE);
                    else
                        holder.status_prev4_play.setVisibility(View.VISIBLE);
                }
                String url = attachment.getPreview_url();

                if (url == null || url.trim().equals(""))
                    url = attachment.getUrl();
                else if (attachment.getType().equals("unknown"))
                    url = attachment.getRemote_url();

                if (fullAttachement) {
                    imageView.setImageBitmap(null);
                    if (!url.trim().contains("missing.png") && !((Activity) context).isFinishing())
                        Glide.with(imageView.getContext())
                                .asBitmap()
                                .load(url)
                                .thumbnail(0.1f)
                                .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(10)))
                                .into(new SimpleTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                                        DrawableTransitionOptions.withCrossFade();
                                        int width = resource.getWidth();
                                        int height = resource.getHeight();

                                        if (height < Helper.convertDpToPixel(200, context)) {
                                            double ratio = ((double) Helper.convertDpToPixel(200, context) / (double) height);
                                            width = (int) (ratio * width);
                                            height = (int) Helper.convertDpToPixel(200, context);
                                            resource = Bitmap.createScaledBitmap(resource, width, height, false);
                                        }
                                        imageView.setImageBitmap(resource);
                                    }
                                });
                } else {
                    if (!url.trim().contains("missing.png") && !((Activity) context).isFinishing())
                        Glide.with(imageView.getContext())
                                .load(url)
                                .thumbnail(0.1f)
                                .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(10)))
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .into(imageView);
                }
                final int finalPosition = position;
                if (attachment.getDescription() != null && !attachment.getDescription().equals("null"))
                    imageView.setContentDescription(attachment.getDescription());
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, MediaActivity.class);
                        Bundle b = new Bundle();
                        intent.putParcelableArrayListExtra("mediaArray", status.getMedia_attachments());
                        b.putInt("position", finalPosition);
                        intent.putExtras(b);
                        context.startActivity(intent);
                    }
                });
                i++;
                position++;
            }
        } else {
            holder.status_horizontal_document_container.setVisibility(View.GONE);
            holder.status_document_container.setVisibility(View.GONE);
        }
        holder.status_show_more.setVisibility(View.GONE);


    }


    @Override
    public void onRetrieveFeeds(APIResponse apiResponse) {

        if (apiResponse.getStatuses() != null && apiResponse.getStatuses().size() > 0) {
            SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            long id = new StatusStoredDAO(context, db).insertStatus(toot, apiResponse.getStatuses().get(0));
            Intent intentToot = new Intent(context, TootActivity.class);
            Bundle b = new Bundle();
            b.putLong("restored", id);
            b.putBoolean("removed", true);
            intentToot.putExtras(b);
            context.startActivity(intentToot);
        }

    }


    @Override
    public void onRetrieveAccount(Card card) {
        if (conversationPosition < this.statuses.size() && card != null)
            this.statuses.get(conversationPosition).setCard(card);
        if (conversationPosition < this.statuses.size())
            statusListAdapter.notifyItemChanged(conversationPosition);
    }


    @Override
    public void onPostAction(int statusCode, API.StatusAction statusAction, String targetedId, Error error) {

        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        if (error != null) {
            Toasty.error(context, error.getError(), Toast.LENGTH_LONG).show();
            return;
        }
        Helper.manageMessageStatusCode(context, statusCode, statusAction);
        //When muting or blocking an account, its status are removed from the list
        List<Status> statusesToRemove = new ArrayList<>();
        if (statusAction == API.StatusAction.MUTE || statusAction == API.StatusAction.BLOCK) {
            for (Status status : statuses) {
                if (status.getAccount().getId().equals(targetedId))
                    statusesToRemove.add(status);
            }
            statuses.removeAll(statusesToRemove);
            statusListAdapter.notifyDataSetChanged();
        } else if (statusAction == API.StatusAction.UNSTATUS) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    statuses.remove(status);
                    statusListAdapter.notifyItemRemoved(position);
                    SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                    //Remove the status from cache also
                    try {
                        new StatusCacheDAO(context, db).remove(StatusCacheDAO.ARCHIVE_CACHE, status);
                    } catch (Exception ignored) {
                    }
                    break;
                }
                position++;
            }
        } else if (statusAction == API.StatusAction.PIN || statusAction == API.StatusAction.UNPIN) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    if (statusAction == API.StatusAction.PIN)
                        status.setPinned(true);
                    else
                        status.setPinned(false);
                    statusListAdapter.notifyItemChanged(position);
                    break;
                }
                position++;
            }
        }

        if (statusAction == API.StatusAction.REBLOG) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    status.setReblogs_count(status.getReblogs_count() + 1);
                    statusListAdapter.notifyItemChanged(position);
                    break;
                }
                position++;
            }
        } else if (statusAction == API.StatusAction.UNREBLOG) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    if (status.getReblogs_count() - 1 >= 0)
                        status.setReblogs_count(status.getReblogs_count() - 1);
                    statusListAdapter.notifyItemChanged(position);
                    SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                    //Remove the status from cache also
                    try {
                        new StatusCacheDAO(context, db).remove(StatusCacheDAO.ARCHIVE_CACHE, status);
                    } catch (Exception ignored) {
                    }
                    break;
                }
                position++;
            }
        } else if (statusAction == API.StatusAction.FAVOURITE) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    status.setFavourites_count(status.getFavourites_count() + 1);
                    statusListAdapter.notifyItemChanged(position);
                    break;
                }
                position++;
            }
        } else if (statusAction == API.StatusAction.UNFAVOURITE) {
            int position = 0;
            for (Status status : statuses) {
                if (status.getId().equals(targetedId)) {
                    if (status.getFavourites_count() - 1 >= 0)
                        status.setFavourites_count(status.getFavourites_count() - 1);
                    statusListAdapter.notifyItemChanged(position);
                    break;
                }
                position++;
            }
        }
    }

    public void notifyStatusChanged(Status status) {
        for (int i = 0; i < statusListAdapter.getItemCount(); i++) {
            //noinspection ConstantConditions
            if (statusListAdapter.getItemAt(i) != null && statusListAdapter.getItemAt(i).getId().equals(status.getId())) {
                try {
                    statusListAdapter.notifyItemChanged(i);
                } catch (Exception ignored) {
                }
            }
        }
    }


    @Override
    public void onRetrieveEmoji(Status status, boolean fromTranslation) {
        if (status != null) {
            if (!fromTranslation) {
                status.setEmojiFound(true);
            } else {
                status.setEmojiTranslateFound(true);
            }
            notifyStatusChanged(status);
        }
    }

    @Override
    public void onRetrieveSearchEmoji(List<Emojis> emojis) {

    }


    private void translateToot(Status status) {
        //Manages translations
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        int trans = sharedpreferences.getInt(Helper.SET_TRANSLATOR, Helper.TRANS_YANDEX);
        MyTransL.translatorEngine et = MyTransL.translatorEngine.YANDEX;
        String api_key = null;


        if (trans == Helper.TRANS_YANDEX) {
            et = MyTransL.translatorEngine.YANDEX;
        } else if (trans == Helper.TRANS_DEEPL) {
            et = MyTransL.translatorEngine.DEEPL;
        }
        final MyTransL myTransL = MyTransL.getInstance(et);
        myTransL.setObfuscation(true);
        if (trans == Helper.TRANS_YANDEX) {
            api_key = sharedpreferences.getString(Helper.SET_YANDEX_API_KEY, Helper.YANDEX_KEY);
            myTransL.setYandexAPIKey(api_key);
        } else if (trans == Helper.TRANS_DEEPL) {
            api_key = sharedpreferences.getString(Helper.SET_DEEPL_API_KEY, "");
            myTransL.setDeeplAPIKey(api_key);
        }


        if (!status.isTranslated()) {
            String statusToTranslate;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                statusToTranslate = Html.fromHtml(status.getReblog() != null ? status.getReblog().getContent() : status.getContent(), Html.FROM_HTML_MODE_LEGACY).toString();
            else
                //noinspection deprecation
                statusToTranslate = Html.fromHtml(status.getReblog() != null ? status.getReblog().getContent() : status.getContent()).toString();
            //TODO: removes the replaceAll once fixed with the lib
            myTransL.translate(statusToTranslate, myTransL.getLocale(), new Results() {
                @Override
                public void onSuccess(Translate translate) {
                    if (translate.getTranslatedContent() != null) {
                        status.setTranslated(true);
                        status.setTranslationShown(true);
                        status.setContentTranslated(translate.getTranslatedContent());
                        Status.transformTranslation(context, status);
                        Status.makeEmojisTranslation(context, StatusListAdapter.this, status);
                        notifyStatusChanged(status);
                    } else {
                        Toasty.error(context, context.getString(R.string.toast_error_translate), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFail(HttpsConnectionException e) {
                    e.printStackTrace();
                    Toasty.error(context, context.getString(R.string.toast_error_translate), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            status.setTranslationShown(!status.isTranslationShown());
            notifyStatusChanged(status);
        }
    }

    public void setConversationPosition(int position) {
        this.conversationPosition = position;
    }
}