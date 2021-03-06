// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.killerwhale.memary.Activity;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.location.Location;
import android.net.Uri;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;


import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.killerwhale.memary.ARComponent.Model.Stroke;
import com.killerwhale.memary.ARComponent.Renderer.BackgroundRenderer;
import com.killerwhale.memary.ARComponent.Renderer.LineShaderRenderer;
import com.killerwhale.memary.ARComponent.Renderer.LineShaderRendererGroup;
import com.killerwhale.memary.ARComponent.Renderer.LineUtils;
import com.killerwhale.memary.ARComponent.Renderer.PointCloudRenderer;
import com.killerwhale.memary.ARComponent.Utils.BrushSelector;
import com.killerwhale.memary.ARComponent.Utils.ClearDrawingDialog;
import com.killerwhale.memary.ARComponent.Utils.ErrorDialog;
import com.killerwhale.memary.ARComponent.Utils.TrackingIndicator;
import com.killerwhale.memary.ARComponent.Utils.StrokeStorageHelper;
import com.killerwhale.memary.ARComponent.Listener.OnArDownloadedListener;
import com.killerwhale.memary.ARComponent.Listener.OnStrokeUrlCompleteListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.killerwhale.memary.ARComponent.Utils.UploadDrawingDialog;
import com.killerwhale.memary.ARComponent.Utils.ARSettings;
import com.killerwhale.memary.DataModel.Post;
import com.killerwhale.memary.Preference;
import com.killerwhale.memary.R;
import com.killerwhale.memary.ARComponent.Utils.SessionHelper;
import com.uncorkedstudios.android.view.recordablesurfaceview.RecordableSurfaceView;

import org.imperiumlabs.geofirestore.GeoFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;


/**
 * Author: Qili Zeng (qzeng@bu.edu)
 * With reference to: Justaline [https://github.com/googlecreativelab/justaline-android]
 * Prominent modification to original codes done under Apache License 2.0
 * This is a comprehensive implementation of memARy AR services.
 */

