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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.varunest.sparkbutton.SparkButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.activities.MediaActivity;
import fr.gouv.etalab.mastodon.activities.ShowAccountActivity;
import fr.gouv.etalab.mastodon.activities.ShowConversationActivity;
import fr.gouv.etalab.mastodon.activities.TootActivity;
import fr.gouv.etalab.mastodon.asynctasks.PostActionAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.PostNotificationsAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Attachment;
import fr.gouv.etalab.mastodon.client.Entities.Emojis;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.client.Entities.Notification;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.helper.CrossActions;
import fr.gouv.etalab.mastodon.helper.CustomTextView;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnPostActionInterface;
import fr.gouv.etalab.mastodon.interfaces.OnPostNotificationsActionInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveEmojiAccountInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveEmojiInterface;

import static fr.gouv.etalab.mastodon.helper.Helper.THEME_BLACK;
import static fr.gouv.etalab.mastodon.helper.Helper.THEME_DARK;
import static fr.gouv.etalab.mastodon.helper.Helper.changeDrawableColor;
import static fr.gouv.etalab.mastodon.helper.Helper.getLiveInstance;


/**
 * Created by Thomas on 24/04/2017.
 * Adapter for Status
 */

public class NotificationsListAdapter extends RecyclerView.Adapter implements OnPostActionInterface, OnPostNotificationsActionInterface, OnRetrieveEmojiInterface, OnRetrieveEmojiAccountInterface {

    private Context context;
    private List<Notification> notifications;
    private LayoutInflater layoutInflater;
    private NotificationsListAdapter notificationsListAdapter;
    private int behaviorWithAttachments;
    private boolean isOnWifi;
    private NotificationsListAdapter.ViewHolder holder;
    private int style;

    public NotificationsListAdapter(Context context, boolean isOnWifi, int behaviorWithAttachments, List<Notification> notifications){
        this.context = context;
        this.notifications = notifications;
        layoutInflater = LayoutInflater.from(this.context);
        notificationsListAdapter = this;
        this.isOnWifi = isOnWifi;
        this.behaviorWithAttachments = behaviorWithAttachments;
    }

    

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.drawer_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {

        holder = (NotificationsListAdapter.ViewHolder) viewHolder;
        final Notification notification = notifications.get(position);
        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);

        int iconSizePercent = sharedpreferences.getInt(Helper.SET_ICON_SIZE, 130);
        int textSizePercent = sharedpreferences.getInt(Helper.SET_TEXT_SIZE, 110);



        final float scale = context.getResources().getDisplayMetrics().density;
        String type = notification.getType();
        String typeString = "";
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        boolean expand_cw = sharedpreferences.getBoolean(Helper.SET_EXPAND_CW, false);
        boolean confirmFav = sharedpreferences.getBoolean(Helper.SET_NOTIF_VALIDATION_FAV, false);
        boolean confirmBoost = sharedpreferences.getBoolean(Helper.SET_NOTIF_VALIDATION, true);

