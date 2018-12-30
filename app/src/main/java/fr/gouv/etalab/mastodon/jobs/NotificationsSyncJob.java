package fr.gouv.etalab.mastodon.jobs;
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import fr.gouv.etalab.mastodon.activities.MainActivity;
import fr.gouv.etalab.mastodon.client.API;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Notification;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;

import static fr.gouv.etalab.mastodon.helper.Helper.INTENT_ACTION;
import static fr.gouv.etalab.mastodon.helper.Helper.INTENT_TARGETED_ACCOUNT;
import static fr.gouv.etalab.mastodon.helper.Helper.NOTIFICATION_INTENT;
import static fr.gouv.etalab.mastodon.helper.Helper.PREF_INSTANCE;
import static fr.gouv.etalab.mastodon.helper.Helper.PREF_KEY_ID;
import static fr.gouv.etalab.mastodon.helper.Helper.canNotify;
import static fr.gouv.etalab.mastodon.helper.Helper.notify_user;


/**
 * Created by Thomas on 29/04/2017.
 * Notifications refresh job
 */

public class NotificationsSyncJob extends Job {

    static final String NOTIFICATION_REFRESH = "job_notification";
    static {
        Helper.installProvider();
    }

    @NonNull
    @Override
    protected Result onRunJob(@NonNull Params params) {
        //Code refresh here
        callAsynchronousTask();
        return Result.SUCCESS;
    }


    public static int schedule(boolean updateCurrent) {

        Set<JobRequest> jobRequests = JobManager.instance().getAllJobRequestsForTag(NOTIFICATION_REFRESH);
        if (!jobRequests.isEmpty() && !updateCurrent) {
            return jobRequests.iterator().next().getJobId();
        }

        int jobRequestschedule = -1;
        try {
            jobRequestschedule = new JobRequest.Builder(NotificationsSyncJob.NOTIFICATION_REFRESH)
                    .setPeriodic(TimeUnit.MINUTES.toMillis(Helper.MINUTES_BETWEEN_NOTIFICATIONS_REFRESH), TimeUnit.MINUTES.toMillis(5))
                    .setUpdateCurrent(updateCurrent)
                    .setRequiredNetworkType(JobRequest.NetworkType.METERED)
                    .setRequirementsEnforced(false)
                    .build()
                    .schedule();
        }catch (Exception ignored){}

        return jobRequestschedule;
    }


    /**
     * Task in background starts here.
     */
    private void callAsynchronousTask() {
        if( !canNotify(getContext()))
            return;
        SQLiteDatabase db = Sqlite.getInstance(getContext(), Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
        //If an Internet connection and user agrees with notification refresh
        final SharedPreferences sharedpreferences = getContext().getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        //Check which notifications the user wants to see
        boolean notif_follow = sharedpreferences.getBoolean(Helper.SET_NOTIF_FOLLOW, true);
        boolean notif_add = sharedpreferences.getBoolean(Helper.SET_NOTIF_ADD, true);
        boolean notif_mention = sharedpreferences.getBoolean(Helper.SET_NOTIF_MENTION, true);
        boolean notif_share = sharedpreferences.getBoolean(Helper.SET_NOTIF_SHARE, true);
        //User disagree with all notifications
        if( !notif_follow && !notif_add && !notif_mention && !notif_share)
            return; //Nothing is done
        //No account connected, the service is stopped
        if(!Helper.isLoggedIn(getContext()))
            return;
        //If WIFI only and on WIFI OR user defined any connections to use the service.
        if(!sharedpreferences.getBoolean(Helper.SET_WIFI_ONLY, false) || Helper.isOnWIFI(getContext())) {
            List<Account> accounts = new AccountDAO(getContext(),db).getAllAccount();
            //It means there is no user in DB.
            if( accounts == null )
                return;
            //Retrieve users in db that owner has.
            for (Account account: accounts) {
                API api = new API(getContext(), account.getInstance(), account.getToken());
                APIResponse apiResponse = api.getNotificationsSince(null, false);
                onRetrieveNotifications(apiResponse, account);
            }
        }
    }


    private void onRetrieveNotifications(APIResponse apiResponse, final Account account) {
        List<Notification> notificationsReceived = apiResponse.getNotifications();
        if( apiResponse.getError() != null || notificationsReceived == null || notificationsReceived.size() == 0 || account == null)
            return;
        final SharedPreferences sharedpreferences = getContext().getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean notif_follow = sharedpreferences.getBoolean(Helper.SET_NOTIF_FOLLOW, true);
        boolean notif_add = sharedpreferences.getBoolean(Helper.SET_NOTIF_ADD, true);
        boolean notif_mention = sharedpreferences.getBoolean(Helper.SET_NOTIF_MENTION, true);
        boolean notif_share = sharedpreferences.getBoolean(Helper.SET_NOTIF_SHARE, true);
        final String max_id = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), null);
        final  List<Notification> notifications = new ArrayList<>();
        int pos = 0;
        for(Notification notif: notificationsReceived){
            if( max_id == null || notif.getDate_id() > Long.parseLong(max_id) ) {
                notifications.add(pos, notif);
                pos++;
            }
        }
        if( notifications.size() == 0 )
            return;
        //No previous notifications in cache, so no notification will be sent
        int newFollows = 0;
        int newAdds = 0;
        int newMentions = 0;
        int newShare = 0;
        String notificationUrl = null;
        String title = null;
        final String message;
        String targeted_account = null;
        Helper.NotifType notifType = Helper.NotifType.MENTION;

