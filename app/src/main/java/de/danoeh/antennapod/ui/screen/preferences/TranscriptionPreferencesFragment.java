package de.danoeh.antennapod.ui.screen.preferences;

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.ModelDownloadEvent;
import de.danoeh.antennapod.event.TranscriptionEvent;
import de.danoeh.antennapod.transcription.serviceinterface.AiSettings;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModel;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionModels;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionServiceInterface;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TranscriptionPreferencesFragment extends AnimatedPreferenceFragment {
    private static final String MODEL_CATEGORY = "transcriptionModelCategory";
    private static final String AUTOMATIC = "transcriptionAutomatic";
    private static final String SPEAKER_LABELS = "transcriptionSpeakerLabels";
    private static final String AI_SETTINGS = "transcriptionAiSettings";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_transcription);
        setupControls();
        refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.transcription_settings);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTranscriptionEvent(TranscriptionEvent event) {
        if (event.getType() == TranscriptionEvent.Type.MODEL_CHANGED) {
            refresh();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onModelDownloadEvent(ModelDownloadEvent event) {
        refresh();
        if (event.getState() == ModelDownloadEvent.State.FAILED) {
            Toast.makeText(requireContext(), R.string.transcription_model_download_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setupControls() {
        SwitchPreferenceCompat automatic = findPreference(AUTOMATIC);
        automatic.setOnPreferenceChangeListener((preference, newValue) -> {
            TranscriptionServiceInterface.get().setAutomaticTranscriptionEnabled(
                    requireContext(), (Boolean) newValue);
            return true;
        });
        SwitchPreferenceCompat speakers = findPreference(SPEAKER_LABELS);
        speakers.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue && !TranscriptionServiceInterface.get().isModelInstalled(
                    requireContext(), TranscriptionModels.getSpeakerDiarization().getId())) {
                TranscriptionServiceInterface.get().downloadModel(
                        requireContext(), TranscriptionModels.getSpeakerDiarization().getId());
                refresh();
                Toast.makeText(requireContext(), R.string.transcription_model_download_queued,
                        Toast.LENGTH_LONG).show();
                return false;
            }
            TranscriptionServiceInterface.get().setSpeakerLabelsEnabled(requireContext(), (Boolean) newValue);
            return true;
        });
        findPreference(AI_SETTINGS).setOnPreferenceClickListener(preference -> {
            showAiSettings();
            return true;
        });
    }

    private void refresh() {
        if (getContext() == null) {
            return;
        }
        final TranscriptionServiceInterface service = TranscriptionServiceInterface.get();
        PreferenceCategory category = findPreference(MODEL_CATEGORY);
        category.removeAll();
        List<TranscriptionModel> models = new ArrayList<>(TranscriptionModels.getAll());
        models.add(TranscriptionModels.getSpeakerDiarization());
        for (TranscriptionModel model : models) {
            boolean installed = service.isModelInstalled(requireContext(), model.getId());
            boolean selected = model.getId().equals(service.getSelectedModelId(requireContext()));
            int downloadProgress = service.getModelDownloadProgress(requireContext(), model.getId());
            Preference preference = new Preference(requireContext());
            preference.setTitle(modelTitle(model));
            preference.setSummary(modelSummary(model, installed, selected, downloadProgress));
            preference.setIcon(modelIcon(installed, selected));
            preference.setEnabled(installed || downloadProgress < 0);
            preference.setOnPreferenceClickListener(value -> {
                showModelActions(model);
                return true;
            });
            category.addPreference(preference);
        }
        SwitchPreferenceCompat automatic = findPreference(AUTOMATIC);
        automatic.setChecked(service.isAutomaticTranscriptionEnabled(requireContext()));
        SwitchPreferenceCompat speakers = findPreference(SPEAKER_LABELS);
        speakers.setChecked(service.isSpeakerLabelsEnabled(requireContext()));
        AiSettings settings = service.getAiSettings(requireContext());
        findPreference(AI_SETTINGS).setSummary(settings.getBaseUrl() + " · " + settings.getModel() + "\n"
                + getString(settings.isApiKeyStored()
                ? R.string.transcription_ai_api_key_saved : R.string.transcription_ai_api_key_missing));
    }

    private String modelSummary(TranscriptionModel model, boolean installed, boolean selected,
                                int downloadProgress) {
        String size = Formatter.formatShortFileSize(requireContext(), model.getDownloadSizeBytes());
        String state;
        if (selected && installed) {
            state = getString(R.string.transcription_model_selected, size);
        } else if (installed) {
            state = getString(R.string.transcription_model_installed) + " · " + size;
        } else if (downloadProgress == 0) {
            state = getString(R.string.transcription_model_download_pending, size);
        } else if (downloadProgress > 0) {
            state = getString(R.string.transcription_model_downloading, downloadProgress, size);
        } else {
            state = getString(R.string.transcription_model_not_installed, size);
        }
        return modelDescription(model) + "\n" + state;
    }

    private void showModelActions(TranscriptionModel model) {
        TranscriptionServiceInterface service = TranscriptionServiceInterface.get();
        if (!service.isModelInstalled(requireContext(), model.getId())) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(modelTitle(model))
                    .setMessage(modelDescription(model))
                    .setPositiveButton(R.string.transcription_download_model, (dialog, which) -> {
                        service.downloadModel(requireContext(), model.getId());
                        refresh();
                        Toast.makeText(requireContext(), R.string.transcription_model_download_queued,
                                Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton(R.string.cancel_label, null)
                    .show();
            return;
        }
        boolean speakerModel = model.getEngine() == TranscriptionModel.Engine.SPEAKER_DIARIZATION;
        String[] actions = speakerModel
                ? new String[]{getString(R.string.transcription_delete_model)}
                : new String[]{getString(R.string.transcription_select_model),
                        getString(R.string.transcription_delete_model)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(modelTitle(model))
                .setItems(actions, (dialog, which) -> {
                    if (!speakerModel && which == 0) {
                        service.setSelectedModelId(requireContext(), model.getId());
                        refresh();
                    } else {
                        deleteModel(model);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void deleteModel(TranscriptionModel model) {
        try {
            TranscriptionServiceInterface.get().deleteModel(requireContext(), model.getId());
            if (model.getEngine() == TranscriptionModel.Engine.SPEAKER_DIARIZATION) {
                TranscriptionServiceInterface.get().setSpeakerLabelsEnabled(requireContext(), false);
            }
            refresh();
        } catch (IOException e) {
            Toast.makeText(requireContext(), R.string.transcription_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showAiSettings() {
        AiSettings settings = TranscriptionServiceInterface.get().getAiSettings(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_transcription_ai, null);
        EditText baseUrl = view.findViewById(R.id.ai_base_url);
        EditText model = view.findViewById(R.id.ai_model);
        EditText language = view.findViewById(R.id.ai_translation_language);
        EditText apiKey = view.findViewById(R.id.ai_api_key);
        baseUrl.setText(settings.getBaseUrl());
        model.setText(settings.getModel());
        language.setText(settings.getTranslationLanguage());
        if (settings.isApiKeyStored()) {
            apiKey.setHint(R.string.transcription_ai_api_key_saved);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transcription_ai)
                .setView(view)
                .setPositiveButton(R.string.transcription_ai_save, (dialog, which) -> {
                    try {
                        TranscriptionServiceInterface.get().saveAiSettings(requireContext(),
                                baseUrl.getText().toString(), model.getText().toString(),
                                language.getText().toString(), apiKey.getText().toString());
                        refresh();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(requireContext(), R.string.transcription_ai_invalid_settings,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private int modelIcon(boolean installed, boolean selected) {
        if (installed && selected) {
            return R.drawable.circle_checked;
        }
        return installed ? R.drawable.ic_check : R.drawable.ic_download;
    }

    private String modelTitle(TranscriptionModel model) {
        int resource;
        switch (model.getId()) {
            case "whisper-tiny-int8":
                resource = R.string.transcription_model_whisper_tiny;
                break;
            case "whisper-base-int8":
                resource = R.string.transcription_model_whisper_base;
                break;
            case "sense-voice-int8":
                resource = R.string.transcription_model_sense_voice;
                break;
            case "qwen3-asr-0.6b-int8":
                resource = R.string.transcription_model_qwen;
                break;
            case "speaker-diarization-int8":
                resource = R.string.transcription_model_speaker_labels;
                break;
            default:
                throw new IllegalArgumentException("Unknown transcription model");
        }
        return getString(resource);
    }

    private String modelDescription(TranscriptionModel model) {
        int resource;
        switch (model.getId()) {
            case "whisper-tiny-int8":
                resource = R.string.transcription_model_whisper_tiny_description;
                break;
            case "whisper-base-int8":
                resource = R.string.transcription_model_whisper_base_description;
                break;
            case "sense-voice-int8":
                resource = R.string.transcription_model_sense_voice_description;
                break;
            case "qwen3-asr-0.6b-int8":
                resource = R.string.transcription_model_qwen_description;
                break;
            case "speaker-diarization-int8":
                resource = R.string.transcription_model_speaker_labels_description;
                break;
            default:
                throw new IllegalArgumentException("Unknown transcription model");
        }
        return getString(resource);
    }
}
