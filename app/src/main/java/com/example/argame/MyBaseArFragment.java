package com.example.argame;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;
import com.google.ar.sceneform.ux.PlaneDiscoveryController;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class MyBaseArFragment extends Fragment
        implements Scene.OnPeekTouchListener, Scene.OnUpdateListener {
    private static final String TAG = BaseArFragment.class.getSimpleName();
    /** Invoked when an ARCore plane is tapped. */
    public interface OnTapArPlaneListener {
        /**
         * Called when an ARCore plane is tapped. The callback will only be invoked if no {@link
         * com.google.ar.sceneform.Node} was tapped.
         *
         * @see #setOnSingleTapArPlaneListener(OnTapArPlaneListener)
         * @param hitResult The ARCore hit result that occurred when tapping the plane
         * @param plane The ARCore Plane that was tapped
         * @param motionEvent the motion event that triggered the tap
         */
        void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent);
    }

    public interface OnFlingListener {
        void onFling(float velocityX, float velocityY);
    }

    private static final int RC_PERMISSIONS = 1010;
    private boolean installRequested;
    private boolean sessionInitializationFailed = false;
    private ArSceneView arSceneView;
    private PlaneDiscoveryController planeDiscoveryController;
    private TransformationSystem transformationSystem;
    private GestureDetector gestureDetector;
    private FrameLayout frameLayout;
    private boolean isStarted;
    private boolean canRequestDangerousPermissions = true;
    @Nullable private OnTapArPlaneListener onSingleTapArPlaneListener;
    @Nullable private OnTapArPlaneListener onDoubleTapArPlaneListener;
    @Nullable private OnTapArPlaneListener onLongPressArPlaneListener;
    private OnFlingListener onFlingListener;

    @SuppressWarnings({"initialization"})
    private final ViewTreeObserver.OnWindowFocusChangeListener onFocusListener =
            (hasFocus -> onWindowFocusChanged(hasFocus));

    /** Gets the ArSceneView for this fragment. */
    public ArSceneView getArSceneView() {
        return arSceneView;
    }

    /**
     * Gets the plane discovery controller, which displays instructions for how to scan for planes.
     */
    public PlaneDiscoveryController getPlaneDiscoveryController() {
        return planeDiscoveryController;
    }

    /**
     * Gets the transformation system, which is used by {@link TransformableNode} for detecting
     * gestures and coordinating which node is selected.
     */
    public TransformationSystem getTransformationSystem() {
        return transformationSystem;
    }

    /**
     * Registers a callback to be invoked when an ARCore Plane is long tapped. The callback will only be
     * invoked if no {@link com.google.ar.sceneform.Node} was tapped.
     *
     * @param onDoubleTapArPlaneListener the {@link MyBaseArFragment.OnTapArPlaneListener} to attach
     */
    public void setOnDoubleTapArPlaneListener(@Nullable OnTapArPlaneListener onDoubleTapArPlaneListener) {
        this.onDoubleTapArPlaneListener = onDoubleTapArPlaneListener;
    }

    /**
     * Registers a callback to be invoked when an ARCore Plane is tapped. The callback will only be
     * invoked if no {@link com.google.ar.sceneform.Node} was tapped.
     *
     * @param onSingleTapArPlaneListener the {@link MyBaseArFragment.OnTapArPlaneListener} to attach
     */
    public void setOnSingleTapArPlaneListener(@Nullable OnTapArPlaneListener onSingleTapArPlaneListener) {
        this.onSingleTapArPlaneListener = onSingleTapArPlaneListener;
    }

    /**
     * Registers a callback to be invoked when an ARCore Plane is tapped. The callback will only be
     * invoked if no {@link com.google.ar.sceneform.Node} was tapped.
     *
     * @param onLongPressArPlaneListener the {@link MyBaseArFragment.OnTapArPlaneListener} to attach
     */
    public void setOnLongPressArPlaneListener(@Nullable OnTapArPlaneListener onLongPressArPlaneListener) {
        this.onLongPressArPlaneListener = onLongPressArPlaneListener;
    }

    public void setOnFlingListener(OnFlingListener onFlingListener) {
        this.onFlingListener = onFlingListener;
    }

    @Override
    @SuppressWarnings({"initialization"})
    // Suppress @UnderInitialization warning.
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        frameLayout =
                (FrameLayout) inflater.inflate(R.layout.sceneform_ux_fragment_layout, container, false);
        arSceneView = (ArSceneView) frameLayout.findViewById(R.id.sceneform_ar_scene_view);

        // Setup the instructions view.
        View instructionsView = loadPlaneDiscoveryView(inflater, container);
        if (instructionsView != null) {
            frameLayout.addView(instructionsView);
        }
        planeDiscoveryController = new PlaneDiscoveryController(instructionsView);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Enforce API level 24
            return frameLayout;
        }

        transformationSystem = makeTransformationSystem();

        gestureDetector =
                new GestureDetector(
                        getContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                MyBaseArFragment.this.onDoubleTap(e);
                                return true;
                            }

                            @Override
                            public void onLongPress(MotionEvent e) {
                                MyBaseArFragment.this.onLongPress(e);
                            }

                            @Override
                            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                                MyBaseArFragment.this.onFling(velocityX, velocityY);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });

        arSceneView.getScene().addOnPeekTouchListener(this);
        arSceneView.getScene().addOnUpdateListener(this);

        if (isArRequired()) {
            // Request permissions
            requestDangerousPermissions();
        }

        // Make the app immersive and don't turn off the display.
        arSceneView.getViewTreeObserver().addOnWindowFocusChangeListener(onFocusListener);
        return frameLayout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        arSceneView.getViewTreeObserver().removeOnWindowFocusChangeListener(onFocusListener);
    }

    /**
     * Returns true if this application is AR Required, false if AR Optional. This is called when
     * initializing the application and the session.
     */
    public abstract boolean isArRequired();

    /**
     * Returns an array of dangerous permissions that are required by the app in addition to
     * Manifest.permission.CAMERA, which is needed by ARCore. If no additional permissions are needed,
     * an empty array should be returned.
     */
    public abstract String[] getAdditionalPermissions();

    /**
     * Starts the process of requesting dangerous permissions. This combines the CAMERA permission
     * required of ARCore and any permissions returned from getAdditionalPermissions(). There is no
     * specific processing on the result of the request, subclasses can override
     * onRequestPermissionsResult() if additional processing is needed.
     *
     * <p>{@link #setCanRequestDangerousPermissions(Boolean)} can stop this function from doing
     * anything.
     */
    protected void requestDangerousPermissions() {
        if (!canRequestDangerousPermissions) {
            // If this is in progress, don't do it again.
            return;
        }
        canRequestDangerousPermissions = false;

        List<String> permissions = new ArrayList<String>();
        String[] additionalPermissions = getAdditionalPermissions();
        int permissionLength = additionalPermissions != null ? additionalPermissions.length : 0;
        for (int i = 0; i < permissionLength; ++i) {
            if (ActivityCompat.checkSelfPermission(requireActivity(), additionalPermissions[i])
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(additionalPermissions[i]);
            }
        }

        // Always check for camera permission
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (!permissions.isEmpty()) {
            // Request the permissions
            requestPermissions(permissions.toArray(new String[permissions.size()]), RC_PERMISSIONS);
        }
    }

    /**
     * Receives the results for permission requests.
     *
     * <p>Brings up a dialog to request permissions. The dialog can send the user to the Settings app,
     * or finish the activity.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        AlertDialog.Builder builder;
        builder =
                new AlertDialog.Builder(requireActivity(), android.R.style.Theme_Material_Dialog_Alert);

        builder
                .setTitle("Camera permission required")
                .setMessage("Add camera permission via Settings?")
                .setPositiveButton(
                        android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // If Ok was hit, bring up the Settings app.
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", requireActivity().getPackageName(), null));
                                requireActivity().startActivity(intent);
                                // When the user closes the Settings app, allow the app to resume.
                                // Allow the app to ask for permissions again now.
                                setCanRequestDangerousPermissions(true);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setOnDismissListener(
                        new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(final DialogInterface arg0) {
                                // canRequestDangerousPermissions will be true if "OK" was selected from the dialog,
                                // false otherwise.  If "OK" was selected do nothing on dismiss, the app will
                                // continue and may ask for permission again if needed.
                                // If anything else happened, finish the activity when this dialog is
                                // dismissed.
                                if (!getCanRequestDangerousPermissions()) {
                                    requireActivity().finish();
                                }
                            }
                        })
                .show();
    }

    /**
     * If true, {@link #requestDangerousPermissions()} returns without doing anything, if false
     * permissions will be requested
     */
    protected Boolean getCanRequestDangerousPermissions() {
        return canRequestDangerousPermissions;
    }

    /**
     * If true, {@link #requestDangerousPermissions()} returns without doing anything, if false
     * permissions will be requested
     */
    protected void setCanRequestDangerousPermissions(Boolean canRequestDangerousPermissions) {
        this.canRequestDangerousPermissions = canRequestDangerousPermissions;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isArRequired() && arSceneView.getSession() == null) {
            initializeSession();
        }
        start();
    }


    protected final boolean requestInstall() throws UnavailableException {
        switch (ArCoreApk.getInstance().requestInstall(requireActivity(), !installRequested)) {
            case INSTALL_REQUESTED:
                installRequested = true;
                return true;
            case INSTALLED:
                break;
        }
        return false;
    }

    /**
     * Initializes the ARCore session. The CAMERA permission is checked before checking the
     * installation state of ARCore. Once the permissions and installation are OK, the method
     * #getSessionConfiguration(Session session) is called to get the session configuration to use.
     * Sceneform requires that the ARCore session be updated using LATEST_CAMERA_IMAGE to avoid
     * blocking while drawing. This mode is set on the configuration object returned from the
     * subclass.
     */
    protected final void initializeSession() {

        // Only try once
        if (sessionInitializationFailed) {
            return;
        }
        // if we have the camera permission, create the session
        if (ContextCompat.checkSelfPermission(requireActivity(), "android.permission.CAMERA")
                == PackageManager.PERMISSION_GRANTED) {

            UnavailableException sessionException = null;
            try {
                if (requestInstall()) {
                    return;
                }

                Session session = createSession();

                Config config = getSessionConfiguration(session);
                // Force the non-blocking mode for the session.

                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                session.configure(config);
                getArSceneView().setupSession(session);
                return;
            } catch (UnavailableException e) {
                sessionException = e;
            } catch (Exception e) {
                sessionException = new UnavailableException();
                sessionException.initCause(e);
            }
            sessionInitializationFailed = true;
            handleSessionException(sessionException);

        } else {
            requestDangerousPermissions();
        }
    }

    private Session createSession()
            throws UnavailableSdkTooOldException, UnavailableDeviceNotCompatibleException,
            UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        Session session = createSessionWithFeatures();
        if (session == null) {
            session = new Session(requireActivity());
        }
        return session;
    }


    Session createSessionWithFeatures()
            throws UnavailableSdkTooOldException, UnavailableDeviceNotCompatibleException,
            UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        return new Session(requireActivity(), getSessionFeatures());
    }

    /**
     * Creates the transformation system used by this fragment. Can be overridden to create a custom
     * transformation system.
     */
    protected TransformationSystem makeTransformationSystem() {
        FootprintSelectionVisualizer selectionVisualizer = new FootprintSelectionVisualizer();

        TransformationSystem transformationSystem =
                new TransformationSystem(getResources().getDisplayMetrics(), selectionVisualizer);

        setupSelectionRenderable(selectionVisualizer);

        return transformationSystem;
    }

    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})

    protected void setupSelectionRenderable(FootprintSelectionVisualizer selectionVisualizer) {
        ModelRenderable.builder()
                .setSource(getActivity(), R.raw.sceneform_footprint)
                .build()
                .thenAccept(
                        renderable -> {
                            // If the selection visualizer already has a footprint renderable, then it was set to
                            // something custom. Don't override the custom visual.
                            if (selectionVisualizer.getFootprintRenderable() == null) {
                                selectionVisualizer.setFootprintRenderable(renderable);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(
                                            getContext(), "Unable to load footprint renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }

    protected abstract void handleSessionException(UnavailableException sessionException);

    protected abstract Config getSessionConfiguration(Session session);

    /**
     * Specifies additional features for creating an ARCore {@link com.google.ar.core.Session}. See
     * {@link com.google.ar.core.Session.Feature}.
     */

    protected abstract Set<Session.Feature> getSessionFeatures();

    protected void onWindowFocusChanged(boolean hasFocus) {
        FragmentActivity activity = getActivity();
        if (hasFocus && activity != null) {
            // Standard Android full-screen functionality.
            activity
                    .getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stop();
    }

    @Override
    public void onDestroy() {
        stop();
        arSceneView.destroy();
        super.onDestroy();
    }

    @Override
    public void onPeekTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
        transformationSystem.onTouch(hitTestResult, motionEvent);

        if (hitTestResult.getNode() == null) {
            gestureDetector.onTouchEvent(motionEvent);
        }
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        Frame frame = arSceneView.getArFrame();
        if (frame == null) {
            return;
        }

        for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                planeDiscoveryController.hide();
            }
        }
    }

    private void start() {
        if (isStarted) {
            return;
        }

        if (getActivity() != null) {
            isStarted = true;
            try {
                arSceneView.resume();
            } catch (CameraNotAvailableException ex) {
                sessionInitializationFailed = true;
            }
            if (!sessionInitializationFailed) {
                planeDiscoveryController.show();
            }
        }
    }

    private void stop() {
        if (!isStarted) {
            return;
        }

        isStarted = false;
        planeDiscoveryController.hide();
        arSceneView.pause();
    }

    // Load the default view we use for the plane discovery instructions.
    @Nullable

    private View loadPlaneDiscoveryView(LayoutInflater inflater, @Nullable ViewGroup container) {
        return inflater.inflate(R.layout.sceneform_plane_discovery_layout, container, false);
    }

    private void onSingleTap(MotionEvent motionEvent) {
        Frame frame = arSceneView.getArFrame();

        transformationSystem.selectNode(null);

        // Local variable for nullness static-analysis.
        OnTapArPlaneListener onTapArPlaneListener = this.onSingleTapArPlaneListener;

        if (frame != null && onTapArPlaneListener != null) {
            if (motionEvent != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(motionEvent)) {
                    Trackable trackable = hit.getTrackable();
                    if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                        Plane plane = (Plane) trackable;
                        onTapArPlaneListener.onTapPlane(hit, plane, motionEvent);
                        break;
                    }
                }
            }
        }
    }

    private void onDoubleTap(MotionEvent motionEvent) {
        Frame frame = arSceneView.getArFrame();

        transformationSystem.selectNode(null);

        // Local variable for nullness static-analysis.
        OnTapArPlaneListener onTapArPlaneListener = this.onDoubleTapArPlaneListener;

        if (frame != null && onTapArPlaneListener != null) {
            if (motionEvent != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(motionEvent)) {
                    Trackable trackable = hit.getTrackable();
                    if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                        Plane plane = (Plane) trackable;
                        onTapArPlaneListener.onTapPlane(hit, plane, motionEvent);
                        break;
                    }
                }
            }
        }
    }

    private void onLongPress(MotionEvent motionEvent) {
        Frame frame = arSceneView.getArFrame();

        transformationSystem.selectNode(null);

        // Local variable for nullness static-analysis.
        OnTapArPlaneListener onTapArPlaneListener = this.onLongPressArPlaneListener;

        if (frame != null && onTapArPlaneListener != null) {
            if (motionEvent != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(motionEvent)) {
                    Trackable trackable = hit.getTrackable();
                    if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                        Plane plane = (Plane) trackable;
                        onTapArPlaneListener.onTapPlane(hit, plane, motionEvent);
                        break;
                    }
                }
            }
        }
    }

    private void onFling(float velocityX, float velocityY) {
        OnFlingListener onFlingListener = this.onFlingListener;

        if (onFlingListener != null) {
            onFlingListener.onFling(velocityX, velocityY);
        }
    }
}