        for(Notification notification: notifications){
            switch (notification.getType()){
                case "mention":
                    notifType = Helper.NotifType.MENTION;
                    if(notif_mention){
                        newMentions++;
                        if( notificationUrl == null){
                            notificationUrl = notification.getAccount().getAvatar();
                            if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0 )
                                title = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),getContext().getString(R.string.notif_mention));
                            else
                                title = String.format("@%s %s", notification.getAccount().getAcct(),getContext().getString(R.string.notif_mention));
                        }
                    }
                    break;
                case "reblog":
                    notifType = Helper.NotifType.BOOST;
                    if(notif_share){
                        newShare++;
                        if( notificationUrl == null){
                            notificationUrl = notification.getAccount().getAvatar();
                            if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0 )
                                title = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),getContext().getString(R.string.notif_reblog));
                            else
                                title = String.format("@%s %s", notification.getAccount().getAcct(),getContext().getString(R.string.notif_reblog));

                        }
                    }
                    break;
                case "favourite":
                    notifType = Helper.NotifType.FAV;
                    if(notif_add){
                        newAdds++;
                        if( notificationUrl == null){
                            notificationUrl = notification.getAccount().getAvatar();
                            if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0 )
                                title = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),getContext().getString(R.string.notif_favourite));
                            else
                                title = String.format("@%s %s", notification.getAccount().getAcct(),getContext().getString(R.string.notif_favourite));
                        }
                    }
                    break;
                case "follow":
                    notifType = Helper.NotifType.FOLLLOW;
                    if(notif_follow){
                        newFollows++;
                        if( notificationUrl == null){
                            notificationUrl = notification.getAccount().getAvatar();
                            if( notification.getAccount().getDisplay_name() != null && notification.getAccount().getDisplay_name().length() > 0 )
                                title = String.format("%s %s", Helper.shortnameToUnicode(notification.getAccount().getDisplay_name(), true),getContext().getString(R.string.notif_follow));
                            else
                                title = String.format("@%s %s", notification.getAccount().getAcct(),getContext().getString(R.string.notif_follow));
                            targeted_account = notification.getAccount().getId();
                        }
                    }
                    break;
                default:
            }
        }

        int allNotifCount = newFollows + newAdds + newMentions + newShare;
        if( allNotifCount > 0){
            //Some others notification
            int other = allNotifCount -1;
            if(other > 0 )
                message = getContext().getResources().getQuantityString(R.plurals.other_notifications, other, other);
            else
                message = "";
            final Intent intent = new Intent(getContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK );
            intent.putExtra(INTENT_ACTION, NOTIFICATION_INTENT);
            intent.putExtra(PREF_KEY_ID, account.getId());
            if( targeted_account != null && notifType == Helper.NotifType.FOLLLOW)
                intent.putExtra(INTENT_TARGETED_ACCOUNT, targeted_account);
            intent.putExtra(PREF_INSTANCE, account.getInstance());
            long notif_id = Long.parseLong(account.getId());
            final int notificationId = ((notif_id + 1) > 2147483647) ? (int) (2147483647 - notif_id - 1) : (int) (notif_id + 1);
            if( notificationUrl != null ){


                final String finalTitle = title;
                Handler mainHandler = new Handler(Looper.getMainLooper());

                final String finalNotificationUrl = notificationUrl;
                Helper.NotifType finalNotifType = notifType;
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Glide.with(getContext())
                                .asBitmap()
                                .load(finalNotificationUrl)
                                .listener(new RequestListener<Bitmap>() {

                                    @Override
                                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target target, boolean isFirstResource) {
                                        notify_user(getContext(), intent, notificationId, BitmapFactory.decodeResource(getContext().getResources(),
                                                R.drawable.mastodonlogo), finalNotifType, finalTitle, message);
                                        String lastNotif = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), null);
                                        if( lastNotif == null || Long.parseLong(notifications.get(0).getId()) > Long.parseLong(lastNotif)){
                                            SharedPreferences.Editor editor = sharedpreferences.edit();
                                            editor.putString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), String.valueOf(notifications.get(0).getDate_id()));
                                            editor.apply();
                                        }
                                        return false;
                                    }
                                })
                                .into(new SimpleTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                                        notify_user(getContext(), intent, notificationId, resource, finalNotifType, finalTitle, message);
                                        String lastNotif = sharedpreferences.getString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), null);
                                        if( lastNotif == null || Long.parseLong(notifications.get(0).getId()) > Long.parseLong(lastNotif)){
                                            SharedPreferences.Editor editor = sharedpreferences.edit();
                                            editor.putString(Helper.LAST_NOTIFICATION_MAX_ID + account.getId() + account.getInstance(), String.valueOf(notifications.get(0).getDate_id()));
                                            editor.apply();
                                        }
                                    }
                                });
                    }
                };
                mainHandler.post(myRunnable);

            }

        }
    }

}