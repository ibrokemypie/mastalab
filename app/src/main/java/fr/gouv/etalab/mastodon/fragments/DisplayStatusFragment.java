package fr.gouv.etalab.mastodon.fragments;
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import es.dmoral.toasty.Toasty;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.activities.BaseMainActivity;
import fr.gouv.etalab.mastodon.activities.MainActivity;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAfterBookmarkAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveMissingFeedsAsyncTask;
import fr.gouv.etalab.mastodon.asynctasks.RetrievePeertubeSearchAsyncTask;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Conversation;
import fr.gouv.etalab.mastodon.client.Entities.Peertube;
import fr.gouv.etalab.mastodon.client.Entities.RemoteInstance;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.client.Entities.TagTimeline;
import fr.gouv.etalab.mastodon.drawers.PeertubeAdapter;
import fr.gouv.etalab.mastodon.drawers.StatusListAdapter;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsAfterBookmarkInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveMissingFeedsInterface;
import fr.gouv.etalab.mastodon.services.StreamingFederatedTimelineService;
import fr.gouv.etalab.mastodon.services.StreamingHomeTimelineService;
import fr.gouv.etalab.mastodon.services.StreamingLocalTimelineService;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.InstancesDAO;
import fr.gouv.etalab.mastodon.sqlite.SearchDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;
import fr.gouv.etalab.mastodon.sqlite.TempMuteDAO;


/**
 * Created by Thomas on 24/04/2017.
 * Fragment to display content related to status
 */
public class DisplayStatusFragment extends Fragment implements OnRetrieveFeedsInterface, OnRetrieveMissingFeedsInterface, OnRetrieveFeedsAfterBookmarkInterface {


    private boolean flag_loading;
    private Context context;
    private AsyncTask<Void, Void, Void> asyncTask;
    private StatusListAdapter statusListAdapter;
    private PeertubeAdapter peertubeAdapater;
    private String max_id;
    private List<Status> statuses;
    private List<Peertube> peertubes;
    private RetrieveFeedsAsyncTask.Type type;
    private RelativeLayout mainLoader, nextElementLoader, textviewNoAction;
    private boolean firstLoad;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String targetedId;
    private String tag;
    private RecyclerView lv_status;
    private boolean showMediaOnly, showPinned, showReply;
    private Intent streamingHomeIntent, streamingFederatedIntent, streamingLocalIntent;
    LinearLayoutManager mLayoutManager;
    boolean firstTootsLoaded;
    private String userId, instance;
    private SharedPreferences sharedpreferences;
    private boolean isSwipped;
    private String remoteInstance;
    private List<String> mutedAccount;
    private String instanceType;
    private String search_peertube, remote_channel_name;
    private String initialBookMark;
    private Long initialBookMarkDate;
    private boolean fetchMoreButtonDisplayed;
    private TagTimeline tagTimeline;

    private String updatedBookMark;
    private Long updatedBookMarkDate;
    private Long lastReadToot;

    public DisplayStatusFragment(){
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_status, container, false);
        statuses = new ArrayList<>();
        peertubes = new ArrayList<>();
        context = getContext();
        Bundle bundle = this.getArguments();
        showMediaOnly = false;
        //Will allow to load first toots if bookmark != null
        firstTootsLoaded = false;
        fetchMoreButtonDisplayed = false;
        showPinned = false;
        showReply = false;
        tagTimeline = null;
        if (bundle != null) {
            type = (RetrieveFeedsAsyncTask.Type) bundle.get("type");
            targetedId = bundle.getString("targetedId", null);
            tag = bundle.getString("tag", null);
            showMediaOnly = bundle.getBoolean("showMediaOnly",false);
            showPinned = bundle.getBoolean("showPinned",false);
            showReply = bundle.getBoolean("showReply",false);
            remoteInstance = bundle.getString("remote_instance", "");
            search_peertube = bundle.getString("search_peertube", null);
            remote_channel_name = bundle.getString("remote_channel_name", null);

        }
        SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
        if( !remoteInstance.equals("")){
            List<RemoteInstance> remoteInstanceObj = new InstancesDAO(context, db).getInstanceByName(remoteInstance);
            if( remoteInstanceObj != null && remoteInstanceObj.size() > 0)
                instanceType = remoteInstanceObj.get(0).getType();
        }
        isSwipped = false;
        max_id = null;
        flag_loading = true;
        firstLoad = true;
        initialBookMark = null;

