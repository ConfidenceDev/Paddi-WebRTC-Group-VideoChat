package tv.paddi.android.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;
import io.skyway.Peer.Room;
import io.skyway.Peer.RoomOption;
import pl.bclogic.pulsator4droid.library.PulsatorLayout;
import tv.paddi.android.R;
import tv.paddi.android.adapters.RemoteViewAdapter;
import tv.paddi.android.dialogs.PayDialog;
import tv.paddi.android.utils.CommaCounter;
import tv.paddi.android.utils.InputKeyboardMethod;
import tv.paddi.android.utils.NetworkConnection;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

import static tv.paddi.android.utils.GetTimeAgo.getTimeAgo;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private FirebaseFirestore firebaseFirestore;
    private CollectionReference mainRef, mPublicCol, mPrivateCol;
    private FirebaseFirestoreSettings firestoreSettings;
    private NetworkConnection networkConnection;
    private InputKeyboardMethod inputKeyboardMethod;
    private CommaCounter commaCounter;
    private String currentUserId, myId;

    private ImageView mSettingsBtn, mLeaveBtn, mSwapBtn;
    private TextView mOnlineCount;
    private String peerId = null, suspendUtc;
    private LinearLayout mOnlineLin;
    private PulsatorLayout mPulse;

    //=========================== Set your APIkey and Domain ==================================
    private static final String API_KEY = "6f672800-6203-49e2-887e-fbeb55252c89";
    private static final String DOMAIN = "paddi.tv";

    private Peer peer;
    private int count = 0;

    private MediaStream localCam;
    private Room room;
    private RemoteViewAdapter adapter;
    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mEditor;
    private Button mJoinBtn, mStateBtn, mCreateBtn;
    private EditText mPrivateField;

    private GridView grdRemote;
    private Canvas myCam;
    private boolean isActive = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Window wnd = getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        firebaseFirestore = FirebaseFirestore.getInstance();
        firestoreSettings = new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build();
        firebaseFirestore.setFirestoreSettings(firestoreSettings);
        networkConnection = new NetworkConnection();
        inputKeyboardMethod = new InputKeyboardMethod();
        commaCounter = new CommaCounter();

        mainRef = firebaseFirestore.collection("Paddi").document("Files").collection("Users");
        mPrivateCol = firebaseFirestore.collection("Paddi").document("Files").collection("Private");
        mPublicCol = firebaseFirestore.collection("Paddi").document("Files").collection("Public");

        grdRemote = findViewById(R.id.grdRemote);
        mSettingsBtn = findViewById(R.id.settingsBtn);
        mOnlineLin = findViewById(R.id.onlineLin);
        mOnlineCount = findViewById(R.id.onlineCount);
        myCam = findViewById(R.id.svLocalView);
        mLeaveBtn = findViewById(R.id.leaveBtn);
        mSwapBtn = findViewById(R.id.swapBtn);
        mPulse = findViewById(R.id.peerPulse);
        mJoinBtn = findViewById(R.id.joinRoomBtn);
        mStateBtn = findViewById(R.id.state);
        mCreateBtn = findViewById(R.id.createRoomBtn);
        mPrivateField = findViewById(R.id.roomNameField);

        adapter = new RemoteViewAdapter(this);
        grdRemote.setAdapter(adapter);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mPreferences.edit();

        suspendUtc = mPreferences.getString("suspendUtc", "default");
        grdRemote.setScrollingCacheEnabled(false);
        grdRemote.setNestedScrollingEnabled(false);
        grdRemote.setEnabled(false);
        grdRemote.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return motionEvent.getAction() == MotionEvent.ACTION_MOVE;
            }
        });
        networkCheck();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            sendToSignIn();
        } else {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            try {
                mainRef.document(currentUserId)
                        .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                            @Override
                            public void onEvent(@Nullable DocumentSnapshot documentSnapshot,
                                                @Nullable FirebaseFirestoreException e) {
                                if (documentSnapshot != null) {
                                    if (!documentSnapshot.exists()) {
                                        Date utc = new Date(System.currentTimeMillis());
                                        Map<String, Object> setMap = new HashMap<>();
                                        setMap.put("utc", utc);
                                        setMap.put("active", utc);

                                        mainRef.document(currentUserId).set(setMap);
                                        startIt();
                                    } else {
                                        startIt();
                                    }
                                }
                            }
                        });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void startIt() {
        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mainRef.document(currentUserId).addSnapshotListener(MainActivity.this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (documentSnapshot != null) {
                    if (documentSnapshot.exists()) {
                        String k = "active";
                        Object timeStamp = documentSnapshot.get(k);

                        if (!TextUtils.isEmpty(String.valueOf(timeStamp)) && timeStamp != null) {
                            Date utc = new Date(String.valueOf(timeStamp));
                            delete(k, utc.getTime());

                        } else {
                            isActive = false;
                            checkIfActive();
                        }

                    } else {
                        isActive = false;
                        checkIfActive();
                    }
                }
            }
        });
    }

    private void sendToSignIn() {
        startActivity(new Intent(MainActivity.this, SignInActivity.class));
    }

    void networkCheck() {
        if (!networkConnection.isConnected(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.no_internet));
            builder.setMessage(getResources().getString(R.string.internet_continue));
            builder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            });

            AlertDialog createDialog = builder.create();
            createDialog.setCancelable(false);
            createDialog.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        findViewById(R.id.settingsBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.no_anim);
            }
        });


        //========================== App Update ======================================
        firebaseFirestore.collection("Media").document("Information")
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot documentSnapshot,
                                        @Nullable FirebaseFirestoreException e) {
                        if (documentSnapshot != null) {
                            if (documentSnapshot.exists()) {
                                String ver = documentSnapshot.getString("paddiUpdate");
                                if (ver != null && Integer.parseInt(ver) == 1) {
                                    appUpdate();
                                }
                            }
                        }
                    }
                });

        //============================= Check Suspension ===========================
        if (!suspendUtc.equals("default")) {
            Date utc = new Date(suspendUtc);
            checkSuspension(utc.getTime());
        }

        mainFunction();
        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
    }

    //=========================================== Main =====================================

    void mainFunction() {

        //=================================== Initialize Peer ====================================

        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        peer = new Peer(this, currentUserId, option);

        //
        // Set Peer event callbacks
        //
        //============================ Request permissions ===================================
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        } else {
            // Get a local MediaStream & show it
            startLocalStream();
        }

        //=============================== Swap Cam ===================================
        mSwapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (localCam != null) {
                    boolean result = localCam.switchCamera();
                    if (result) {
                        //Success
                        Log.d(TAG, "Camera Swapped");
                    } else {
                        //Failed
                        Log.d(TAG, "Camera Swap Error");
                        //terminateCam();
                        //startLocalStream();
                    }
                }

            }
        });

        // OPEN
        peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                // Show my ID
                Log.d(TAG, "[On/Open]");
                myId = (String) object;
                onlineCount();
            }
        });

        peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Close]");
            }
        });
        peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });
        peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/Error]" + error.message);
            }
        });

        //======================== Other Functions ========================================

        mJoinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (networkConnection.isConnected(MainActivity.this)) {
                    if (!isActive) {
                        showDialog();
                    } else {
                        if ((null == peer) || (null == myId) || (0 == myId.length())) {
                            Toast.makeText(MainActivity.this,
                                    getResources().getString(R.string.connecting_device),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mSettingsBtn.setVisibility(View.GONE);
                        mOnlineLin.setVisibility(View.GONE);

                        mPulse.start();
                        mPublicCol.whereLessThan("size", 3).get().addOnCompleteListener(MainActivity.this,
                                new OnCompleteListener<QuerySnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                        if (task.isSuccessful()) {
                                            if (!task.getResult().isEmpty()) {
                                                List<String> list = new ArrayList<>();
                                                for (QueryDocumentSnapshot document : task.getResult()) {
                                                    list.add(document.getId());
                                                }

                                                if (list.size() > 0) {
                                                    Random rand = new Random();
                                                    String roomName = list.get(rand.nextInt(list.size()));
                                                    joinRoom(roomName, mPublicCol);

                                                } else {
                                                    mPulse.stop();
                                                    mSettingsBtn.setVisibility(View.VISIBLE);
                                                    mOnlineLin.setVisibility(View.VISIBLE);
                                                }
                                            } else {
                                                String roomName = UUID.randomUUID().toString();
                                                joinRoom(roomName, mPublicCol);
                                            }
                                        }
                                    }
                                });
                    }
                } else {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
                }
            }
        });

        //========================== Create Room ========================

        mCreateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = mPrivateField.getText().toString().trim();
                if (!TextUtils.isEmpty(name)) {
                    if (networkConnection.isConnected(MainActivity.this)) {
                        if ((null == peer) || (null == myId) || (0 == myId.length())) {
                            Toast.makeText(MainActivity.this,
                                    getResources().getString(R.string.connecting_device),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        inputKeyboardMethod.hideKeyboard(MainActivity.this);
                        mSettingsBtn.setVisibility(View.GONE);
                        mOnlineLin.setVisibility(View.GONE);

                        mPulse.start();
                        mPrivateCol.document(name)
                                .get().addOnCompleteListener(MainActivity.this,
                                new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                        if (task.isSuccessful()) {
                                            if (task.getResult().exists()) {
                                                String val = String.valueOf(task.getResult().get("size"));
                                                if (Integer.parseInt(val) > -1 && Integer.parseInt(val) < 3) {

                                                    joinRoom(name, mPrivateCol);

                                                } else {
                                                    mPulse.stop();
                                                    mSettingsBtn.setVisibility(View.VISIBLE);
                                                    mOnlineLin.setVisibility(View.VISIBLE);
                                                    Toast.makeText(MainActivity.this,
                                                            getResources().getString(R.string.room_full), Toast.LENGTH_LONG).show();
                                                }
                                            } else {
                                                joinRoom(name, mPrivateCol);

                                            }
                                        } else {
                                            Toast.makeText(MainActivity.this,
                                                    getResources().getString(R.string.error), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                    } else {
                        Toast.makeText(MainActivity.this,
                                getResources().getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.enter_room_name), Toast.LENGTH_SHORT).show();
                }
            }
        });

        //========================== Leave Room ========================

        mLeaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                leaveRoom();
                mPulse.stop();
                mSettingsBtn.setVisibility(View.VISIBLE);
                mOnlineLin.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocalStream();
            } else {
                Toast.makeText(this, getResources().getString(R.string.permission_return), Toast.LENGTH_LONG).show();
            }
        }
    }

    void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.pay_msg));
        builder.setPositiveButton(getResources().getString(R.string.activate),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        onStop();
                        terminateCam();

                        PayDialog payDialog = new PayDialog(MainActivity.this, currentUserId, "active");
                        boolean isComplete = payDialog.payAdd();
                        if (isComplete) {
                            startLocalStream();
                            onResume();

                        } else {
                            startLocalStream();
                            onResume();
                        }
                    }
                });
        builder.setNegativeButton(getResources().getString(R.string.cancel), null);
        AlertDialog createDialog = builder.create();
        createDialog.setCancelable(false);
        createDialog.show();
    }

    void appUpdate() {
        AlertDialog.Builder tipsAlert = new AlertDialog.Builder(MainActivity.this);
        tipsAlert.setTitle(getResources().getString(R.string.app_update))
                .setMessage(getResources().getString(R.string.app_update_msg))
                .setPositiveButton(getResources().getString(R.string.update), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        openStore(getResources().getString(R.string.store_url));
                    }
                })
                .setNegativeButton(getResources().getString(R.string.website), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.web_add))));
                    }
                })
                .setNeutralButton(getResources().getString(R.string.finish), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
        AlertDialog start_builder = tipsAlert.create();
        start_builder.setCancelable(false);
        start_builder.show();
    }

    void openStore(String updateUrl) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    void checkIfActive() {
        if (isActive) {
            mStateBtn.setBackground(getResources().getDrawable(R.drawable.bg_active));
            mStateBtn.setText(getResources().getString(R.string.active));
            mStateBtn.setTextColor(getResources().getColor(R.color.greenish_white));
        } else {
            mStateBtn.setBackground(getResources().getDrawable(R.drawable.bg_passive));
            mStateBtn.setText(getResources().getString(R.string.passive));
            mStateBtn.setTextColor(getResources().getColor(R.color.pink));
        }
    }

    void delete(String key, long utc) {
        try {
            String date = getTimeAgo(utc, MainActivity.this);
            if (date.contains(getResources().getString(R.string.days_ago))) {
                String raw = date.replace(getResources().getString(R.string.days_ago), "").trim();
                int val = Integer.parseInt(raw);

                if (val > 7) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(key, FieldValue.delete());
                    mainRef.document(currentUserId).update(updates);
                    isActive = false;
                    checkIfActive();
                } else {
                    isActive = true;
                    checkIfActive();
                }
            } else {
                isActive = true;
                checkIfActive();
            }
        } catch (Exception p) {
            p.printStackTrace();
        }
    }

    void checkSuspension(long utc) {
        String date = getTimeAgo(utc, MainActivity.this);
        if (date.contains(getResources().getString(R.string.minutes))) {
            String raw = date.replace(getResources().getString(R.string.minutes), "").trim();
            int val = Integer.parseInt(raw);
            if (val > 2) {
                mEditor.remove("suspendUtc");
                mEditor.commit();
            } else {
                showSuspensionDialog();
            }
        } else if (date.contains(getResources().getString(R.string.days_ago)) ||
                date.contains(getResources().getString(R.string.yesterday))) {
            mEditor.remove("suspendUtc");
            mEditor.commit();
        } else {
            showSuspensionDialog();
        }
    }

    void showSuspensionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.acc_suspended));
        builder.setMessage(getResources().getString(R.string.acc_suspended_msg));
        builder.setPositiveButton(getResources().getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });

        AlertDialog createDialog = builder.create();
        createDialog.setCancelable(false);
        createDialog.show();
    }

    void onlineCount() {
        if (peer != null) {
            final int TIMER = 1000;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    try {
                        peer.listAllPeers(new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                if (!(object instanceof JSONArray)) {
                                    return;
                                }

                                JSONArray peers = (JSONArray) object;
                                ArrayList<String> listPeerIds = new ArrayList<>();
                                String peerId;

                                // Exclude my own ID
                                for (int i = 0; peers.length() > i; i++) {
                                    try {
                                        peerId = peers.getString(i);
                                        listPeerIds.add(peerId);
                                        count = listPeerIds.size();
                                        mOnlineCount.setText(commaCounter.getFormattedNumber(String.valueOf(count)));

                                        onlineCount();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                    } catch (Exception p) {
                        p.printStackTrace();
                    }
                }
            }, TIMER);
        }
    }

    @Override
    protected void onStop() {
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        destroyPeer();
        /*final int TIMER = 5000;

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAppInBackground(MainActivity.this)) {
                    destroyPeer();
                }
            }
        }, TIMER); */

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyPeer();
        super.onDestroy();
    }

    //
    //==================== Get a local MediaStream & show it =================================
    //
    void startLocalStream() {
        try {
            Navigator.initialize(peer);
            MediaConstraints constraints = new MediaConstraints();
            localCam = Navigator.getUserMedia(constraints);

            myCam.mirror = false;
            localCam.addVideoRenderer(myCam, 0);
        } catch (Exception c) {
            c.printStackTrace();
        }
    }

    //
    // ====================== Clean up objects ======================
    //
    void destroyPeer() {
        try {
            leaveRoom();

            terminateCam();

            if (null != peer) {
                unsetPeerCallback(peer);
                if (!peer.isDisconnected()) {
                    peer.disconnect();
                }

                if (!peer.isDestroyed()) {
                    peer.destroy();
                }

                peer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    void terminateCam() {
        if (null != localCam) {
            localCam.removeVideoRenderer(myCam, 0);
            localCam.close();
        }

        Navigator.terminate();
    }

    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if (null == peer) {
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }

    // =============================== Join the room ===========================================

    void joinRoom(String roomName, CollectionReference mCol) {
        if ((peer == null) || (myId == null) || (myId.length() == 0)) {
            Toast.makeText(this, getResources().getString(R.string.connecting_device), Toast.LENGTH_SHORT).show();

        } else {
            if (TextUtils.isEmpty(roomName)) {
                Toast.makeText(this, getResources().getString(R.string.no_room), Toast.LENGTH_SHORT).show();
            } else {

                RoomOption option = new RoomOption();
                option.mode = RoomOption.RoomModeEnum.MESH;
                option.stream = localCam;

                // Join Room
                room = peer.joinRoom(roomName, option);
                //
                // Set Callbacks
                //
                mPulse.stop();

                try {
                    if (room != null) {
                        room.on(Room.RoomEventEnum.OPEN, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                if (!(object instanceof String)) return;

                                mCol.document(roomName)
                                        .get().addOnCompleteListener(MainActivity.this,
                                        new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    if (task.getResult().exists()) {
                                                        String val = String.valueOf(task.getResult().get("size"));
                                                        int finalSize;
                                                        if (!TextUtils.isEmpty(val)) {
                                                            finalSize = (Integer.parseInt(val) + 1);
                                                        } else {
                                                            finalSize = 1;
                                                        }

                                                        if (finalSize == 1) {
                                                            waitForOthers();
                                                        }

                                                        Map<String, Object> setMap = new HashMap<>();
                                                        setMap.put("size", finalSize);

                                                        mCol.document(roomName).set(setMap);
                                                    } else {
                                                        waitForOthers();

                                                        Map<String, Object> setMap = new HashMap<>();
                                                        setMap.put("size", 1);

                                                        mCol.document(roomName).set(setMap);
                                                    }
                                                }
                                            }
                                        });

                                String roomName = (String) object;
                                Log.i(TAG, "Enter Room: " + roomName);
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.joined_room), Toast.LENGTH_LONG).show();
                            }
                        });

                        room.on(Room.RoomEventEnum.CLOSE, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                mCol.document(roomName)
                                        .get().addOnCompleteListener(MainActivity.this,
                                        new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    if (task.getResult().exists()) {
                                                        String val = String.valueOf(task.getResult().get("size"));
                                                        if (!TextUtils.isEmpty(val)) {
                                                            if (Integer.parseInt(val) > 0) {
                                                                int finalSize = (Integer.parseInt(val) - 1);

                                                                Map<String, Object> setMap = new HashMap<>();
                                                                setMap.put("size", finalSize);

                                                                mCol.document(roomName).update(setMap);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        });

                                String roomName = (String) object;
                                Log.i(TAG, "Leave Room: " + roomName);
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.left_room), Toast.LENGTH_LONG).show();

                                // Remove all streams
                                adapter.removeAllRenderers();

                                // Unset callbacks
                                room.on(Room.RoomEventEnum.OPEN, null);
                                room.on(Room.RoomEventEnum.CLOSE, null);
                                room.on(Room.RoomEventEnum.ERROR, null);
                                room.on(Room.RoomEventEnum.PEER_JOIN, null);
                                room.on(Room.RoomEventEnum.PEER_LEAVE, null);
                                room.on(Room.RoomEventEnum.STREAM, null);
                                room.on(Room.RoomEventEnum.REMOVE_STREAM, null);

                                room = null;
                            }
                        });

                        room.on(Room.RoomEventEnum.ERROR, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                PeerError error = (PeerError) object;
                                Log.d(TAG, "RoomEventEnum.ERROR:" + error);
                            }
                        });

                        room.on(Room.RoomEventEnum.PEER_JOIN, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                Log.d(TAG, "RoomEventEnum.PEER_JOIN:");

                                if (!(object instanceof String)) return;
                                String peerId = (String) object;
                                Log.i(TAG, "Join Room: " + peerId);
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.user_joined), Toast.LENGTH_LONG).show();
                            }
                        });
                        room.on(Room.RoomEventEnum.PEER_LEAVE, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                Log.d(TAG, "RoomEventEnum.PEER_LEAVE:");

                                if (!(object instanceof String)) return;

                                String peerId = (String) object;
                                Log.i(TAG, "Leave Room: " + peerId);
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.user_left), Toast.LENGTH_LONG).show();

                                adapter.remove(peerId);
                            }
                        });

                        room.on(Room.RoomEventEnum.STREAM, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                Log.d(TAG, "RoomEventEnum.STREAM: + " + object);

                                if (!(object instanceof MediaStream)) return;

                                final MediaStream stream = (MediaStream) object;
                                Log.d(TAG, "peer = " + stream.getPeerId() + ", label = " + stream.getLabel());

                                adapter.add(stream);
                            }
                        });

                        room.on(Room.RoomEventEnum.REMOVE_STREAM, new OnCallback() {
                            @Override
                            public void onCallback(Object object) {
                                Log.d(TAG, "RoomEventEnum.REMOVE_STREAM: " + object);

                                if (!(object instanceof MediaStream)) return;

                                final MediaStream stream = (MediaStream) object;
                                Log.d(TAG, "peer = " + stream.getPeerId() + ", label = " + stream.getLabel());

                                adapter.remove(stream);
                            }
                        });
                    } else {
                        leaveRoom();
                        mSettingsBtn.setVisibility(View.VISIBLE);
                        mOnlineLin.setVisibility(View.VISIBLE);
                        Toast.makeText(this,
                                getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception r) {
                    r.printStackTrace();
                }
            }
        }
    }

    void waitForOthers() {
        final int TIMER = 4000;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.one_present), Toast.LENGTH_LONG).show();
            }
        }, TIMER);
    }

    //
    //=============== Leave the room ==================
    //
    void leaveRoom() {
        if (peer != null && room != null) {
            room.close();
        }
    }

    //================================= Check background ==================================
    static boolean isAppInBackground(Context context) {
        boolean isInBackground = false;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = null;
        if (am != null) {
            runningProcesses = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if (processInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String activeProcess : processInfo.pkgList) {
                        if (activeProcess.equals(context.getPackageName())) {
                            isInBackground = true;
                        }
                    }
                }
            }
        }
        return isInBackground;
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