        if (theme == THEME_DARK ){
            holder.main_container_trans.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
            holder.main_container_trans.setAlpha(.5f);
        }else if( theme == THEME_BLACK){
            holder.main_container_trans.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
            holder.main_container_trans.setAlpha(.5f);
        }else{
            holder.main_container_trans.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
            holder.main_container_trans.setAlpha(.5f);
        }
        if (theme == Helper.THEME_DARK) {
            style = R.style.DialogDark;
        } else if (theme == Helper.THEME_BLACK){
            style = R.style.DialogBlack;
        }else {
            style = R.style.Dialog;
        }
        Drawable imgH = null;
        holder.status_date.setVisibility(View.VISIBLE);
        switch (type){
            case "mention":
                holder.status_action_container.setVisibility(View.VISIBLE);
                if (notification.getStatus().getVisibility() != null) {
                    if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                        typeString = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), context.getString(R.string.notif_mention));
                    else
                        typeString = String.format("@%s %s", notification.getAccount().getUsername(), context.getString(R.string.notif_mention));
                    if (theme == Helper.THEME_DARK) {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
                    } else if (theme == Helper.THEME_BLACK) {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
                    } else {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
                    }
                }
                imgH = ContextCompat.getDrawable(context, R.drawable.ic_chat_bubble_outline);
                holder.main_container_trans.setVisibility(View.GONE);
                holder.status_more.setVisibility(View.VISIBLE);
                break;
            case "reply":
                holder.status_action_container.setVisibility(View.VISIBLE);
                if (notification.getStatus().getVisibility() != null) {
                    if (notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                        typeString = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true), context.getString(R.string.notif_mention));
                    else
                        typeString = String.format("@%s %s", notification.getAccount().getUsername(), context.getString(R.string.notif_mention));
                    if (theme == Helper.THEME_DARK) {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
                    } else if (theme == Helper.THEME_BLACK) {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
                    } else {
                        if (notification.getStatus().getVisibility().equals("direct"))
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_private));
                        else
                            holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
                    }
                }
                imgH = ContextCompat.getDrawable(context, R.drawable.ic_chat_bubble_outline);
                holder.main_container_trans.setVisibility(View.GONE);
                holder.status_more.setVisibility(View.VISIBLE);
                break;
            case "renote":
                holder.status_action_container.setVisibility(View.GONE);
                if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                    typeString = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),context.getString(R.string.notif_reblog));
                else
                    typeString = String.format("@%s %s", notification.getAccount().getUsername(),context.getString(R.string.notif_reblog));
                if( theme == Helper.THEME_DARK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
                }else if( theme == Helper.THEME_BLACK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
                }else {
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
                }
                imgH = ContextCompat.getDrawable(context, R.drawable.ic_repeat_head);
                holder.main_container_trans.setVisibility(View.VISIBLE);
                holder.status_more.setVisibility(View.GONE);
                break;
            case "reaction":
                holder.status_action_container.setVisibility(View.GONE);
                if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                    typeString = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),context.getString(R.string.notif_favourite));
                else
                    typeString = String.format("@%s %s", notification.getAccount().getUsername(),context.getString(R.string.notif_favourite));
                if( theme == Helper.THEME_DARK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
                }else if( theme == Helper.THEME_BLACK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
                }else {
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
                }
                imgH = ContextCompat.getDrawable(context, R.drawable.ic_star_border_header);
                holder.main_container_trans.setVisibility(View.VISIBLE);
                holder.status_more.setVisibility(View.GONE);
                break;
            case "follow":
                holder.status_action_container.setVisibility(View.GONE);
                holder.status_date.setVisibility(View.GONE);
                if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0)
                    typeString = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),context.getString(R.string.notif_follow));
                else
                    typeString = String.format("@%s %s", notification.getAccount().getUsername(),context.getString(R.string.notif_follow));
                if( theme == Helper.THEME_DARK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_dark_1));
                }else if( theme == Helper.THEME_BLACK){
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_black_1));
                }else {
                    holder.card_status_container.setBackgroundColor(ContextCompat.getColor(context, R.color.notif_light_1));
                }
                imgH = ContextCompat.getDrawable(context, R.drawable.ic_follow_notif_header);
                holder.main_container_trans.setVisibility(View.GONE);
                break;
        }

        if( theme == THEME_BLACK){
            changeDrawableColor(context, R.drawable.ic_chat_bubble_outline, R.color.dark_icon);
            changeDrawableColor(context, R.drawable.ic_repeat_head,R.color.dark_icon);
            changeDrawableColor(context, R.drawable.ic_star_border_header,R.color.dark_icon);
            changeDrawableColor(context, R.drawable.ic_follow_notif_header,R.color.dark_icon);
        }else {
            changeDrawableColor(context, R.drawable.ic_chat_bubble_outline, R.color.mastodonC4);
            changeDrawableColor(context, R.drawable.ic_repeat_head,R.color.mastodonC4);
            changeDrawableColor(context, R.drawable.ic_star_border_header,R.color.mastodonC4);
            changeDrawableColor(context, R.drawable.ic_follow_notif_header,R.color.mastodonC4);
        }


        if( notification.getAccount().getdisplayNameSpan() == null) {
            holder.notification_type.setText(typeString);
            notification.getAccount().setStored_displayname(notification.getAccount().getDisplay_name());
            notification.getAccount().setDisplay_name(typeString);
        }else
            holder.notification_type.setText(notification.getAccount().getdisplayNameSpan(), TextView.BufferType.SPANNABLE);
        notification.getAccount().makeAccountNameEmoji(context, NotificationsListAdapter.this, notification.getAccount());
        if( imgH != null) {
            holder.notification_type.setCompoundDrawablePadding((int)Helper.convertDpToPixel(5, context));
            imgH.setBounds(0, 0, (int) (20 * iconSizePercent / 100 * scale + 0.5f), (int) (20 * iconSizePercent / 100 * scale + 0.5f));
        }
        holder.notification_type.setCompoundDrawables( imgH, null, null, null);

        holder.status_privacy.getLayoutParams().height = (int) Helper.convertDpToPixel((20*iconSizePercent/100), context);
        holder.status_privacy.getLayoutParams().width = (int) Helper.convertDpToPixel((20*iconSizePercent/100), context);
        holder.status_reply.getLayoutParams().height = (int) Helper.convertDpToPixel((20*iconSizePercent/100), context);
        holder.status_reply.getLayoutParams().width = (int) Helper.convertDpToPixel((20*iconSizePercent/100), context);
        holder.status_spoiler.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14*textSizePercent/100);

        holder.notification_status_content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14*textSizePercent/100);
        holder.notification_type.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14*textSizePercent/100);
        holder.status_date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12*textSizePercent/100);


        //Manages theme for icon colors
        if( theme == THEME_BLACK){
            changeDrawableColor(context, R.drawable.ic_reply,R.color.action_black);
            changeDrawableColor(context, holder.status_more, R.color.action_black);
            changeDrawableColor(context, holder.status_privacy, R.color.action_black);
            changeDrawableColor(context, R.drawable.ic_repeat,R.color.action_black);
            changeDrawableColor(context, R.drawable.ic_star_border,R.color.action_black);
            changeDrawableColor(context, R.drawable.ic_photo,R.color.dark_text);
            changeDrawableColor(context, R.drawable.ic_remove_red_eye,R.color.dark_text);
            changeDrawableColor(context, holder.notification_delete,R.color.dark_text);
        }else if( theme == Helper.THEME_DARK ){
            changeDrawableColor(context, R.drawable.ic_reply,R.color.action_dark);
            changeDrawableColor(context, holder.status_more, R.color.action_dark);
            changeDrawableColor(context, holder.status_privacy, R.color.action_dark);
            changeDrawableColor(context, R.drawable.ic_repeat,R.color.action_dark);
            changeDrawableColor(context, R.drawable.ic_star_border,R.color.action_dark);
            changeDrawableColor(context, R.drawable.ic_photo,R.color.mastodonC4);
            changeDrawableColor(context, R.drawable.ic_remove_red_eye,R.color.mastodonC4);
            changeDrawableColor(context, holder.notification_delete,R.color.dark_text);
        }else {
            changeDrawableColor(context, R.drawable.ic_reply,R.color.action_light);
            changeDrawableColor(context, holder.status_more, R.color.action_light);
            changeDrawableColor(context, holder.status_privacy, R.color.action_light);
            changeDrawableColor(context, R.drawable.ic_repeat,R.color.action_light);
            changeDrawableColor(context, R.drawable.ic_star_border,R.color.action_light);
            changeDrawableColor(context, R.drawable.ic_photo,R.color.black);
            changeDrawableColor(context, R.drawable.ic_remove_red_eye,R.color.black);
            changeDrawableColor(context, holder.notification_delete,R.color.black);
        }
        holder.spark_button_fav.pressOnTouch(false);
        holder.spark_button_reblog.pressOnTouch(false);
        holder.spark_button_fav.setActiveImageTint(R.color.marked_icon);
        holder.spark_button_reblog.setActiveImageTint(R.color.boost_icon);
        holder.spark_button_fav.setDisableCircle(true);
        holder.spark_button_reblog.setDisableCircle(true);
        if( theme == THEME_DARK) {
            holder.spark_button_fav.setInActiveImageTint(R.color.action_dark);
            holder.spark_button_reblog.setInActiveImageTint(R.color.action_dark);
        }else if(theme == THEME_BLACK) {
            holder.spark_button_fav.setInActiveImageTint(R.color.action_black);
            holder.spark_button_reblog.setInActiveImageTint(R.color.action_black);
        }else {
            holder.spark_button_fav.setInActiveImageTint(R.color.action_light);
            holder.spark_button_reblog.setInActiveImageTint(R.color.action_light);
        }
        holder.spark_button_fav.setColors(R.color.marked_icon, R.color.marked_icon);
        holder.spark_button_fav.setImageSize((int) (20 * iconSizePercent/100 * scale + 0.5f));
        holder.spark_button_fav.setMinimumWidth((int)Helper.convertDpToPixel((20 * iconSizePercent/100 * scale + 0.5f),context));

        holder.spark_button_reblog.setColors(R.color.boost_icon, R.color.boost_icon);
        holder.spark_button_reblog.setImageSize((int) (20 * iconSizePercent/100 * scale + 0.5f));
        holder.spark_button_reblog.setMinimumWidth((int)Helper.convertDpToPixel((20 * iconSizePercent/100 * scale + 0.5f),context));

        final Status status = notification.getStatus();
        if( status != null ){
            if( status.getMedia_attachments() == null || status.getMedia_attachments().size() < 1)
                holder.status_document_container.setVisibility(View.GONE);
            else
                holder.status_document_container.setVisibility(View.VISIBLE);

            holder.status_reply.setText("");
            if( !status.isClickable())
                Status.transform(context, status);
            if( !status.isEmojiFound())
                Status.makeEmojis(context, NotificationsListAdapter.this, status);
            holder.notification_status_content.setText(status.getContentSpan(), TextView.BufferType.SPANNABLE);
            holder.status_spoiler.setText(status.getContentSpanCW(), TextView.BufferType.SPANNABLE);
            holder.status_spoiler.setMovementMethod(LinkMovementMethod.getInstance());
            holder.notification_status_content.setMovementMethod(LinkMovementMethod.getInstance());
            holder.status_favorite_count.setText(String.valueOf(status.getFavourites_count()));
            holder.status_reblog_count.setText(String.valueOf(status.getReblogs_count()));
            holder.status_date.setText(Helper.dateDiff(context, status.getCreated_at()));

            Helper.absoluteDateTimeReveal(context, holder.status_date, status.getCreated_at());

            if (status.getMentions() != null) {
                holder.status_mention_spoiler.setText(Helper.makeMentionsClick(context, status.getMentions()), TextView.BufferType.SPANNABLE);
                holder.status_mention_spoiler.setMovementMethod(LinkMovementMethod.getInstance());
            }

            //Adds attachment -> disabled, to enable them uncomment the line below
            //loadAttachments(status, holder);
            holder.notification_status_container.setVisibility(View.VISIBLE);
            holder.card_status_container.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ShowConversationActivity.class);
                    Bundle b = new Bundle();
                    b.putParcelable("status", status);
                    intent.putExtras(b);
                    context.startActivity(intent);
                }
            });
            holder.notification_status_content.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ShowConversationActivity.class);
                    Bundle b = new Bundle();
                    b.putParcelable("status", status);
                    intent.putExtras(b);
                    context.startActivity(intent);
                }
            });
            holder.status_action_container.setVisibility(View.VISIBLE);


            Drawable imgReply;
            if( !status.isFavAnimated() ) {
                if (status.isFavourited() || (status.getReblog() != null && status.getReblog().isFavourited())) {
                    holder.spark_button_fav.setChecked(true);
                } else {
                    holder.spark_button_fav.setChecked(false);
                }
            }else {
                status.setFavAnimated(false);
                holder.spark_button_fav.setChecked(true);
                holder.spark_button_fav.setAnimationSpeed(1.0f);
                holder.spark_button_fav.playAnimation();
            }
            if( !status.isBoostAnimated()){
                if( status.isReblogged()|| (status.getReblog() != null && status.getReblog().isReblogged())) {
                    holder.spark_button_reblog.setChecked(true);
                }else {
                    holder.spark_button_reblog.setChecked(false);
                }
            }else {
                status.setBoostAnimated(false);
                holder.spark_button_reblog.setChecked(true);
                holder.spark_button_reblog.setAnimationSpeed(1.0f);
                holder.spark_button_reblog.playAnimation();

            }



            if( theme == THEME_DARK)
                changeDrawableColor(context, R.drawable.ic_reply,R.color.action_dark);
            else if(theme == THEME_BLACK)
                changeDrawableColor(context, R.drawable.ic_reply,R.color.action_black);
            else
                changeDrawableColor(context, R.drawable.ic_reply,R.color.action_light);
            imgReply = ContextCompat.getDrawable(context, R.drawable.ic_reply);

            if( status.getReblog() == null) {
                if (status.getSpoiler_text() != null && status.getSpoiler_text().trim().length() > 0 ) {
                    holder.status_spoiler_container.setVisibility(View.VISIBLE);
                    if( !status.isSpoilerShown() && !expand_cw) {
                        holder.notification_status_container.setVisibility(View.GONE);
                        holder.status_spoiler_mention_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler));
                    }else {
                        holder.notification_status_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler_less));
                    }
                } else {
                    holder.status_spoiler_container.setVisibility(View.GONE);
                    holder.status_spoiler_mention_container.setVisibility(View.GONE);
                    holder.notification_status_container.setVisibility(View.VISIBLE);
                }

            }else {
                if (status.getReblog().getSpoiler_text() != null && status.getReblog().getSpoiler_text().trim().length() > 0) {
                    holder.status_spoiler_container.setVisibility(View.VISIBLE);
                    if( !status.isSpoilerShown() && !expand_cw) {
                        holder.notification_status_container.setVisibility(View.GONE);
                        holder.status_spoiler_mention_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler));
                    }else {
                        holder.notification_status_container.setVisibility(View.VISIBLE);
                        holder.status_spoiler_mention_container.setVisibility(View.GONE);
                        holder.status_spoiler_button.setText(context.getString(R.string.load_attachment_spoiler_less));
                    }
                } else {
                    holder.status_spoiler_container.setVisibility(View.GONE);
                    holder.status_spoiler_mention_container.setVisibility(View.GONE);
                    holder.notification_status_container.setVisibility(View.VISIBLE);
                }
            }

            assert imgReply != null;
            imgReply.setBounds(0,0,(int) (20 * iconSizePercent/100 * scale + 0.5f),(int) (20 * iconSizePercent/100 * scale + 0.5f));
            holder.status_reply.setCompoundDrawables(imgReply, null, null, null);

            if( theme == THEME_DARK ){
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_dark));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_dark));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_dark));
            }else if(theme == THEME_BLACK){
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_black));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_black));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_black));
            }else {
                holder.status_favorite_count.setTextColor(ContextCompat.getColor(context, R.color.action_light));
                holder.status_reblog_count.setTextColor(ContextCompat.getColor(context, R.color.action_light));
                holder.status_reply.setTextColor(ContextCompat.getColor(context, R.color.action_light));
            }
            if( type.equals("favourite") || type.equals("reblog")){
                holder.status_document_container.setVisibility(View.GONE);
                holder.status_show_more.setVisibility(View.GONE);
            }else {
                if (status.getReblog() == null) {
                    if (status.getMedia_attachments().size() < 1) {
                        holder.status_document_container.setVisibility(View.GONE);
                        holder.status_show_more.setVisibility(View.GONE);
                    } else {
                        //If medias are loaded without any conditions or if device is on wifi
                        if (!status.isSensitive() && (behaviorWithAttachments == Helper.ATTACHMENT_ALWAYS || (behaviorWithAttachments == Helper.ATTACHMENT_WIFI && isOnWifi))) {
                            loadAttachments(notification, holder);
                            holder.status_show_more.setVisibility(View.GONE);
                            status.setAttachmentShown(true);
                        } else {
                            //Text depending if toots is sensitive or not
                            String textShowMore = (status.isSensitive()) ? context.getString(R.string.load_sensitive_attachment) : context.getString(R.string.load_attachment);
                            holder.status_show_more.setText(textShowMore);
                            if (!status.isAttachmentShown()) {
                                holder.status_show_more.setVisibility(View.VISIBLE);
                                holder.status_document_container.setVisibility(View.GONE);
                            } else {
                                loadAttachments(notification, holder);
                            }
                        }
                    }
                }
            }

            holder.spark_button_fav.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if( !status.isFavourited() && confirmFav)
                        status.setFavAnimated(true);
                    if( !status.isFavourited() && !confirmFav) {
                        status.setFavAnimated(true);
                        notifyNotificationChanged(notification);
                    }
                    CrossActions.doCrossAction(context, null, status, null, status.isFavourited()? API.StatusAction.UNFAVOURITE:API.StatusAction.FAVOURITE, notificationsListAdapter, NotificationsListAdapter.this, true);
                }
            });
            holder.spark_button_reblog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if( !status.isReblogged() && confirmBoost)
                        status.setBoostAnimated(true);
                    if( !status.isReblogged() && !confirmBoost) {
                        status.setBoostAnimated(true);
                        notifyNotificationChanged(notification);
                    }
                    CrossActions.doCrossAction(context, null, status, null, status.isReblogged()? API.StatusAction.UNREBLOG:API.StatusAction.REBLOG, notificationsListAdapter, NotificationsListAdapter.this, true);
                }
            });

            //Spoiler opens
            holder.status_spoiler_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notification.getStatus().setSpoilerShown(!status.isSpoilerShown());
                    notifyNotificationChanged(notification);
                }
            });


            switch (status.getVisibility()){
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
            switch (status.getVisibility()){
                case "direct":
                case "private":
                    holder.status_reblog_count.setVisibility(View.GONE);
                    holder.spark_button_reblog.setVisibility(View.GONE);
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



            holder.status_show_more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    notification.getStatus().setAttachmentShown(true);
                    notifyNotificationChanged(notification);
                    /*
                    Added a Countdown Timer, so that Sensitive (NSFW)
                    images only get displayed for user set time,
                    giving the user time to click on them to expand them,
                    if they want. Images are then hidden again.
                    -> Default value is set to 5 seconds
                    */
                    final int timeout = sharedpreferences.getInt(Helper.SET_NSFW_TIMEOUT, 5);

                    if (timeout > 0) {

                        new CountDownTimer((timeout * 1000), 1000) {

                            public void onTick(long millisUntilFinished) {
                            }

                            public void onFinish() {
                                notification.getStatus().setAttachmentShown(false);
                                notifyNotificationChanged(notification);
                            }
                        }.start();
                    }
                }
            });



            holder.status_reply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CrossActions.doCrossReply(context, status, RetrieveFeedsAsyncTask.Type.LOCAL, true);
                }
            });

            if( !status.getVisibility().equals("direct"))
            holder.spark_button_fav.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    CrossActions.doCrossAction(context, null, status, null, status.isFavourited()? API.StatusAction.UNFAVOURITE:API.StatusAction.FAVOURITE, notificationsListAdapter, NotificationsListAdapter.this, false);
                    return true;
                }
            });
            if( !status.getVisibility().equals("direct"))
            holder.status_reply.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    CrossActions.doCrossReply(context, status, RetrieveFeedsAsyncTask.Type.LOCAL, false);
                    return true;
                }
            });
            if( !status.getVisibility().equals("direct"))
            holder.spark_button_reblog.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    CrossActions.doCrossAction(context, null, status, null, status.isReblogged()? API.StatusAction.UNREBLOG:API.StatusAction.REBLOG, notificationsListAdapter, NotificationsListAdapter.this, false);
                    return true;
                }
            });
        }else {
            holder.notification_status_container.setVisibility(View.GONE);
            holder.status_spoiler_container.setVisibility(View.GONE);
            holder.status_spoiler_mention_container.setVisibility(View.GONE);
            holder.card_status_container.setOnClickListener(null);
        }



        holder.notification_account_profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, ShowAccountActivity.class);
                Bundle b = new Bundle();
                notification.getAccount().setDisplay_name(notification.getAccount().getStored_displayname());
                b.putParcelable("account", notification.getAccount());
                intent.putExtras(b);
                context.startActivity(intent);
            }
        });



        holder.notification_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayConfirmationNotificationDialog(notification);
            }
        });
        holder.notification_account_username.setVisibility(View.GONE);

        final String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        final View attached = holder.status_more;
        holder.status_more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(context, attached);
                assert status != null;
                final boolean isOwner = status.getAccount().getId().equals(userId);
                popup.getMenuInflater()
                        .inflate(R.menu.option_toot, popup.getMenu());
                popup.getMenu().findItem(R.id.action_timed_mute).setVisible(false);
                if( status.getVisibility().equals("private") || status.getVisibility().equals("direct")){
                    popup.getMenu().findItem(R.id.action_mention).setVisible(false);
                }
                if( popup.getMenu().findItem(R.id.action_redraft) != null)
                    popup.getMenu().findItem(R.id.action_redraft).setVisible(false);
                if( popup.getMenu().findItem(R.id.action_translate) != null)
                    popup.getMenu().findItem(R.id.action_translate).setVisible(false);
                final String[] stringArrayConf;
                if( isOwner) {
                    popup.getMenu().findItem(R.id.action_block).setVisible(false);
                    popup.getMenu().findItem(R.id.action_mute).setVisible(false);
                    popup.getMenu().findItem(R.id.action_report).setVisible(false);
                    stringArrayConf =  context.getResources().getStringArray(R.array.more_action_owner_confirm);
                }else {
                    popup.getMenu().findItem(R.id.action_remove).setVisible(false);
                    stringArrayConf =  context.getResources().getStringArray(R.array.more_action_confirm);
                }
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        AlertDialog.Builder builderInner;
                        final API.StatusAction doAction;
                        switch (item.getItemId()) {
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
                            case R.id.action_mute:
                                builderInner = new AlertDialog.Builder(context, style);
                                builderInner.setTitle(stringArrayConf[0]);
                                doAction = API.StatusAction.MUTE;
                                break;
                            case R.id.action_open_browser:
                                Helper.openBrowser(context, status.getUrl());
                                return true;
                            case R.id.action_block:
                                builderInner = new AlertDialog.Builder(context, style);
                                builderInner.setTitle(stringArrayConf[1]);
                                doAction = API.StatusAction.BLOCK;
                                break;
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
                                String content;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                    content = Html.fromHtml(status.getContent(), Html.FROM_HTML_MODE_LEGACY).toString();
                                else
                                    //noinspection deprecation
                                    content = Html.fromHtml(status.getContent()).toString();
                                ClipData clip = ClipData.newPlainText(Helper.CLIP_BOARD, content);
                                assert clipboard != null;
                                clipboard.setPrimaryClip(clip);
                                Toasty.info(context,context.getString(R.string.clipboard),Toast.LENGTH_LONG).show();
                                return true;
                            case R.id.action_share:
                                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.shared_via));
                                sendIntent.putExtra(Intent.EXTRA_TEXT, status.getUrl());
                                sendIntent.setType("text/plain");
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_with)));
                                return true;
                            case R.id.action_mention:
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        String name = "@"+(status.getReblog()!=null?status.getReblog().getAccount().getAcct():status.getAccount().getAcct());
                                        if( name.split("@", -1).length - 1 == 1)
                                            name = name + "@" + getLiveInstance(context);
                                        Bitmap bitmap = Helper.convertTootIntoBitmap(context, name, holder.notification_status_content);
                                        Intent intent = new Intent(context, TootActivity.class);
                                        Bundle b = new Bundle();
                                        String fname = "tootmention_" + status.getId() +".jpg";
                                        File file = new File (context.getCacheDir() + "/", fname);
                                        if (file.exists ()) //noinspection ResultOfMethodCallIgnored
                                            file.delete ();
                                        try {
                                            FileOutputStream out = new FileOutputStream(file);
                                            assert bitmap != null;
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                                            out.flush();
                                            out.close();
                                        } catch (Exception ignored) {}
                                        b.putString("fileMention", fname);
                                        b.putString("tootMention", (status.getReblog() != null)?status.getReblog().getAccount().getAcct():status.getAccount().getAcct());
                                        b.putString("urlMention", (status.getReblog() != null)?status.getReblog().getUrl():status.getUrl());
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
                        if( doAction == API.StatusAction.REPORT){
                            input = new EditText(context);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                            input.setLayoutParams(lp);
                            builderInner.setView(input);
                        }
                        builderInner.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int which) {
                                dialog.dismiss();
                            }
                        });
                        final EditText finalInput = input;
                        builderInner.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int which) {
                                if(doAction ==  API.StatusAction.UNSTATUS ){
                                    String targetedId = status.getId();
                                    new PostActionAsyncTask(context, doAction, targetedId, NotificationsListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }else if(doAction ==  API.StatusAction.REPORT ){
                                    String comment = null;
                                    if( finalInput.getText() != null)
                                        comment = finalInput.getText().toString();
                                    new PostActionAsyncTask(context, doAction, status.getId(), status, comment, NotificationsListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }else{
                                    String targetedId = status.getAccount().getId();
                                    new PostActionAsyncTask(context, doAction, targetedId, NotificationsListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

        holder.notification_status_content.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP && !view.hasFocus()) {
                    try{view.requestFocus();}catch (Exception ignored){}
                }
                return false;
            }
        });
        if( theme == Helper.THEME_LIGHT) {
            holder.status_show_more.setTextColor(ContextCompat.getColor(context, R.color.white));
        }

        //Profile picture
        Helper.loadGiF(context, notification.getAccount().getAvatar(), holder.notification_account_profile);

    }

    private void notifyNotificationChanged(Notification notification){
        for (int i = 0; i < notificationsListAdapter.getItemCount(); i++) {
            if (notificationsListAdapter.getItemAt(i) != null && notificationsListAdapter.getItemAt(i).getId().equals(notification.getId())) {
                try {
                    notificationsListAdapter.notifyItemChanged(i);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }


    private Notification getItemAt(int position){
        if( notifications.size() > position)
            return notifications.get(position);
        else
            return null;
    }

    /**
     * Display a validation message for notification deletion
     * @param notification Notification
     */
    private void displayConfirmationNotificationDialog(final Notification notification){
        final ArrayList seletedItems = new ArrayList();

        AlertDialog dialog = new AlertDialog.Builder(context, style)
                .setTitle(R.string.delete_notification_ask)
                .setMultiChoiceItems(new String[]{context.getString(R.string.delete_notification_ask_all)}, null, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            //noinspection unchecked
                            seletedItems.add(indexSelected);
                        } else  {
                            if (seletedItems.contains(indexSelected))
                                seletedItems.remove(Integer.valueOf(indexSelected));
                        }

                    }
                }).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (seletedItems.size() > 0)
                            new PostNotificationsAsyncTask(context, null, NotificationsListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        else
                            new PostNotificationsAsyncTask(context, notification.getId(), NotificationsListAdapter.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        dialog.dismiss();
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }

    @Override
    public void onPostAction(int statusCode, API.StatusAction statusAction, String targetedId, Error error) {
        if( error != null){
            Toasty.error(context, error.getError(),Toast.LENGTH_LONG).show();
            return;
        }
        Helper.manageMessageStatusCode(context, statusCode, statusAction);
        //When muting or blocking an account, its status are removed from the list
        List<Notification> notificationsToRemove = new ArrayList<>();
        if( statusAction == API.StatusAction.MUTE || statusAction == API.StatusAction.BLOCK){
            for(Notification notification: notifications){
                if( notification.getType().equals("mention") && notification.getAccount().getId().equals(targetedId))
                    notificationsToRemove.add(notification);
            }
            notifications.removeAll(notificationsToRemove);
            notificationsListAdapter.notifyDataSetChanged();
        }
        if( targetedId != null ) {
            if (statusAction == API.StatusAction.REBLOG) {
                int position = 0;
                for (Notification notification : notifications) {
                    if (notification.getStatus() != null && notification.getStatus().getId().equals(targetedId)) {
                        notification.getStatus().setReblogs_count(notification.getStatus().getReblogs_count() + 1);
                        notificationsListAdapter.notifyItemChanged(position);
                        break;
                    }

                }
            } else if (statusAction == API.StatusAction.UNREBLOG) {
                int position = 0;
                for (Notification notification : notifications) {
                    if (notification.getStatus() != null && notification.getStatus().getId().equals(targetedId)) {
                        if (notification.getStatus().getReblogs_count() - 1 >= 0)
                            notification.getStatus().setReblogs_count(notification.getStatus().getReblogs_count() - 1);
                        notificationsListAdapter.notifyItemChanged(position);
                        break;
                    }
                    position++;
                }
            } else if (statusAction == API.StatusAction.FAVOURITE) {
                int position = 0;
                for (Notification notification : notifications) {
                    if (notification.getStatus() != null && notification.getStatus().getId().equals(targetedId)) {
                        notification.getStatus().setFavourites_count(notification.getStatus().getFavourites_count() + 1);
                        notificationsListAdapter.notifyItemChanged(position);
                        break;
                    }
                    position++;
                }
            } else if (statusAction == API.StatusAction.UNFAVOURITE) {
                int position = 0;
                for (Notification notification : notifications) {
                    if (notification.getStatus() != null && notification.getStatus().getId().equals(targetedId)) {
                        if (notification.getStatus().getFavourites_count() - 1 >= 0)
                            notification.getStatus().setFavourites_count(notification.getStatus().getFavourites_count() - 1);
                        notificationsListAdapter.notifyItemChanged(position);
                        break;
                    }
                    position++;
                }
            }
        }
    }

    @Override
    public void onPostNotificationsAction(APIResponse apiResponse, String targetedId) {
        if(apiResponse.getError() != null){
            Toasty.error(context, context.getString(R.string.toast_error),Toast.LENGTH_LONG).show();
            return;
        }
        if( targetedId != null){
            int position = 0;
            for (Notification notif : notifications) {
                if (notif.getId().equals(targetedId)) {
                    notifications.remove(notif);
                    notificationsListAdapter.notifyItemRemoved(position);
                    break;
                }
                position++;
            }
            Toasty.success(context,context.getString(R.string.delete_notification),Toast.LENGTH_LONG).show();
        }else{
            int size = notifications.size();
            notifications.clear();
            notificationsListAdapter.notifyItemRangeRemoved(0, size);
            Toasty.success(context,context.getString(R.string.delete_notification_all),Toast.LENGTH_LONG).show();
        }

    }

    private void loadAttachments(final Notification notification, ViewHolder holder){
        List<Attachment> attachments = notification.getStatus().getMedia_attachments();
        if( attachments != null && attachments.size() > 0){
            int i = 0;
            holder.status_document_container.setVisibility(View.VISIBLE);
            if( attachments.size() == 1){
                holder.status_container2.setVisibility(View.GONE);
                if( attachments.get(0).getUrl().trim().contains("missing.png"))
                    holder.status_document_container.setVisibility(View.GONE);
            }else if(attachments.size() == 2){
                holder.status_container2.setVisibility(View.VISIBLE);
                holder.status_container3.setVisibility(View.GONE);
                holder.status_prev4_container.setVisibility(View.GONE);
                if( attachments.get(1).getUrl().trim().contains("missing.png"))
                    holder.status_container2.setVisibility(View.GONE);
            }else if( attachments.size() == 3){
                holder.status_container2.setVisibility(View.VISIBLE);
                holder.status_container3.setVisibility(View.VISIBLE);
                holder.status_prev4_container.setVisibility(View.GONE);
                if( attachments.get(2).getUrl().trim().contains("missing.png"))
                    holder.status_container3.setVisibility(View.GONE);
            }else {
                holder.status_container2.setVisibility(View.VISIBLE);
                holder.status_container3.setVisibility(View.VISIBLE);
                holder.status_prev4_container.setVisibility(View.VISIBLE);
                if( attachments.get(2).getUrl().trim().contains("missing.png"))
                    holder.status_prev4_container.setVisibility(View.GONE);
            }
            int position = 1;
            for(final Attachment attachment: attachments){
                ImageView imageView;
                if( i == 0) {
                    imageView = holder.status_prev1;
                    if( attachment.getType().equals("image"))
                        holder.status_prev1_play.setVisibility(View.GONE);
                    else
                        holder.status_prev1_play.setVisibility(View.VISIBLE);
                }else if( i == 1) {
                    imageView = holder.status_prev2;
                    if( attachment.getType().equals("image"))
                        holder.status_prev2_play.setVisibility(View.GONE);
                    else
                        holder.status_prev2_play.setVisibility(View.VISIBLE);
                }else if(i == 2) {
                    imageView = holder.status_prev3;
                    if( attachment.getType().equals("image"))
                        holder.status_prev3_play.setVisibility(View.GONE);
                    else
                        holder.status_prev3_play.setVisibility(View.VISIBLE);
                }else {
                    imageView = holder.status_prev4;
                    if( attachment.getType().equals("image"))
                        holder.status_prev4_play.setVisibility(View.GONE);
                    else
                        holder.status_prev4_play.setVisibility(View.VISIBLE);
                }
                String url = attachment.getPreview_url();
                if( url == null || url.trim().equals(""))
                    url = attachment.getUrl();
                if( !url.trim().contains("missing.png"))
                    Glide.with(imageView.getContext())
                            .load(url)
                            .into(imageView);
                final int finalPosition = position;
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, MediaActivity.class);
                        Bundle b = new Bundle();
                        intent.putParcelableArrayListExtra("mediaArray", notification.getStatus().getMedia_attachments());
                        b.putInt("position", finalPosition);
                        intent.putExtras(b);
                        context.startActivity(intent);
                    }
                });
                i++;
                position++;
            }
        }else{
            holder.status_document_container.setVisibility(View.GONE);
        }
        holder.status_show_more.setVisibility(View.GONE);
    }


    @Override
    public void onRetrieveEmoji(Status status, boolean fromTranslation) {
        if( !status.isEmojiFound()) {
            for (int i = 0; i < notificationsListAdapter.getItemCount(); i++) {
                if (notificationsListAdapter.getItemAt(i) != null && notificationsListAdapter.getItemAt(i).getStatus() != null &&  notificationsListAdapter.getItemAt(i).getStatus().getId().equals(status.getId())) {
                    if( notificationsListAdapter.getItemAt(i).getStatus() != null) {
                        notificationsListAdapter.getItemAt(i).getStatus().setEmojiFound(true);
                        try {
                            notificationsListAdapter.notifyItemChanged(i);
                        }catch (Exception ignored){}
                    }
                }
            }

        }
    }

    @Override
    public void onRetrieveSearchEmoji(List<Emojis> emojis) {

    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        final NotificationsListAdapter.ViewHolder viewHolder = (NotificationsListAdapter.ViewHolder) holder;
        // Bug workaround for losing text selection ability, see:
        // https://code.google.com/p/android/issues/detail?id=208169
        viewHolder.notification_status_content.setEnabled(false);
        viewHolder.notification_status_content.setEnabled(true);
        viewHolder.status_spoiler.setEnabled(false);
        viewHolder.status_spoiler.setEnabled(true);
    }

    @Override
    public void onRetrieveEmojiAccount(Account account) {
        for( Notification notification: notifications){
            if( notification.getAccount().equals(account)){
                notifyNotificationChanged(notification);
            }
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        FrameLayout card_status_container;
        CustomTextView notification_status_content;
        TextView notification_type;
        LinearLayout status_spoiler_container;
        CustomTextView status_spoiler;
        Button status_spoiler_button;
        TextView notification_account_username;
        ImageView notification_account_profile;
        ImageView notification_delete;
        TextView status_favorite_count;
        TextView status_reblog_count;
        TextView status_date;
        TextView status_reply;
        LinearLayout status_document_container;
        LinearLayout status_action_container;
        Button status_show_more;
        ImageView status_prev1;
        ImageView status_prev2;
        ImageView status_prev3;
        ImageView status_prev4;
        ImageView status_prev1_play;
        ImageView status_prev2_play;
        ImageView status_prev3_play;
        ImageView status_prev4_play;
        ImageView status_more;
        RelativeLayout status_prev4_container;
        LinearLayout status_container2;
        LinearLayout status_container3;
        LinearLayout notification_status_container;
        RelativeLayout main_container_trans;
        ImageView status_privacy;
        LinearLayout status_spoiler_mention_container;
        TextView status_mention_spoiler;
        SparkButton spark_button_fav, spark_button_reblog;
        public View getView(){
            return itemView;
        }

        public ViewHolder(View itemView) {
            super(itemView);
            card_status_container = itemView.findViewById(R.id.card_status_container);
            main_container_trans = itemView.findViewById(R.id.container_trans);
            notification_status_container = itemView.findViewById(R.id.notification_status_container);
            status_document_container = itemView.findViewById(R.id.status_document_container);
            notification_status_content = itemView.findViewById(R.id.notification_status_content);
            notification_account_username = itemView.findViewById(R.id.notification_account_username);
            notification_type = itemView.findViewById(R.id.notification_type);
            notification_account_profile = itemView.findViewById(R.id.notification_account_profile);
            status_favorite_count = itemView.findViewById(R.id.status_favorite_count);
            status_reblog_count = itemView.findViewById(R.id.status_reblog_count);
            status_date = itemView.findViewById(R.id.status_date);
            status_reply = itemView.findViewById(R.id.status_reply);
            status_privacy = itemView.findViewById(R.id.status_privacy);
            notification_delete = itemView.findViewById(R.id.notification_delete);
            status_show_more = itemView.findViewById(R.id.status_show_more);
            status_prev1 = itemView.findViewById(R.id.status_prev1);
            status_prev2 = itemView.findViewById(R.id.status_prev2);
            status_prev3 = itemView.findViewById(R.id.status_prev3);
            status_prev4 = itemView.findViewById(R.id.status_prev4);
            status_prev1_play = itemView.findViewById(R.id.status_prev1_play);
            status_prev2_play = itemView.findViewById(R.id.status_prev2_play);
            status_prev3_play = itemView.findViewById(R.id.status_prev3_play);
            status_prev4_play = itemView.findViewById(R.id.status_prev4_play);
            status_container2 = itemView.findViewById(R.id.status_container2);
            status_container3 = itemView.findViewById(R.id.status_container3);
            status_prev4_container = itemView.findViewById(R.id.status_prev4_container);
            status_action_container = itemView.findViewById(R.id.status_action_container);
            status_more = itemView.findViewById(R.id.status_more);
            status_spoiler_container = itemView.findViewById(R.id.status_spoiler_container);
            status_spoiler = itemView.findViewById(R.id.status_spoiler);
            status_spoiler_button = itemView.findViewById(R.id.status_spoiler_button);
            status_spoiler_mention_container = itemView.findViewById(R.id.status_spoiler_mention_container);
            status_mention_spoiler = itemView.findViewById(R.id.status_mention_spoiler);
            spark_button_fav =  itemView.findViewById(R.id.spark_button_fav);
            spark_button_reblog =  itemView.findViewById(R.id.spark_button_reblog);
        }
    }

}