        assert context != null;
        sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean isOnWifi = Helper.isOnWIFI(context);
        swipeRefreshLayout = rootView.findViewById(R.id.swipeContainer);
        lv_status = rootView.findViewById(R.id.lv_status);
        mainLoader =  rootView.findViewById(R.id.loader);
        nextElementLoader = rootView.findViewById(R.id.loading_next_status);
        textviewNoAction =  rootView.findViewById(R.id.no_action);
        mainLoader.setVisibility(View.VISIBLE);
        nextElementLoader.setVisibility(View.GONE);
        userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        instance = sharedpreferences.getString(Helper.PREF_INSTANCE, context!=null?Helper.getLiveInstance(context):null);
        Account account = new AccountDAO(context, db).getAccountByID(userId);
        mutedAccount = new TempMuteDAO(context, db).getAllTimeMuted(account);

        //For Home timeline, fetch stored values for bookmark and last read toot
        if( type == RetrieveFeedsAsyncTask.Type.HOME) {
            initialBookMark = sharedpreferences.getString(Helper.BOOKMARK_ID + userId + instance, null);
            initialBookMarkDate = sharedpreferences.getLong(Helper.BOOKMARK_ID + userId + instance, 0);
            lastReadToot = sharedpreferences.getLong(Helper.LAST_READ_TOOT_ID + userId + instance, 0);
        }
        if( type == RetrieveFeedsAsyncTask.Type.TAG && tag != null) {
            BaseMainActivity.displayPeertube = null;
            List<TagTimeline> tagTimelines = new SearchDAO(context, db).getTimelineInfo(tag);
            if( tagTimelines != null && tagTimelines.size() > 0) {
                tagTimeline = tagTimelines.get(0);
                statusListAdapter = new StatusListAdapter(context, tagTimeline, targetedId, isOnWifi, this.statuses);
                lv_status.setAdapter(statusListAdapter);
            }
        }else if( search_peertube == null && (instanceType == null || instanceType.equals("MASTODON"))) {
            BaseMainActivity.displayPeertube = null;
            statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, this.statuses);
            lv_status.setAdapter(statusListAdapter);
        }else {
            BaseMainActivity.displayPeertube = remoteInstance;
            peertubeAdapater = new PeertubeAdapter(context, remoteInstance, this.peertubes);
            lv_status.setAdapter(peertubeAdapater);
        }
        mLayoutManager = new LinearLayoutManager(context);
        lv_status.setLayoutManager(mLayoutManager);



        if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE  && search_peertube != null)
            ((Activity)context).setTitle(remoteInstance + " - " + search_peertube);
        if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE  && remote_channel_name != null)
            ((Activity)context).setTitle(remote_channel_name + " - " + remoteInstance);
        lv_status.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
            {
                if (type != RetrieveFeedsAsyncTask.Type.ART && context instanceof  BaseMainActivity ) {
                    if( dy < 0 && !((BaseMainActivity)context).getFloatingVisibility() )
                        ((BaseMainActivity) context).manageFloatingButton(true);
                    if( dy > 0 && ((BaseMainActivity)context).getFloatingVisibility() )
                        ((BaseMainActivity) context).manageFloatingButton(false);
                }
                int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                if(dy > 0){
                    int visibleItemCount = mLayoutManager.getChildCount();
                    int totalItemCount = mLayoutManager.getItemCount();
                    if(firstVisibleItem + visibleItemCount == totalItemCount && context != null) {
                        if(!flag_loading ) {
                            flag_loading = true;
                            if( type == RetrieveFeedsAsyncTask.Type.USER)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, showReply, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else if( type == RetrieveFeedsAsyncTask.Type.TAG)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE ) {
                                if( search_peertube == null) {
                                    if( remote_channel_name == null)
                                        asyncTask = new RetrieveFeedsAsyncTask(context, type, remoteInstance, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    else
                                        asyncTask = new RetrieveFeedsAsyncTask(context, remoteInstance, remote_channel_name, null,DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }
                                else
                                    asyncTask = new RetrievePeertubeSearchAsyncTask(context, remoteInstance, search_peertube, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }else{
                                if( type == RetrieveFeedsAsyncTask.Type.HOME){
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }else {
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }
                            }
                            nextElementLoader.setVisibility(View.VISIBLE);
                        }
                    } else {
                        nextElementLoader.setVisibility(View.GONE);
                    }
                }
                if(type == RetrieveFeedsAsyncTask.Type.HOME && statuses != null && statuses.size() > firstVisibleItem && firstVisibleItem >= 0) {
                    Long bookmarkL = (statuses.get(firstVisibleItem).getDate_id()) + 1;
                    updatedBookMark = statuses.get(firstVisibleItem).getId();
                    updatedBookMarkDate = statuses.get(firstVisibleItem).getDate_id();
                    if( lastReadToot == null || bookmarkL > (lastReadToot)) //Last read toot, only incremented if the id of the toot is greater than the recorded one
                        lastReadToot = Long.valueOf(bookmarkL);
                }
            }
        });


        if( instanceType == null || instanceType.equals("MASTODON"))
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    if( type == RetrieveFeedsAsyncTask.Type.HOME)
                        MainActivity.countNewStatus = 0;
                    isSwipped = true;
                    if( type != RetrieveFeedsAsyncTask.Type.CONVERSATION)
                        retrieveMissingToots(null);
                    else if( statuses.size() > 0)
                        retrieveMissingToots(statuses.get(0).getId());

                }
            });
        else
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    if( peertubes.size() > 0) {
                        int size = peertubes.size();
                        isSwipped = true;
                        peertubes.clear();
                        peertubes = new ArrayList<>();
                        max_id = "0";
                        peertubeAdapater.notifyItemRangeRemoved(0, size);
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, remoteInstance, "0", DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                }
            });
        SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        int theme = sharedpreferences.getInt(Helper.SET_THEME, Helper.THEME_DARK);
        switch (theme){
            case Helper.THEME_LIGHT:
                swipeRefreshLayout.setColorSchemeResources(R.color.mastodonC4,
                        R.color.mastodonC2,
                        R.color.mastodonC3);
                swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.white));
                break;
            case Helper.THEME_DARK:
                swipeRefreshLayout.setColorSchemeResources(R.color.mastodonC4__,
                        R.color.mastodonC4,
                        R.color.mastodonC4);
                swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.mastodonC1_));
                break;
            case Helper.THEME_BLACK:
                swipeRefreshLayout.setColorSchemeResources(R.color.dark_icon,
                        R.color.mastodonC2,
                        R.color.mastodonC3);
                swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.black_3));
                break;
        }
        if( context != null) {
            if (type == RetrieveFeedsAsyncTask.Type.USER)
                asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, showReply,DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE ) {
                if( search_peertube == null) {
                    if( remote_channel_name == null)
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, remoteInstance, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    else
                        asyncTask = new RetrieveFeedsAsyncTask(context, remoteInstance, remote_channel_name, null,DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                else
                    asyncTask = new RetrievePeertubeSearchAsyncTask(context, remoteInstance, search_peertube, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }else {
                if( type == RetrieveFeedsAsyncTask.Type.HOME ){
                    if( context instanceof BaseMainActivity){
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, String.valueOf(initialBookMark),  DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                }else {
                    asyncTask = new RetrieveFeedsAsyncTask(context, type, null, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }else {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if( context != null){
                        if (type == RetrieveFeedsAsyncTask.Type.USER)
                            asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, showReply,DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                            asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        else if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE ) {
                            if( search_peertube == null) {
                                if( remote_channel_name == null)
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, remoteInstance, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                else
                                    asyncTask = new RetrieveFeedsAsyncTask(context, remoteInstance, remote_channel_name, null,DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                            else
                                asyncTask = new RetrievePeertubeSearchAsyncTask(context, remoteInstance, search_peertube, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }else {
                            if( type == RetrieveFeedsAsyncTask.Type.HOME ){
                                if( context instanceof BaseMainActivity){
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, String.valueOf(initialBookMark),DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                }
                            }else {
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, null, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        }
                    }
                }
            }, 500);
        }

        return rootView;
    }

    @Override
    public void onPause(){
        super.onPause();
        //Store bookmark on pause
        if (context instanceof BaseMainActivity && type == RetrieveFeedsAsyncTask.Type.HOME) {
            SharedPreferences.Editor editor = sharedpreferences.edit();
            if(updatedBookMark != null)
                editor.putString(Helper.BOOKMARK_ID + userId + instance, updatedBookMark);
                editor.putLong(Helper.BOOKMARK_DATE_ID + userId + instance, updatedBookMarkDate);
            if( lastReadToot != null)
                editor.putLong(Helper.LAST_READ_TOOT_ID + userId + instance, lastReadToot);
            if( lastReadToot != null || updatedBookMark != null)
                editor.apply();
        }
    }


    @Override
    public void onCreate(Bundle saveInstance)
    {
        super.onCreate(saveInstance);
    }



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroy (){
        super.onDestroy();
        if(asyncTask != null && asyncTask.getStatus() == AsyncTask.Status.RUNNING)
            asyncTask.cancel(true);
    }



    @Override
    public void onRetrieveFeeds(APIResponse apiResponse) {
        //hide loaders
        mainLoader.setVisibility(View.GONE);
        nextElementLoader.setVisibility(View.GONE);
        //handle other API error but discards 404 - error which can often happen due to toots which have been deleted
        if( apiResponse == null || (apiResponse.getError() != null && apiResponse.getError().getStatusCode() != 404) ){
            if( apiResponse == null)
                Toasty.error(context, context.getString(R.string.toast_error),Toast.LENGTH_LONG).show();
            else
                Toasty.error(context, apiResponse.getError().getError(),Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            flag_loading = false;
            return;
        }
        //For remote Peertube remote instances
        if( type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE && peertubeAdapater != null){
            int previousPosition = this.peertubes.size();
            if( max_id == null)
                max_id = "0";
            //max_id needs to work like an offset
            max_id = String.valueOf(Integer.valueOf(max_id) + 50);
            this.peertubes.addAll(apiResponse.getPeertubes());
            //If no item were inserted previously the adapter is created
            if( previousPosition == 0) {
                peertubeAdapater = new PeertubeAdapter(context, remoteInstance, this.peertubes);
                lv_status.setAdapter(peertubeAdapater);
            }else
                peertubeAdapater.notifyItemRangeInserted(previousPosition, apiResponse.getPeertubes().size());
            //remove handlers
            swipeRefreshLayout.setRefreshing(false);
            firstLoad = false;
            flag_loading = false;
        }else {
            //When Mastodon statuses have been fetched.
            if( type == RetrieveFeedsAsyncTask.Type.CONVERSATION ){ //Conversation timeline
                //this timeline is dealt differently because it is embedded in Conversation entity and  not directly in statuses
                List<Conversation> conversations = apiResponse.getConversations();
                //Statuses from conversation entity are retrieved
                List<Status> statusesConversations = new ArrayList<>();
                if( conversations != null) {
                    for (Conversation conversation : conversations) {
                        Status status = conversation.getLast_status();
                        if (status != null) {
                            status.setConversationId(conversation.getId());
                            List<String> ppConversation = new ArrayList<>();
                            for (Account account : conversation.getAccounts())
                                ppConversation.add(account.getAvatar());
                            status.setConversationProfilePicture(ppConversation);
                        }
                        statusesConversations.add(status);
                    }
                }
                apiResponse.setStatuses(statusesConversations);
            }
            int previousPosition = this.statuses.size();
            List<Status> statuses = apiResponse.getStatuses();
            //At this point all statuses are in "List<Status> statuses"
            max_id = apiResponse.getMax_id();
            //while max_id is different from null, there are some more toots to load when scrolling
            flag_loading = (max_id == null );
            //If it's the first load and the reply doesn't contain any toots, a message is displayed.
            if( firstLoad && (statuses == null || statuses.size() == 0)) {
                textviewNoAction.setVisibility(View.VISIBLE);
                lv_status.setVisibility(View.GONE);
            }else {
                lv_status.setVisibility(View.VISIBLE);
                textviewNoAction.setVisibility(View.GONE);
            }

            //First toot are loaded as soon as the bookmark has been retrieved
            //Only for the Home timeline

            if( type == RetrieveFeedsAsyncTask.Type.HOME && !firstTootsLoaded){
                asyncTask = new RetrieveFeedsAfterBookmarkAsyncTask(context, null, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                firstTootsLoaded = true;
            }
            //Let's deal with statuses
            if( statuses != null && statuses.size() > 0) {
                if ( statusListAdapter != null){
                    if( tagTimeline == null || !tagTimeline.isART() || (tagTimeline.isART() && tagTimeline.isNSFW())) {
                        this.statuses.addAll(statuses);
                        statusListAdapter.notifyItemRangeInserted(previousPosition, statuses.size());
                    }else { //If it's an Art timeline not allowing NSFW
                        ArrayList<Status> safeStatuses = new ArrayList<>();

                        for(Status status: statuses){
                            if( !status.isSensitive())
                            safeStatuses.add(status);
                        }
                        this.statuses.addAll(safeStatuses);
                        statusListAdapter.notifyItemRangeInserted(previousPosition, safeStatuses.size());
                    }
                }

            }
            swipeRefreshLayout.setRefreshing(false);
            firstLoad = false;

        }
    }

    /**
     * Deals with new status coming from the streaming api
     * @param status Status
     */
    public void refresh(Status status){
        //New data are available
        if (context == null)
            return;
        if( status.getId() != null && statuses != null && statuses.size() > 0 && statuses.get(0)!= null
                && status.getDate_id() > statuses.get(0).getDate_id()) {
            List<Status> tempTootResult = new ArrayList();
            tempTootResult.add(status);
            if( tempTootResult.size() > 0)
                status = tempTootResult.get(0);
            if (type == RetrieveFeedsAsyncTask.Type.HOME) {

                //Makes sure the status is not already displayed
                if( !statuses.contains(status)){
                    //Update the id of the last toot retrieved
                    MainActivity.lastHomeId = status.getId();
                    statuses.add(0, status);
                    if (!status.getAccount().getId().equals(userId)) {
                        MainActivity.countNewStatus++;
                    }
                    try {
                        ((MainActivity) context).updateHomeCounter();
                    }catch (Exception ignored){}
                    statusListAdapter.notifyItemInserted(0);
                    if (textviewNoAction.getVisibility() == View.VISIBLE)
                        textviewNoAction.setVisibility(View.GONE);
                }

            } else if (type == RetrieveFeedsAsyncTask.Type.PUBLIC || type == RetrieveFeedsAsyncTask.Type.LOCAL|| type == RetrieveFeedsAsyncTask.Type.DIRECT) {

                status.setNew(false);
                statuses.add(0, status);
                int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem > 0)
                    statusListAdapter.notifyItemInserted(0);
                else
                    statusListAdapter.notifyDataSetChanged();
                if (textviewNoAction.getVisibility() == View.VISIBLE)
                    textviewNoAction.setVisibility(View.GONE);

            }
        }
    }


    @Override
    public void onResume(){
        super.onResume();
        boolean liveNotifications = sharedpreferences.getBoolean(Helper.SET_LIVE_NOTIFICATIONS, true);
        int batteryProfile = sharedpreferences.getInt(Helper.SET_BATTERY_PROFILE, Helper.BATTERY_PROFILE_NORMAL);


        if (type == RetrieveFeedsAsyncTask.Type.HOME){
            if( getUserVisibleHint() ){
                statusListAdapter.updateMuted(mutedAccount);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + userId + instance, true);
                editor.apply();
                if(liveNotifications && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingHomeIntent = new Intent(context, StreamingHomeTimelineService.class);
                    try {
                        context.startService(streamingHomeIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        } else if( type == RetrieveFeedsAsyncTask.Type.PUBLIC){
            if( getUserVisibleHint() ){
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED + userId + instance, true);
                editor.apply();
                if(liveNotifications && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingFederatedIntent = new Intent(context, StreamingFederatedTimelineService.class);
                    try {
                        context.startService(streamingFederatedIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.LOCAL){

            if( getUserVisibleHint() ){
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL + userId + instance, true);
                editor.apply();
                if( liveNotifications  && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingLocalIntent = new Intent(context, StreamingLocalTimelineService.class);
                    try {
                        context.startService(streamingLocalIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.DIRECT){
            if( getUserVisibleHint() ){
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.CONVERSATION){
            if( getUserVisibleHint() ){
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.TAG){
            if( getUserVisibleHint() ){
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }
    }

    /**
     * Called from main activity in onResume to retrieve missing toots (home timeline)
     * @param sinceId String
     */
    public void retrieveMissingToots(String sinceId){

        if( type == RetrieveFeedsAsyncTask.Type.HOME)
            asyncTask = new RetrieveFeedsAfterBookmarkAsyncTask(context, null, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        if (type == RetrieveFeedsAsyncTask.Type.REMOTE_INSTANCE )
            asyncTask = new RetrieveMissingFeedsAsyncTask(context, remoteInstance, sinceId, type, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else if(type == RetrieveFeedsAsyncTask.Type.TAG)
            asyncTask = new RetrieveMissingFeedsAsyncTask(context, tag, sinceId, type, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            asyncTask = new RetrieveMissingFeedsAsyncTask(context, sinceId, type, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    /**
     * When tab comes visible, first displayed toot is defined as read
     * @param visible boolean
     */
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        if( context == null)
            return;
        boolean liveNotifications = sharedpreferences.getBoolean(Helper.SET_LIVE_NOTIFICATIONS, true);
        int batteryProfile = sharedpreferences.getInt(Helper.SET_BATTERY_PROFILE, Helper.BATTERY_PROFILE_NORMAL);
        //Store last toot id for home timeline to avoid to notify for those that have been already seen
        if (type == RetrieveFeedsAsyncTask.Type.HOME ) {

            if (visible) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + userId + instance, true);
                editor.apply();
                if(liveNotifications  && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingHomeIntent = new Intent(context, StreamingHomeTimelineService.class);
                    try {
                        context.startService(streamingHomeIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }else {
                if( streamingHomeIntent != null ){
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + userId + instance, false);
                    editor.apply();
                    context.stopService(streamingHomeIntent);
                }
            }
        } else if( type == RetrieveFeedsAsyncTask.Type.PUBLIC ){
            if (visible) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED + userId + instance, true);
                editor.apply();
                if(liveNotifications  && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingFederatedIntent = new Intent(context, StreamingFederatedTimelineService.class);
                    try {
                        context.startService(streamingFederatedIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }else {
                if( streamingFederatedIntent != null ){
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED + userId + instance, false);
                    editor.apply();
                    context.stopService(streamingFederatedIntent);
                }
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.LOCAL){
            if (visible) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL + userId + instance, true);
                editor.apply();
                if( liveNotifications  && batteryProfile == Helper.BATTERY_PROFILE_NORMAL) {
                    streamingLocalIntent = new Intent(context, StreamingLocalTimelineService.class);
                    try {
                        context.startService(streamingLocalIntent);
                    }catch (Exception ignored){}
                }
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }else {
                if( streamingLocalIntent != null ){
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL + userId + instance, false);
                    editor.apply();
                    context.stopService(streamingLocalIntent);
                }
            }
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        if( type == RetrieveFeedsAsyncTask.Type.HOME && streamingHomeIntent != null){
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_HOME + userId + instance, false);
            editor.apply();
            context.stopService(streamingHomeIntent);
        } else if( type == RetrieveFeedsAsyncTask.Type.PUBLIC && streamingFederatedIntent != null){
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED + userId + instance, false);
            editor.apply();
            context.stopService(streamingFederatedIntent);
        }else if(type == RetrieveFeedsAsyncTask.Type.LOCAL && streamingLocalIntent != null){
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL + userId + instance, false);
            editor.apply();
            context.stopService(streamingLocalIntent);
        }
    }

    public void scrollToTop(){
        if( lv_status != null) {
            lv_status.setAdapter(statusListAdapter);
        }
    }

    /**
     * Refresh status in list
     */
    public void refreshFilter(){
        statusListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRetrieveMissingFeeds(List<Status> statuses) {
        if(swipeRefreshLayout == null)
            return;
        swipeRefreshLayout.setRefreshing(false);

        if( isSwipped && this.statuses != null && this.statuses.size() > 0) {
            for (Status status : this.statuses) {
                status.setNew(false);
            }
            statusListAdapter.notifyItemRangeChanged(0, this.statuses.size());
        }
        isSwipped = false;
        if( statuses != null && statuses.size() > 0) {
            int inserted = 0;
            int insertedConversation = 0;
            if(type == RetrieveFeedsAsyncTask.Type.CONVERSATION){ //Remove conversation already displayed if new messages
                int position = 0;
                insertedConversation = statuses.size();
                if( this.statuses != null) {
                    for (Iterator<Status> it = this.statuses.iterator(); it.hasNext(); ) {
                        Status status = it.next();
                        for (Status status1 : statuses) {
                            if (status.getConversationId() != null && status.getConversationId().equals(status1.getConversationId())) {
                                statusListAdapter.notifyItemRemoved(position);
                                it.remove();
                            }
                        }
                        position++;
                    }
                }
            }
            for (int i = statuses.size() - 1; i >= 0; i--) {
                if( this.statuses != null) {
                    if (this.statuses.size() == 0){
                        if( type != RetrieveFeedsAsyncTask.Type.HOME){
                            if( statuses.get(i).getDate_id() > this.statuses.get(0).getDate_id()) {
                                inserted++;
                                this.statuses.add(0, statuses.get(i));
                            }
                        }else {
                            if( lastReadToot != null && statuses.get(i).getDate_id() > lastReadToot)  {
                                statuses.get(i).setNew(true);
                                MainActivity.countNewStatus++;
                                inserted++;
                                this.statuses.add(0, statuses.get(i));
                            }
                        }

                    }
                }
            }
            statusListAdapter.notifyItemRangeInserted(0, inserted);
            try {
                if( type == RetrieveFeedsAsyncTask.Type.HOME)
                    ((MainActivity) context).updateHomeCounter();
                else {
                    if( type != RetrieveFeedsAsyncTask.Type.CONVERSATION)
                        ((MainActivity) context).updateTimeLine(type, inserted);
                    else
                        ((MainActivity) context).updateTimeLine(type, insertedConversation);
                }
            }catch (Exception ignored){}
        }
    }

    public void fetchMore(String max_id){
        fetchMoreButtonDisplayed = false;
        asyncTask = new RetrieveFeedsAfterBookmarkAsyncTask(context, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onRetrieveFeedsAfterBookmark(APIResponse apiResponse) {
        if( apiResponse == null || (apiResponse.getError() != null && apiResponse.getError().getStatusCode() != 404) ){
            if( apiResponse == null)
                Toasty.error(context, context.getString(R.string.toast_error),Toast.LENGTH_LONG).show();
            else
                Toasty.error(context, apiResponse.getError().getError(),Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            flag_loading = false;
            return;
        }
        List<Status> statuses = apiResponse.getStatuses();
        if( statuses == null || statuses.size() == 0 )
            return;
        //Find the position of toots between those already present
        int position = 0;
        while (position < this.statuses.size() && statuses.get(0).getDate_id() < this.statuses.get(position).getDate_id()) {
            position++;
        }
        ArrayList<Status> tmpStatuses = new ArrayList<>();
        for (Status tmpStatus : statuses) {
            //Put the toot at its place in the list (id desc)
            if( !this.statuses.contains(tmpStatus) ) { //Element not already added
                //Mark status at new ones when their id is greater than the last read toot id
                if (type == RetrieveFeedsAsyncTask.Type.HOME && lastReadToot != null && tmpStatus.getDate_id() > lastReadToot) {
                    tmpStatus.setNew(true);
                    MainActivity.countNewStatus++;
                }
                tmpStatuses.add(tmpStatus);
            }
        }
        try {
            ((MainActivity) context).updateHomeCounter();
        }catch (Exception ignored){}
        int tootPerPage = sharedpreferences.getInt(Helper.SET_TOOTS_PER_PAGE, 40);
        //Display the fetch more toot button
        if( tmpStatuses.size()  >= tootPerPage) {
            if (initialBookMark != null && !fetchMoreButtonDisplayed && tmpStatuses.size() > 0 && tmpStatuses.get(tmpStatuses.size() - 1).getDate_id() > initialBookMarkDate) {
                tmpStatuses.get(tmpStatuses.size() - 1).setFetchMore(true);
                fetchMoreButtonDisplayed = true;
            }
        }
        this.statuses.addAll(position, tmpStatuses);
        statusListAdapter.notifyItemRangeInserted(position, tmpStatuses.size());
    }

    //Update last read toots value when pressing tab button
    public void updateLastReadToot(){
        if (type == RetrieveFeedsAsyncTask.Type.HOME && this.statuses != null && this.statuses.size() > 0) {
            lastReadToot = this.statuses.get(0).getDate_id();
        }
    }
}
