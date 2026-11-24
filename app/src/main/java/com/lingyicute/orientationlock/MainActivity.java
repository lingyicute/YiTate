package com.lingyicute.orientationlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.service.YiTateService;
import com.lingyicute.orientationlock.utils.*;

import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 100;

    private PreferenceManager preferenceManager;
    private AlertDialog rationaleDialog;
    private boolean requestingFromRationale;
    private int displayedOrientation = Integer.MIN_VALUE;
    private final SharedPreferences.OnSharedPreferenceChangeListener selectionListener = (store, key) -> {
        if (PreferenceManager.KEY_ORIENTATION.equals(key)) {
            showSelection(preferenceManager.getOrientation());
        }
    };
    private Integer pendingOrientation;
    private static final String STATE_PENDING_ORIENTATION = "pending_orientation";
    private static final String STATE_SHOWING_RATIONALE = "showing_rationale";
    private static final String STATE_REQUESTING_FROM_RATIONALE = "requesting_from_rationale";
    private final SparseIntArray orientationMap = new SparseIntArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initView();
        preferenceManager = PreferenceManager.getInstance(this);

        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.id.tv_orientation_default);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, R.id.tv_orientation_full_sensor);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.id.tv_orientation_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, R.id.tv_orientation_reverse_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.id.tv_orientation_sensor_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.id.tv_orientation_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, R.id.tv_orientation_reverse_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.id.tv_orientation_sensor_portrait);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_PENDING_ORIENTATION)) {
                pendingOrientation = savedInstanceState.getInt(STATE_PENDING_ORIENTATION);
            }
            requestingFromRationale = savedInstanceState.getBoolean(STATE_REQUESTING_FROM_RATIONALE);
        }
        if (!requestingFromRationale && (savedInstanceState == null
                || savedInstanceState.getBoolean(STATE_SHOWING_RATIONALE))) {
            checkNotificationPermission();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        preferenceManager.registerListener(selectionListener);
        showSelection(PermissionUtils.isDrawOverlaysPermissionGranted(this)
                ? preferenceManager.getOrientation()
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    protected void onStop() {
        preferenceManager.unregisterListener(selectionListener);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            requestingFromRationale = false;
            preferenceManager.setNotificationPermissionRequested(false);
            preferenceManager.setNotificationRationaleShown(false);
            if (rationaleDialog != null && rationaleDialog.isShowing()) {
                rationaleDialog.dismiss();
            }
            rationaleDialog = null;
        }
        boolean granted = PermissionUtils.isDrawOverlaysPermissionGranted(this);
        int orientation = granted ? preferenceManager.getOrientation()
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (pendingOrientation != null && granted) orientation = pendingOrientation;
        pendingOrientation = null;
        // Reconcile permission revocation and complete the selection after Settings returns.
        applySelection(orientation);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_REQUESTING_FROM_RATIONALE, requestingFromRationale);
        if (pendingOrientation != null) state.putInt(STATE_PENDING_ORIENTATION, pendingOrientation);
        if (rationaleDialog != null && rationaleDialog.isShowing()) {
            state.putBoolean(STATE_SHOWING_RATIONALE, true);
        }
    }

    @Override
    protected void onDestroy() {
        if (rationaleDialog != null && rationaleDialog.isShowing()) {
            rationaleDialog.dismiss();
        }
        rationaleDialog = null;
        super.onDestroy();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                preferenceManager.setNotificationPermissionRequested(false);
                preferenceManager.setNotificationRationaleShown(false);
                return;
            }
            boolean rationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
            if (rationale ? preferenceManager.isNotificationRationaleShown()
                    : preferenceManager.isNotificationPermissionRequested()) {
                // Already refused (either permanently or by dismissing the rationale):
                // prompting again on every cold start would only nag or fail silently.
                return;
            }
            if (rationale) {
                // Persist only once the user answers; an interrupted dialog must be shown again.
                rationaleDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.notification_permission_title)
                        .setMessage(R.string.notification_permission_message)
                        .setPositiveButton(R.string.allow, (dialog, which) -> {
                            requestingFromRationale = true;
                            requestPermissions(
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    NOTIFICATION_PERMISSION_REQUEST_CODE);
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                                preferenceManager.setNotificationRationaleShown(true))
                        .setOnCancelListener(dialog ->
                                preferenceManager.setNotificationRationaleShown(true))
                        .show();
            } else {
                // First request, or a retry after an earlier prompt was dismissed unanswered.
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) return;
        boolean fromRationale = requestingFromRationale;
        requestingFromRationale = false;
        if (grantResults.length != 1 || permissions.length != 1
                || !Manifest.permission.POST_NOTIFICATIONS.equals(permissions[0])) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            preferenceManager.setNotificationPermissionRequested(false);
            preferenceManager.setNotificationRationaleShown(false);
            // onResume reconciles the service and overlay permission after the prompt.
        } else {
            // The Permission Controller may return DENIED even when the prompt was dismissed.
            // A normal first refusal enables the rationale; refusing the retry disables it.
            // An unchanged state does not establish a new refusal.
            if (grantResults[0] != PackageManager.PERMISSION_DENIED
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    == fromRationale) return;
            preferenceManager.setNotificationPermissionRequested(true);
            if (fromRationale) preferenceManager.setNotificationRationaleShown(true);
            // Notification permission is NOT a prerequisite for a foreground service.
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void applySelection(int orientation) {
        preferenceManager.setOrientation(orientation);
        showSelection(orientation);
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return;
        Intent intent = new Intent(this, YiTateService.class).setAction(YiTateService.ACTION_SYNC_ORIENTATION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (startForegroundService(intent) != null) return;
            } else if (startService(intent) != null) {
                return;
            }
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Unable to start orientation service", e);
        }
        applySelection(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        Toast.makeText(this, R.string.orientation_failed, Toast.LENGTH_LONG).show();
    }

    private void showSelection(int orientation) {
        if (displayedOrientation == orientation) return;
        int lastViewId = orientationMap.get(displayedOrientation, View.NO_ID);
        if (lastViewId != View.NO_ID) {
            View lastView = findViewById(lastViewId);
            lastView.setSelected(false);
        }
        int viewId = orientationMap.get(orientation, View.NO_ID);
        if (viewId != View.NO_ID) {
            View currentView = findViewById(viewId);
            currentView.setSelected(true);
        }
        displayedOrientation = orientation;
    }

    private void initView() {
        LinearLayout rootView = findViewById(R.id.ll_root);
        List<View> childViews = ViewUtils.getAllChildViews(rootView);
        Drawable icon;
        int iconSize = ViewUtils.dp(this, 32);
        int iconColor = ContextUtils.getColorFromAttr(this, android.R.attr.colorAccent);
        for (View view : childViews) {
            if (view instanceof TextView) {
                Drawable[] drawables = ((TextView) view).getCompoundDrawables();
                icon = drawables[1];
                if (icon == null) {
                    continue;
                }
                icon = icon.mutate();
                icon.setBounds(0, 0, iconSize, iconSize);
                ViewUtils.setDrawableColorFilter(icon, iconColor);
                ((TextView) view).setCompoundDrawables(drawables[0], icon, drawables[2], drawables[3]);
                if (view.getId() != View.NO_ID) {
                    view.setOnClickListener(this);
                }
            }
        }
        findViewById(R.id.iv_about).setOnClickListener(v -> {
            Uri uri = Uri.parse("https://github.com/lingyicute/YiTate/blob/main/README.md");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "Unable to open the project page", e);
                Toast.makeText(this, R.string.about_link_unavailable, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        int index = orientationMap.indexOfValue(id);
        if (index < 0) {
            return;
        }
        int orientation = orientationMap.keyAt(index);
        pendingOrientation = null;
        SimpleLog.d(TAG, "select orientation: " + orientation);
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                && !PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            applySelection(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            pendingOrientation = orientation;
            if (PermissionUtils.requestDrawOverlaysPermission(this)) {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            } else {
                pendingOrientation = null;
                Toast.makeText(this, R.string.overlay_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        } else {
            applySelection(orientation);
        }
    }
}
