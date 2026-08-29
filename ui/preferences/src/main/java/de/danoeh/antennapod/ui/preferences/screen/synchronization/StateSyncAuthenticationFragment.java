package de.danoeh.antennapod.ui.preferences.screen.synchronization;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.net.sync.serviceinterface.StateSyncCoordinator;
import de.danoeh.antennapod.ui.preferences.R;
import de.danoeh.antennapod.ui.preferences.databinding.StatesyncAuthDialogBinding;

public final class StateSyncAuthenticationFragment extends DialogFragment {
    public static final String TAG = "StateSyncAuthenticationFragment";
    private StatesyncAuthDialogBinding viewBinding;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewBinding = StatesyncAuthDialogBinding.inflate(getLayoutInflater());
        viewBinding.serverUrlText.setText(StateSyncCoordinator.getInstance().getServerUrl());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.statesync_title)
                .setView(viewBinding.getRoot())
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.statesync_connect, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> connect(dialog)));
        return dialog;
    }

    @Override
    public void onDestroyView() {
        viewBinding = null;
        super.onDestroyView();
    }

    private void connect(AlertDialog dialog) {
        viewBinding.serverUrlTextInput.setError(null);
        viewBinding.tokenTextInput.setError(null);
        try {
            StateSyncCoordinator.getInstance().configure(
                    String.valueOf(viewBinding.serverUrlText.getText()).trim(),
                    String.valueOf(viewBinding.tokenText.getText()).trim());
            if (getParentFragment() instanceof SynchronizationPreferencesFragment parent) {
                parent.refreshStateSyncScreen();
            }
            dialog.dismiss();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            viewBinding.serverUrlTextInput.setError(getString(R.string.statesync_invalid_configuration));
        }
    }
}