public class ARActivity extends ARBaseActivity
        implements RecordableSurfaceView.RendererCallbacks, View.OnClickListener,
        ErrorDialog.Listener,ClearDrawingDialog.Listener, UploadDrawingDialog.Listener,
        OnStrokeUrlCompleteListener, OnArDownloadedListener{

    private static final String TAG = "ARActivity";
    private static final long INTERVAL_LOC_REQUEST = 5000;
    private static final int TOUCH_QUEUE_SIZE = 10;
    private boolean mUserRequestedARCoreInstall = true;

    enum Mode {
        DRAW, VIEW
    };

    private float[] projmtx = new float[16];
    private float[] viewmtx = new float[16];
    private float[] mZeroMatrix = new float[16];
    private float mScreenWidth = 0;
    private float mScreenHeight = 0;
    private Vector2f mLastTouch;
    private AtomicInteger touchQueueSize;
    private AtomicReferenceArray<Vector2f> touchQueue;
    private float mLineWidthMax = 0.33f;
    private float[] mLastFramePosition;
    private Boolean isDrawing = false;

    private Mode mMode;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private RecordableSurfaceView mSurfaceView;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private LineShaderRenderer mLineShaderRenderer = new LineShaderRenderer();
    private LineShaderRendererGroup mCloudShaderRenderer = new LineShaderRendererGroup(0.0f, mLineWidthMax);
    private final PointCloudRenderer pointCloud = new PointCloudRenderer();
    private TrackingIndicator mTrackingIndicator;
    private ImageButton btnReturn;
    private ImageButton btnUndo;
    private ImageButton btnSave;
    private ImageButton btnClear;
    private ImageButton btnRefresh;
    private View mDrawUiContainer;
    ObjectAnimator mAnimator;

    private Frame mFrame;
    private Session mSession;
    private Anchor mAnchor;
    private List<Stroke> mStrokes;
    private BrushSelector mBrushSelector;

    private AtomicBoolean bHasTracked = new AtomicBoolean(false);
    private AtomicBoolean bTouchDown = new AtomicBoolean(false);
    private AtomicBoolean bClearDrawing = new AtomicBoolean(false);
    private AtomicBoolean bUploadDrawing = new AtomicBoolean(false);
    private AtomicBoolean bUndo = new AtomicBoolean(false);
    private AtomicBoolean bModeChange = new AtomicBoolean(false);
    private AtomicBoolean bNewStroke = new AtomicBoolean(false);
    private AtomicBoolean bInitCloudRenderer = new AtomicBoolean(false);
    private AtomicBoolean bRefreshCloudRenderer = new AtomicBoolean(false);
    private static final int MAX_UNTRACKED_FRAMES = 5;
    private int mFramesNotTracked = 0;
    private Map<String, Stroke> mSharedStrokes = new HashMap<>();
    private long mRenderDuration;
    private GestureDetector gestureDetector;
    StrokeStorageHelper strokeHelper;

    // Location
    private FusedLocationProviderClient FLPC;
    private LocationRequest locationRequest;


    /**
     * Setup the app when main activity is created
     */
    @SuppressLint("ApplySharedPref")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);


        mTrackingIndicator = findViewById(R.id.finding_surfaces_view);

        mSurfaceView = findViewById(R.id.surfaceview);
        mSurfaceView.setRendererCallbacks(this);

        btnUndo = findViewById(R.id.btnUndo);
        btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(this);

        btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(this);
        btnReturn = findViewById(R.id.btnBack);
        btnReturn.setOnClickListener(this);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(this);

        mAnimator = ObjectAnimator.ofFloat(btnRefresh, "rotation", 360f, 0f);
        mAnimator.setDuration(1750);
        mAnimator.setRepeatCount(ObjectAnimator.INFINITE);

        strokeHelper = new StrokeStorageHelper(this);


        // set up brush selector
        mBrushSelector = findViewById(R.id.brush_selector);

        // Reset the zero matrix
        Matrix.setIdentityM(mZeroMatrix, 0);

        mStrokes = new ArrayList<>();
        touchQueueSize = new AtomicInteger(0);
        touchQueue = new AtomicReferenceArray<>(TOUCH_QUEUE_SIZE);

        mDrawUiContainer = findViewById(R.id.draw_container);
        mTrackingIndicator.addListener(new TrackingIndicator.ModeListener(){
            @Override
            public void onModeChange(){
                TrackingIndicator.Mode mode = TrackingIndicator.Mode.VIEW;
                if (mMode == Mode.DRAW){
                    mode = TrackingIndicator.Mode.DRAW;
                }
                mTrackingIndicator.setMode(mode);
            }
        });

        setMode(Mode.VIEW);

        gestureDetector = new GestureDetector(this, new GestureDetector.OnGestureListener(){
            @Override
            public boolean onDown(MotionEvent e) {
                Log.d(TAG, "onDown");
                closeViewsOutsideTapTarget(e);
                return false;
            }
            @Override
            public void onShowPress(MotionEvent e) {
                Log.d(TAG, "onShowPress");
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Log.d(TAG, "onLongPress");
                setMode(Mode.DRAW);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.d(TAG, "onSingleTapUp");
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                Log.d(TAG, "onScroll");
                Log.d(TAG, "onScroll: "+distanceX);
                Log.d(TAG, "onScroll: "+distanceX);
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Log.d(TAG, "onFling");
                return false;
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        FLPC = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(INTERVAL_LOC_REQUEST);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * onResume part of the Android Activity Lifecycle
     */
    @Override
    protected void onResume() {
        super.onResume();


        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.

        // Check if ARCore is installed/up-to-date
        int message = -1;

        Exception exception = null;
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance()
                        .requestInstall(this, mUserRequestedARCoreInstall)) {
                    case INSTALLED:
                        mSession = new Session(this);

                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        mUserRequestedARCoreInstall = false;
                        // at this point, the activity is paused and user will go through
                        // installation process
                        return;
                }
            }
        } catch (Exception e) {
            exception = e;
            message = getARCoreInstallErrorMessage(e);
        }

        // display possible ARCore error to user
        if (message >= 0) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Exception creating session", exception);
            finish();
            return;
        }

        // Create default config and check if supported.
        Config config = new Config(mSession);
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
        config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
        if (!mSession.isSupported(config)) {
            Toast.makeText(getApplicationContext(), R.string.ar_not_supported,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mSession.configure(config);

        // Note that order of session/surface resume matters - session must be resumed
        // before the surface view is resumed or the surface may call back on a session that is
        // not ready.
        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            ErrorDialog.newInstance(R.string.error_camera_not_available, true)
                    .show(this);
        } catch (Exception e) {
            ErrorDialog.newInstance(R.string.error_resuming_session, true).show(this);
        }

        mSurfaceView.resume();


        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        mScreenHeight = displayMetrics.heightPixels;
        mScreenWidth = displayMetrics.widthPixels;

        if (!SessionHelper.shouldContinueSession(this)) {
            // if user has left activity for too long, clear the strokes from the previous session
            bClearDrawing.set(true);
            showStrokeDependentUI();
        }

        findViewById(R.id.draw_container).setVisibility(View.VISIBLE);

        downloadStrokes();
        bInitCloudRenderer.set(true);
        setDownloadAnimation();

    }

    /**
     * onPause part of the Android Activity Lifecycle
     */
    @Override
    public void onPause() {

        mSurfaceView.pause();
        if (mSession != null) {
            mSession.pause();
        }

        mTrackingIndicator.resetTrackingTimeout();

        SessionHelper.setSessionEnd(this);

        super.onPause();
    }


    /**
     * update() is executed on the GL Thread.
     * The method handles all operations that need to take place before drawing to the screen.
     * The method :
     * extracts the current projection matrix and view matrix from the AR Pose
     * handles adding stroke and points to the data collections
     * updates the ZeroMatrix and performs the matrix multiplication needed to re-center the drawing
     * updates the Line Renderer with the current strokes, color, distance scale, line width etc
     */
    private void update() {

        try {

            // Update ARCore frame
            mFrame = mSession.update();

            if (mAnchor == null){
                mAnchor =  mSession.createAnchor(
                        mFrame.getCamera().getPose()
                                .compose(Pose.makeTranslation(0, 0, -1f))
                                .extractTranslation());
            }

            if (bRefreshCloudRenderer.get()){
                mCloudShaderRenderer.setNeedsUpdate();
                mCloudShaderRenderer.checkUpload();
                bRefreshCloudRenderer.set(false);
                setDownloadAnimation();
            }

            if (bInitCloudRenderer.get()){
                List<Anchor> mCloudAnchors = new ArrayList<>();
                Anchor randomAnchor;
                long l = System.currentTimeMillis();
                Random random = new Random(l);
                for (int idx = 0; idx < Preference.arNumber; idx++){
                    float ox = random.nextFloat() * 0.2f - 0.1f;
                    float oy = random.nextFloat() * 2.5f - 1.25f;
                    float oz = random.nextFloat() * 4f - 2f;
                    randomAnchor =  mSession.createAnchor(
                            mFrame.getCamera().getPose()
                                    .compose(Pose.makeTranslation(ox, oy, -1.75f + oz))
                                    .extractTranslation());
                    mCloudAnchors.add(randomAnchor);
                }

                mCloudShaderRenderer.initAnchors(mCloudAnchors);
                mCloudShaderRenderer.setNeedsUpdate();
                mCloudShaderRenderer.checkUpload();
                bInitCloudRenderer.set(false);
                bRefreshCloudRenderer.set(true);
            }



            // Update tracking states
            mTrackingIndicator.setTrackingStates(mFrame, mAnchor);


            if (mTrackingIndicator.trackingState == TrackingState.TRACKING && !bHasTracked.get()) {
                bHasTracked.set(true);
            }

            // Get projection matrix.
            mFrame.getCamera().getProjectionMatrix(projmtx, 0, ARSettings.getNearClip(),
                    ARSettings.getFarClip());
            mFrame.getCamera().getViewMatrix(viewmtx, 0);

            float[] position = new float[3];

            mFrame.getCamera().getPose().getTranslation(position, 0);

            // Multiply the zero matrix
            Matrix.multiplyMM(viewmtx, 0, viewmtx, 0, mZeroMatrix, 0);

            // Check if camera has moved much, if thats the case, stop touchDown events
            // (stop drawing lines abruptly through the air)
            if (mLastFramePosition != null) {
                Vector3f distance = new Vector3f(position[0], position[1], position[2]);
                distance.sub(new Vector3f(mLastFramePosition[0], mLastFramePosition[1],
                        mLastFramePosition[2]));

                if (distance.length() > 0.15) {
                    bTouchDown.set(false);
                }
            }

            mLastFramePosition = position;

            // Add points to strokes from touch queue
            int numPoints = touchQueueSize.get();
            if (numPoints > TOUCH_QUEUE_SIZE) {
                numPoints = TOUCH_QUEUE_SIZE;
            }

            if (numPoints > 0) {
                if (bNewStroke.get()) {
                    bNewStroke.set(false);
                    addStroke();
                }

                Vector2f[] points = new Vector2f[numPoints];
                for (int i = 0; i < numPoints; i++) {
                    points[i] = touchQueue.get(i);
                    mLastTouch = new Vector2f(points[i].x, points[i].y);
                }
                addPoint2f(points);
            }

            // If no new points have been added, and touch is down, add last point again
            if (numPoints == 0 && bTouchDown.get()) {
                addPoint2f(mLastTouch);
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (numPoints > 0) {
                touchQueueSize.set(0);
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (bClearDrawing.get()) {
                bClearDrawing.set(false);
                clearDrawing();
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            // Check if we are still drawing, otherwise finish line
            if (isDrawing && !bTouchDown.get()) {
                isDrawing = false;
                if (!mStrokes.isEmpty()) {
                    mStrokes.get(mStrokes.size() - 1).finishStroke();
                }
            }

//            for (int i = 0; i < mStrokes.size(); i++) {
//                mStrokes.get(i).update();
//            }
            boolean renderNeedsUpdate = false;
            for (Stroke stroke : mSharedStrokes.values()) {
                if (stroke.update()) {
                    renderNeedsUpdate = true;
                }
            }
            if (renderNeedsUpdate) {
                mLineShaderRenderer.bNeedsUpdate.set(true);
            }

            if (bUndo.get()) {
                bUndo.set(false);
                if (mStrokes.size() > 0) {
                    int index = mStrokes.size() - 1;
//                    mPairSessionManager.undoStroke(mStrokes.get(index));
                    mStrokes.remove(index);
                    if (mStrokes.isEmpty()) {
                        showStrokeDependentUI();
                    }
                    mLineShaderRenderer.bNeedsUpdate.set(true);
                }
            }
            if (mLineShaderRenderer.bNeedsUpdate.get()) {
                mLineShaderRenderer.setColor(ARSettings.getColor());
                mLineShaderRenderer.mDrawDistance = ARSettings.getStrokeDrawDistance();
                float distanceScale = 0.0f;
                mLineShaderRenderer.setDistanceScale(distanceScale);
                mLineShaderRenderer.setLineWidth(mLineWidthMax);
                mLineShaderRenderer.clear();
                mLineShaderRenderer.updateStrokes(mStrokes);
                mLineShaderRenderer.upload();
            }

            mCloudShaderRenderer.checkUpload();

            float x = mAnchor.getPose().tx();
            float y = mAnchor.getPose().ty();
            float z = mAnchor.getPose().tz();

            System.out.println("x = "+ String.valueOf(x)
                    + " y = " + String.valueOf(y)
                    + " z = " + String.valueOf(z));

        } catch (Exception e) {
            Log.e(TAG, "update: ", e);
        }
    }

    /**
     * renderScene() clears the Color Buffer and Depth Buffer, draws the current texture from the
     * camera
     * and draws the Line Renderer if ARCore is tracking the world around it
     */
    private void renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mFrame != null) {
            mBackgroundRenderer.draw(mFrame);
        }


        // Draw background.
        if (mFrame != null) {

            // Draw Lines
            if (mTrackingIndicator.isTracking() || (
                    // keep painting through 5 frames where we're not tracking
                    (bHasTracked.get() && mFramesNotTracked < MAX_UNTRACKED_FRAMES))) {

                if (!mTrackingIndicator.isTracking()) {
                    mFramesNotTracked++;
                } else {
                    mFramesNotTracked = 0;
                }

                // If the anchor is set, set the modelMatrix of the line renderer to offset to the anchor
                if (mAnchor != null && mAnchor.getTrackingState() == TrackingState.TRACKING) {
                    mAnchor.getPose().toMatrix(mLineShaderRenderer.mModelMatrix, 0);


                }

                // Render the lines
                mLineShaderRenderer
                        .draw(viewmtx, projmtx, mScreenWidth, mScreenHeight,
                                ARSettings.getNearClip(),
                                ARSettings.getFarClip());

                mCloudShaderRenderer.draw(viewmtx, projmtx, mScreenWidth, mScreenHeight,
                        ARSettings.getNearClip(),
                        ARSettings.getFarClip());

            }

        }
    }

    /**
     * addStroke adds a new stroke to the scene
     */
    private void addStroke() {
        mLineWidthMax = mBrushSelector.getSelectedLineWidth().getWidth();

        Stroke stroke = new Stroke();
        stroke.localLine = true;
        stroke.setLineWidth(mLineWidthMax);
        mStrokes.add(stroke);

        showStrokeDependentUI();

        mTrackingIndicator.setDrawnInSession();
    }

    /**
     * addPoint2f adds a point to the current stroke
     *
     * @param touchPoint a 2D point in screen space and is projected into 3D world space
     */
    private void addPoint2f(Vector2f... touchPoint) {
        Vector3f[] newPoints = new Vector3f[touchPoint.length];
        for (int i = 0; i < touchPoint.length; i++) {
            newPoints[i] = LineUtils
                    .GetWorldCoords(touchPoint[i], mScreenWidth, mScreenHeight, projmtx, viewmtx);
        }

        addPoint3f(newPoints);
    }

    /**
     * addPoint3f adds a point to the current stroke
     *
     * @param newPoint a 3D point in world space
     */
    private void addPoint3f(Vector3f... newPoint) {
        Vector3f point;
        int index = mStrokes.size() - 1;

        if (index < 0)
            return;

        for (int i = 0; i < newPoint.length; i++) {
            if (mAnchor != null && mAnchor.getTrackingState() == TrackingState.TRACKING) {
                point = LineUtils.TransformPointToPose(newPoint[i], mAnchor.getPose());
                mStrokes.get(index).add(point);
                Log.i("ADD 3D Point", "With Anchor");
            } else {
                mStrokes.get(index).add(newPoint[i]);
                Log.i("ADD 3D Point", "Without Anchor");
            }
        }

        // update firebase database
        //mPairSessionManager.updateStroke(mStrokes.get(index));
        isDrawing = true;
    }

    /**
     * Clears the Datacollection of Strokes and sets the Line Renderer to clear and update itself
     * Designed to be executed on the GL Thread
     */
    private void clearDrawing() {
        mStrokes.clear();
        mLineShaderRenderer.clear();
        showStrokeDependentUI();
    }


    /**
     * onClickClear handles showing an AlertDialog to clear the drawing
     */
    private void onClickClear() {
        ClearDrawingDialog.newInstance(false).show(this);
    }


    /**
     * onClickUpload handles showing an AlertDialog to upload the drawing
     */
    private void onClickUpload() {
        UploadDrawingDialog.newInstance(false).show(this);
    }

    // ------- Touch events

    /**
     * onTouchEvent handles saving the lastTouch screen position and setting bTouchDown and
     * bNewStroke
     * AtomicBooleans to trigger addPoint3f and addStroke on the GL Thread to be called
     */
    @Override
    public boolean onTouchEvent(MotionEvent tap) {
        int action = tap.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            closeViewsOutsideTapTarget(tap);
        }

        // do not accept touch events through the playback view
        // or when we are not tracking
        //if (mPlaybackView.isOpen() || !mTrackingIndicator.isTracking()) {
        if (!mTrackingIndicator.isTracking()) {
            if (bTouchDown.get()) {
                bTouchDown.set(false);
            }
            return false;
        }

        if (mMode == Mode.VIEW){

            gestureDetector.onTouchEvent(tap);

            return true;
        }

        if (mMode == Mode.DRAW) {
            if (action == MotionEvent.ACTION_DOWN) {
                touchQueue.set(0, new Vector2f(tap.getX(), tap.getY()));
                bNewStroke.set(true);
                bTouchDown.set(true);
                touchQueueSize.set(1);

                bNewStroke.set(true);
                bTouchDown.set(true);

                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (bTouchDown.get()) {
                    int numTouches = touchQueueSize.addAndGet(1);
                    if (numTouches <= TOUCH_QUEUE_SIZE) {
                        touchQueue.set(numTouches - 1, new Vector2f(tap.getX(), tap.getY()));
                    }
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP
                    || tap.getAction() == MotionEvent.ACTION_CANCEL) {
                bTouchDown.set(false);
                return true;
            }
        }

        return false;
    }

    private void closeViewsOutsideTapTarget(MotionEvent tap) {
        if (isOutsideViewBounds(mBrushSelector, (int) tap.getRawX(), (int) tap.getRawY())
                && mBrushSelector.isOpen()) {
            mBrushSelector.close();
        }
    }

    private boolean isOutsideViewBounds(View view, int x, int y) {
        Rect outRect = new Rect();
        int[] location = new int[2];
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return !outRect.contains(x, y);
    }


    /**
     * The following six Override functions are required by RecordableSurfaceView.
     * */

    @Override
    public void onSurfaceDestroyed() {
        mBackgroundRenderer.clearGL();
        mLineShaderRenderer.clearGL();
        mCloudShaderRenderer.clearGL();
    }

    @Override
    public void onSurfaceCreated() {
        pointCloud.createOnGlThread(/*context=*/ this);
    }


    @Override
    public void onSurfaceChanged(int width, int height) {
        int rotation = Surface.ROTATION_0;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rotation = Surface.ROTATION_90;
        }
        mSession.setDisplayGeometry(rotation, width, height);
    }


    @Override
    public void onContextCreated() {
        mBackgroundRenderer.createOnGlThread(this);

        mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
        try {
            mLineShaderRenderer.createOnGlThread(ARActivity.this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mCloudShaderRenderer.createOnGlThread(ARActivity.this);

        mLineShaderRenderer.bNeedsUpdate.set(true);
        mCloudShaderRenderer.setNeedsUpdate();
    }

    @Override
    public void onPreDrawFrame() {
        update();
    }

    @Override
    public void onDrawFrame() {
        long renderStartTime = System.currentTimeMillis();

        renderScene();

        mRenderDuration = System.currentTimeMillis() - renderStartTime;
    }

    /**
     * Update visibility of all the Buttons and BrushSelector
     * Refresh can only be used when the program is in VIEW mode.
     * Return and BrushSelector can only be used when the program is in DRAW mode.
     * Undo, Clear, and Upload can only be used when user input strokes.
     * */
    private void showStrokeDependentUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnUndo.setVisibility(mStrokes.size() > 0 ? View.VISIBLE : View.GONE);
                btnClear.setVisibility(
                        (mStrokes.size() > 0 || mSharedStrokes.size() > 0) ? View.VISIBLE
                                : View.GONE);
                btnSave.setVisibility(mStrokes.size() > 0 ? View.VISIBLE : View.GONE);
                mBrushSelector.setVisibility(mStrokes.size() > 0 ? View.VISIBLE : View.GONE);
                btnReturn.setVisibility(mMode == Mode.DRAW? View.VISIBLE : View.GONE);
                btnRefresh.setVisibility(mMode == Mode.VIEW? View.VISIBLE : View.GONE);
                mTrackingIndicator.setHasStrokes(mStrokes.size() > 0);
            }
        });
    }

    /**
     * Rotate refresh button when downloading AR Cloud Object
     * */
    private void setDownloadAnimation(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (bInitCloudRenderer.get()){
                    mAnimator.start();
                }
                else{
                    mAnimator.end();
                }
            }
        });
    }

    /**
     * Start further operations if user choose to clear all the drawings.
     * */
    @Override
    public void onClearDrawingConfirmed() {
        bClearDrawing.set(true);
        if (bModeChange.get()){
            setMode(Mode.VIEW);
        }
        bModeChange.set(false);
        showStrokeDependentUI();
    }

    /**
     * Start further operations if user choose to upload their drawings.
     * */
    @Override
    public void onUploadDrawingConfirmed() {
        bUploadDrawing.set(true);
        uploadStrokes();
        // Post text form in PostFeedActivity
    }

    /**
     * onClickUndo handles the touch input on the GUI and sets the AtomicBoolean bUndo to be true
     * the actual undo functionality is executed in the GL Thread
     */
    public void onClickUndo(View button) {

        bUndo.set(true);

    }

    /**
     * Start further operations if user choose to refresh current Cloud AR Object.
     * */
    public void onClickRefresh(){
        mCloudShaderRenderer.clear();
        downloadStrokes();
        bInitCloudRenderer.set(true);
        setDownloadAnimation();
        mCloudShaderRenderer.setNeedsUpdate();
    }


    /**
     * OnClick callback for Views*/
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnClear:
                onClickClear();
                break;
            case R.id.btnSave:
                onClickUpload();
                //saveStrokes();
                break;
            case R.id.btnBack:
                if (mStrokes.size() > 0){
                    bModeChange.set(true);
                    onClickClear();
                }
                else{
                    setMode(Mode.VIEW);
                }

                break;
            case R.id.btnRefresh:
                onClickRefresh();
                break;

        }
        mBrushSelector.close();

    }


    /**
     * Update views for the given mode
     */
    private void setMode(Mode mode) {
        if (mMode != mode) {
            mMode = mode;

            switch (mMode) {
                case VIEW:
                    showView(mDrawUiContainer);
                    showView(mTrackingIndicator);
                    mTrackingIndicator.setDrawPromptEnabled(false);
                    break;
                case DRAW:
                    showView(mDrawUiContainer);
                    showView(mTrackingIndicator);
                    mTrackingIndicator.setDrawPromptEnabled(true);
                    break;
            }
            showStrokeDependentUI();
        }

    }

    /**
     * Set visibility of certain view and start animation
     * */
    private void showView(View toShow) {
        toShow.setVisibility(View.VISIBLE);
        toShow.animate().alpha(1).start();
    }

    @Override
    public void exitApp() {
        finish();
    }

    /**
     * Download strokes from FireBase storage
     */

    public void downloadStrokes() {
        // Get current location
        FLPC.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
            }
        }, null);
        FLPC.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // Modify policy: downloadable input stream
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    strokeHelper.searchNearbyAr(location, Preference.arDistance, Preference.arNumber);
                }
            }
        });
    }

    /**
     * Upload user created strokes to FireBase storage
     */

    public void uploadStrokes() {
        // Get current location
        FLPC.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
            }
        }, null);
        FLPC.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    buildArTextPost(location);
                    strokeHelper.setLocation(location);
                    // Upload to FireBase
                    try {
                        FileOutputStream fileOutputStream = getApplicationContext().openFileOutput("strokeFile.ser", getBaseContext().MODE_PRIVATE);
                        ObjectOutputStream out = new ObjectOutputStream(fileOutputStream);
                        out.writeObject(mStrokes);
                        out.close();
                        File file = new File(ARActivity.this.getFilesDir().getAbsolutePath() + "/strokeFile.ser");
                        Uri uri = Uri.fromFile(file);
                        strokeHelper.uploadStrokeFile(uri);
                        mCloudShaderRenderer.setNeedsUpdate();
                        mCloudShaderRenderer.update(mStrokes, mAnchor);
                        mCloudShaderRenderer.setNeedsUpdate();
                        setMode(Mode.VIEW);
                        clearDrawing();
                        showStrokeDependentUI();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.i(TAG, "Saved failed");
                    }
                }
            }
        });
    }

    /**
     * Callback of {@link OnStrokeUrlCompleteListener}
     */
    @Override
    public void startDownloadStrokes() {
        strokeHelper.downloadStrokeFiles();
    }


    /**
     * Callback of {@link OnArDownloadedListener}
     * @param ar all nearby ar objects
     */
    @Override
    public void setStrokeList(List<List<Stroke>> ar) {
        mCloudShaderRenderer.initStrokes(ar);
        mCloudShaderRenderer.setNeedsUpdate();
        mCloudShaderRenderer.checkUpload();
        bInitCloudRenderer.set(true);
    }

    /**
     * Generate text notification for AR posts being created.
     */

    private void buildArTextPost(Location location) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user.getUid();
        String text = getResources().getString(R.string.text_ar_post);
        int type = Post.TYPE_AR;
        final GeoPoint geo = new GeoPoint(location.getLatitude(), location.getLongitude());
        Timestamp time = new Timestamp(Calendar.getInstance().getTime());
        Post newPost = new Post(uid, type, text, "", geo, time);
        Map<String, Object> post = newPost.getHashMap();
        CollectionReference postRef = FirebaseFirestore.getInstance().collection("posts");
        final GeoFirestore geoFirestore = new GeoFirestore(postRef);
        postRef.add(post).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                geoFirestore.setLocation(documentReference.getId(), geo);
                Log.i(TAG, "Write text post success");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG, "Write text post failed");
            }
        });
    }
}
