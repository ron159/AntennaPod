package de.danoeh.antennapod.ui.preferences.preference;

import android.content.Context;
import android.util.AttributeSet;
import java.text.NumberFormat;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import de.danoeh.antennapod.ui.preferences.R;
import de.danoeh.antennapod.ui.preferences.databinding.StateSyncStatusPreferenceBinding;

public class StateSyncStatusPreference extends Preference {
    private @StringRes int stateResId = R.string.statesync_status_idle;
    private long pendingChangeCount;
    private long serverCursor;
    private CharSequence lastSuccessfulSync;

    public StateSyncStatusPreference(Context context) {
        super(context);
        initialize();
    }

    public StateSyncStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setLayoutResource(R.layout.state_sync_status_preference);
        setSelectable(false);
    }

    public void setStatus(@StringRes int stateResId, long pendingChangeCount,
                          long serverCursor, CharSequence lastSuccessfulSync) {
        this.stateResId = stateResId;
        this.pendingChangeCount = pendingChangeCount;
        this.serverCursor = serverCursor;
        this.lastSuccessfulSync = lastSuccessfulSync;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        StateSyncStatusPreferenceBinding binding = StateSyncStatusPreferenceBinding.bind(holder.itemView);
        binding.stateSyncStatusIcon.setImageResource(getStateIcon());
        binding.stateSyncStatusState.setText(stateResId);
        binding.stateSyncStatusLastSuccess.setText(lastSuccessfulSync == null
                ? getContext().getString(R.string.statesync_status_never) : lastSuccessfulSync);
        NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        binding.stateSyncStatusPending.setText(numberFormat.format(pendingChangeCount));
        binding.stateSyncStatusCursor.setText(numberFormat.format(serverCursor));
    }

    private @DrawableRes int getStateIcon() {
        if (stateResId == R.string.statesync_status_synchronizing) {
            return R.drawable.ic_refresh;
        } else if (stateResId == R.string.statesync_status_up_to_date) {
            return R.drawable.ic_check;
        } else if (stateResId == R.string.statesync_status_failed) {
            return R.drawable.ic_error;
        }
        return R.drawable.ic_history;
    }
}
