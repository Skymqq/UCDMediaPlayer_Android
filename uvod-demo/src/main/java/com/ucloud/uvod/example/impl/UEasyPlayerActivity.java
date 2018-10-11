package com.ucloud.uvod.example.impl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TableLayout;
import android.widget.Toast;

import com.ucloud.uvod.UMediaProfile;
import com.ucloud.uvod.UPlayerStateListener;
import com.ucloud.uvod.example.MainActivity;
import com.ucloud.uvod.example.R;
import com.ucloud.uvod.example.ui.UEasyPlayer;
import com.ucloud.uvod.example.ui.USettingMenuView;
import com.ucloud.uvod.example.ui.base.UMenuItem;
import com.ucloud.uvod.widget.UVideoView;
import com.umeng.analytics.MobclickAgent;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class UEasyPlayerActivity extends Activity implements USettingMenuView.Callback, UPlayerStateListener {

    public static final String TAG = "EasyPlayer";

    @BindView(R.id.video_main_view)
    UEasyPlayer easyPlayer;

    @BindView(R.id.hud_view)
    TableLayout debugInfoHudView;

    private String uri;

    @Override
    protected void onCreate(Bundle bundles) {
        super.onCreate(bundles);
        setContentView(R.layout.activity_video_demo1);
        ButterKnife.bind(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        uri = getIntent().getStringExtra(MainActivity.KEY_URI);
        String intentAction = getIntent().getAction();
        if (!TextUtils.isEmpty(intentAction) && intentAction.equals(Intent.ACTION_VIEW)) {
            Uri data = getIntent().getData();
            if (data != null && "content".equals(getIntent().getScheme())) {
                Cursor cursor = this.getContentResolver().query(data, new String[] { android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    uri = cursor.getString(0);
                    cursor.close();
                }
            }
            else {
                uri = Uri.decode(uri);
            }
        }
        easyPlayer.init(this);

        UMediaProfile profile = new UMediaProfile();
        profile.setInteger(UMediaProfile.KEY_LIVE_STREAMING, getIntent().getIntExtra(MainActivity.KEY_LIVE_STREMAING, 0)); //标识播放的流为直播源，还是点播源(0点播，1直播)
        profile.setInteger(UMediaProfile.KEY_START_ON_PREPARED, getIntent().getIntExtra(MainActivity.KEY_START_ON_PREPARED, 1)); //当prepread成功后自动开始播放，(无须自己监听prepared消息调用start方法) 直播推荐开启(1开启，0不开启)
        profile.setInteger(UMediaProfile.KEY_MEDIACODEC, getIntent().getIntExtra(MainActivity.KEY_MEDIACODEC, 0)); //视频解码方式，推荐软解
        profile.setInteger(UMediaProfile.KEY_RENDER_TEXTURE, 1); //视频渲染方式，推荐
        profile.setInteger(UMediaProfile.KEY_PREPARE_TIMEOUT, 1000 * 15); //设置第一次播放流地址时，prepared超时时间(超过设置的值，sdk内部会做重连动作，单位ms)
        profile.setInteger(UMediaProfile.KEY_READ_FRAME_TIMEOUT, 1000 * 15); //设置播放过程中，网络卡顿出现读取数据超时(超过设置的值，sdk内部会做重连动作，单位ms)
        profile.setInteger(UMediaProfile.KEY_ENABLE_BACKGROUND_PLAY, getIntent().getIntExtra(MainActivity.KEY_ENABLE_BACKGROUND_PLAY, 0)); //设置切换到后台是否继续播放，直播推荐开启，(默认为0不开启)
        profile.setInteger(UMediaProfile.KEY_MAX_RECONNECT_COUNT, 5); //当发生IOERROR PREPARE_TIMEOUT READ_FRAME_TIMEOUT 最大重连次数，默认5次

        //若需要区分4G是否继续播放等与用户确认相关的操作，设置为0，自行根据Android API监听网络状态调用setVideoPath做重连控制操作。
        profile.setInteger(UMediaProfile.KEY_ENABLE_NETWORK_RECOVERY_RECONNECT, 1); //当发生网络切换恢复时SDK内部会做重连（默认为0 不开启 1不开启)
        profile.setInteger(UMediaProfile.KEY_IS_MUSIC_PLAYER, 0); //如果播放的是纯音频流，设置为1，默认为0

        //示例 1：若设置不止一个http请求头字段 （Cookie， Cache-Control等）
        //采用如下外部构造可以满足所有HTTP标准头的设置 （value内容自行拼接处理）value格式不清楚的可以参考http标准的写法，以下尽给出了Cookie & Cache-Control字段的写法

        Map<String, String> headersMap = new HashMap<>();

//        headersMap.put("Cookie", "username=www.ucloud; password=cn;");
//        headersMap.put("Cache-Control", "no-cache;");
//        profile.setExtendMap(UMediaProfile.KEY_HEADERS, headersMap);


        //示例 2： 对Cookie value内容进行了拼接优化处理，不需要自己拼接字段，key,value 以map形式传递 （key,value仅限string内容，若不是先自行转换成string）

        Map<String, String> headCookieMap = new HashMap<>();

        headCookieMap.put("username", "www.ucloud");
        headCookieMap.put("password", "cn");
        headCookieMap.put("test", "test");

        //若示例 1 先设置了Cookie 示例2 同样设置了，无论示例2，在示例1前设置还是后设置 Cookie的值以第二个接口为准，顺序无关。 示例1 设置的其它值同样有效

        profile.setExtendMap(UMediaProfile.KEY_HEADER_COOKIE, headCookieMap); // 针对 cookie设置的接口

//        profile.setExtendMap(UMediaProfile.KEY_HEADERS, null);       //清除所有自定义设置的头字段，清除后http不会携带所有自定义头字段 (包括示例2接口方式设置的cookie)
//        profile.setExtendMap(UMediaProfile.KEY_HEADER_COOKIE, null); //清除cookie字段，清除后http不会携带cookie字段，其它字段没有影响 （示例1方式设置的Cookie，也会移除）


        if (uri != null && uri.endsWith("m3u8")) {
            profile.setInteger(UMediaProfile.KEY_MAX_CACHED_DURATION, 0); // m3u8 默认不开启延时丢帧策略
        }

        easyPlayer.setMediaProfile(profile);
        easyPlayer.setScreenOriention(UEasyPlayer.SCREEN_ORIENTATION_SENSOR);
        easyPlayer.setPlayerStateLisnter(this);
        easyPlayer.setMenuItemSelectedListener(this);

        if (getIntent().getIntExtra(MainActivity.KEY_SHOW_DEBUG_INFO, 1) == 1) {
            easyPlayer.setHudView(debugInfoHudView);
        }
        easyPlayer.initAspectRatio(UVideoView.VIDEO_RATIO_FIT_PARENT);
        easyPlayer.setVideoPath(uri);

        IntentFilter filter = new IntentFilter();
        filter.setPriority(1000);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateListener, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        easyPlayer.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        easyPlayer.onResume();
        MobclickAgent.onResume(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        easyPlayer.onDestroy();
        unregisterReceiver(networkStateListener);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (easyPlayer.isFullscreen()) {
                easyPlayer.toggleScreenOrientation();
                return true;
            }
            else {
                finish();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onSettingMenuSelected(UMenuItem item) {
        return false;
    }

    private BroadcastReceiver networkStateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeInfo = manager.getActiveNetworkInfo();
                if (activeInfo == null) {
                    Toast.makeText(context, R.string.info1, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    public void onPlayerStateChanged(State state, int extra1, Object extra2) {
        Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerStateChanged " + state.name());
        switch (state) {
            case PREPARING:
                break;
            case PREPARED:
                break;
            case START:
                break;
            case PAUSE:
                break;
            case STOP:
                break;
            case VIDEO_SIZE_CHANGED:
                break;
            case COMPLETED:
                break;
            case RECONNECT:
                if (extra1 < 0) {
                    Log.e(TAG, "lifecycle->EasyPlayer->demo->RECONNECT reconnect failed.");
                }
                else if (extra1 == 0) {
                    Log.e(TAG, "lifecycle->EasyPlayer->demo->RECONNECT reconnect failed & info = " + extra2);
                }
                else {
                    Log.e(TAG, "lifecycle->EasyPlayer->demo->RECONNECT reconnect count = " + extra1 + ", info = " + extra2);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlayerInfo(Info info, int extra1, Object extra2) {
        switch (info) {
            case BUFFERING_START:
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerInfo BUFFERING_START.");
                break;
            case BUFFERING_END:
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerInfo BUFFERING_END.");
                break;
            case BUFFERING_UPDATE:
                break;
            case BUFFERING_PERCENT:
                //extra2
                break;
            case VIDEO_RENDERING_START:
                break;
            case AUDIO_RENDERING_START:
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlayerError(Error error, int extra1, Object extra2) {
        switch (error) {
            case IOERROR:
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerError IOERROR.");
                break;
            case PREPARE_TIMEOUT://just a warn
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerError PREPARE_TIMEOUT.");
                break;
            case READ_FRAME_TIMEOUT://just a warn
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerError READ_FRAME_TIMEOUT.");
                break;
            case UNKNOWN:
                Log.i(TAG, "lifecycle->EasyPlayer->demo-> onPlayerError UNKNOWN.");
                break;
            default:
                break;
        }
    }
}
