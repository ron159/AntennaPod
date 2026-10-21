package de.danoeh.antennapod.ui.preferences.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.TextView;
import androidx.preference.ListPreference;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.ui.preferences.R;

public class MaterialListPreference extends ListPreference {

    public MaterialListPreference(Context context) {
        super(context);
    }

    public MaterialListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getTitle());
        builder.setIcon(getDialogIcon());
        builder.setNegativeButton(getNegativeButtonText(), null);
        if (getDialogMessage() != null) {
            TextView message = (TextView) LayoutInflater.from(builder.getContext())
                    .inflate(R.layout.preference_dialog_message, null);
            message.setText(getDialogMessage());
            builder.setView(message);
        }

        CharSequence[] values = getEntryValues();
        int selected = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].toString().equals(getValue())) {
                selected = i;
            }
        }
        builder.setSingleChoiceItems(getEntries(), selected, (dialog, which) -> {
            dialog.dismiss();
            if (which >= 0 && getEntryValues() != null) {
                String value = getEntryValues()[which].toString();
                if (callChangeListener(value)) {
                    setValue(value);
                }
            }
        });
        builder.show();
    }
}
