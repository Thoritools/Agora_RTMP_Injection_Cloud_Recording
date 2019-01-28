package agora.io.injectstream;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.textclassifier.TextLinks;
import android.widget.FrameLayout;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;


import io.agora.rtc.live.LiveInjectStreamConfig;
import io.agora.rtc.Constants;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static agora.io.injectstream.MainActivity.etName;

public class ShowActivity extends AppCompatActivity implements IMediaEngineHandler {
    private WorkThread mWorkThread;
    private SurfaceView localView;
    private SurfaceView injectView;

    private FrameLayout mLocal;
    private FrameLayout mInject;
    private String url;

    private RecyclerView msgView;
    private List<String> mMessageDataSet = new ArrayList<>();
    private CRMRecycleAdapter mCrmAdapter;

    private boolean hasInjectedJoined = false;


    private void postStartJson(String url) {

        //申明给服务端传递一个json串
        //创建一个OkHttpClient对象

        Log.i("Record", "url = " + url);

        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\n\"appid\":\"c9c0aab5db2a401299a352c71d969967\",\n\"channel\":\"agora-test\",\n\"uid\":8888\n}");
        Log.i("Record", "RequestBody = " + body);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Postman-Token", "efb12d11-5e51-f42b-e160-c57ab8bc7de7")
                .build();

        Log.i("Record", "request = " + request);

        try {
            Response response = client.newCall(request).execute();
            Log.i("Record", "response = " + response);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show);

        ((MApplication)getApplication()).initWorkThread();


        msgView = findViewById(R.id.rv_chat_room_main_message);
        msgView.setHasFixedSize(true);
        mCrmAdapter = new CRMRecycleAdapter(new WeakReference<Context>(this), mMessageDataSet);
        msgView.setAdapter(mCrmAdapter);
        msgView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        msgView.addItemDecoration(new CRMItemDecor());

        mWorkThread = ((MApplication)getApplication()).getWorkThread();
        mWorkThread.handler().addEventHandler(this);
        mWorkThread.configEngine(Constants.CLIENT_ROLE_BROADCASTER, Constants.VIDEO_PROFILE_480P);
        mWorkThread.joinChannel(getIntent().getStringExtra("CHANNEL_NAME"), 0);

        localView = RtcEngine.CreateRendererView(this);
        injectView = RtcEngine.CreateRendererView(this);

        mLocal = findViewById(R.id.fl_local_view);
        mInject = findViewById(R.id.fl_inject_view);

        mWorkThread.preview(true, localView, 0);

        url = getIntent().getStringExtra("INJECT_URL");
    }


    public void onStartClicked(View v) {
        new Thread() {
            @Override
            public void run() {
                postStartJson("http://123.155.153.85:3233/v1/recording/start");
            }
        }.start();
    }

    public void onStopClicked(View v) {
        //stop();
        new Thread() {
            @Override
            public void run() {
                postStartJson("http://123.155.153.85:3233/v1/recording/stop");
            }
        }.start();
    }


    public void onExitClicked(View v) {
        stop();
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stop();
    }

    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
        sendChatMessage("joinSuccess:" + channel);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                localView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                localView.setZOrderOnTop(true);

                if (mLocal.getChildCount() > 0)
                    mLocal.removeAllViews();

                if (localView.getParent() != null)
                    ((ViewGroup)(localView.getParent())).removeAllViews();

                mLocal.addView(localView);

                mWorkThread.rtcEngine().addInjectStreamUrl(url, getConfig());
            }
        });
    }

    @Override
    public void onUserJoined(final int uid, int elapsed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (uid == 666 && !hasInjectedJoined) {
                    sendChatMessage("inject user joined: " + uid);

                    if (injectView.getParent() != null) {
                        ((ViewGroup)(injectView.getParent())).removeAllViews();
                    }

                    if (mInject.getChildCount() > 0)
                        mInject.removeAllViews();

                    injectView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    injectView.setZOrderOnTop(false);

                    mWorkThread.setmRemoteView(injectView, uid);
                    mInject.addView(injectView);
                    hasInjectedJoined = true;
                }
            }
        });

    }

    @Override
    public void onStreamPublished(String url, int error) {

    }

    @Override
    public void onStreamUnpublished(String url) {

    }

    @Override
    public void onError(int err) {

    }

    @Override
    public void onUserOffline(int uid, int reason) {

    }

    @Override
    public void onLeaveChannel(IRtcEngineEventHandler.RtcStats stats) {

    }

    @Override
    public void onStreamInjectedStatus(String url, int uid, int status) {
        sendChatMessage("injected status:" + status);
    }

    public LiveInjectStreamConfig getConfig() {
        LiveInjectStreamConfig config = new LiveInjectStreamConfig();
        config.width = 0;
        config.height = 0;
        config.videoGop = 30;
        config.videoFramerate = 15;
        config.videoBitrate = 400;
        config.audioSampleRate = LiveInjectStreamConfig.AudioSampleRateType.TYPE_44100;
        config.audioBitrate = 48;
        config.audioChannels = 1;

        return config;
    }

    public void stop(){
        mWorkThread.leaveChannel();
        mWorkThread.rtcEngine().removeInjectStreamUrl(url);
        finish();
    }

    public void sendChatMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMessageDataSet.add(message);

                if (mMessageDataSet.size() > 14) {// max value is 15
                    int len = mMessageDataSet.size() - 15;
                    for (int i = 0; i < len; i++) {
                        mMessageDataSet.remove(i);
                    }
                }

                mCrmAdapter.upDateDataSet(mMessageDataSet);
                msgView.smoothScrollToPosition(mCrmAdapter.getItemCount() - 1);
            }
        });
    }
}
