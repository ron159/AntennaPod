package de.danoeh.antennapod.ui.screen.playback;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.TranscriptDialogBinding;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.TranscriptionEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.transcript.TranscriptUtils;
import de.danoeh.antennapod.transcription.serviceinterface.TranscriptionServiceInterface;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TranscriptDialogFragment extends DialogFragment
        implements TranscriptAdapter.SegmentClickListener {
    public static final String TAG = "TranscriptFragment";
    private TranscriptDialogBinding viewBinding;
    private Disposable disposable;
    private Playable media;
    private Transcript transcript;
    private TranscriptAdapter adapter = null;
    private boolean doInitialScroll = true;
    private LinearLayoutManager layoutManager;

    @Override
    public void onResume() {
        ViewGroup.LayoutParams params;
        params = getDialog().getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setAttributes((WindowManager.LayoutParams) params);
        super.onResume();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewBinding = TranscriptDialogBinding.inflate(getLayoutInflater());
        layoutManager = new LinearLayoutManager(getContext());
        viewBinding.transcriptList.setLayoutManager(layoutManager);

        adapter = new TranscriptAdapter(getContext(), this);
        viewBinding.transcriptList.setAdapter(adapter);
        viewBinding.transcriptList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    viewBinding.followAudioCheckbox.setChecked(false);
                }
            }
        });

        viewBinding.toolbar.inflateMenu(R.menu.transcript);
        viewBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        viewBinding.followAudioCheckbox.setChecked(true);
        viewBinding.progLoading.setVisibility(View.VISIBLE);
        doInitialScroll = true;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(viewBinding.getRoot())
                .setNegativeButton(R.string.close_label, null)
                .create();
        setMultiselectMode(false);
        return dialog;
    }

    private void setMultiselectMode(boolean multiselectMode) {
        adapter.setMultiselectMode(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_copy).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_cancel_copy).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_select_all).setVisible(multiselectMode);
        viewBinding.toolbar.getMenu().findItem(R.id.action_refresh).setVisible(!multiselectMode);
        viewBinding.followAudioCheckbox.setChecked(!multiselectMode);
        updateActionVisibility(multiselectMode);
    }

    private void copySelectedText() {
        String selectedText = adapter.getSelectedText();
        ClipboardManager clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.transcript), selectedText));
        }
        if (Build.VERSION.SDK_INT <= 32) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.copied_to_clipboard)));
        }
    }

    @Override
    public void onTranscriptClicked(int pos, TranscriptSegment segment) {
        if (adapter.isMultiselectMode()) {
            adapter.toggleSelection(pos);
        } else {
            long startTime = segment.getStartTime();
            long endTime = segment.getEndTime();

            scrollToPosition(pos);
            PlaybackController.bindToMedia3Service(getActivity(), controller -> {
                if (!(controller.getCurrentPosition() >= startTime
                        && controller.getCurrentPosition() <= endTime)) {
                    controller.seekTo(startTime);
                } else if (controller.isPlaying()) {
                    controller.pause();
                } else {
                    controller.play();
                }
            });
            adapter.notifyItemChanged(pos);
            viewBinding.followAudioCheckbox.setChecked(true);
        }
    }

    @Override
    public void onTranscriptLongClicked(int position, TranscriptSegment seg) {
        if (!adapter.isMultiselectMode()) {
            setMultiselectMode(true);
            adapter.toggleSelection(position);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadMediaInfo(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusEvent(PlayerStatusEvent event) {
        loadMediaInfo(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (transcript == null) {
            return;
        }
        int pos = transcript.findSegmentIndexBefore(event.getPosition());
        scrollToPosition(pos);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTranscriptionEvent(TranscriptionEvent event) {
        if (!(media instanceof FeedMedia) || event.getMediaId() != ((FeedMedia) media).getId()) {
            return;
        }
        if (event.getType() == TranscriptionEvent.Type.TRANSCRIPT_READY) {
            loadMediaInfo(false);
        } else if (event.getType() == TranscriptionEvent.Type.FAILED) {
            viewBinding.progLoading.setVisibility(View.GONE);
        } else if (event.getType() == TranscriptionEvent.Type.AI_READY) {
            showAiResult(TranscriptionServiceInterface.AiAction.valueOf(event.getValue()));
        }
    }

    private void loadMediaInfo(boolean forceRefresh) {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.create(emitter -> {
            Playable media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            if (media instanceof FeedMedia) {
                this.media = media;

                transcript = TranscriptUtils.loadTranscript(requireContext(), (FeedMedia) this.media, forceRefresh);
                doInitialScroll = true;
                ((FeedMedia) this.media).setTranscript(transcript);
                emitter.onSuccess(this.media);
            } else {
                emitter.onComplete();
            }
        })
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(media -> onMediaChanged((Playable) media),
                error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void onMediaChanged(Playable media) {
        if (!(media instanceof FeedMedia)) {
            return;
        }
        this.media = media;

        if (!((FeedMedia) media).hasTranscript()) {
            viewBinding.progLoading.setVisibility(View.GONE);
            viewBinding.emptyTranscript.setVisibility(View.VISIBLE);
            viewBinding.followAudioCheckbox.setVisibility(View.GONE);
            updateActionVisibility(false);
            if (!((FeedMedia) media).localFileAvailable()) {
                dismiss();
                Toast.makeText(getContext(), R.string.no_transcript_label, Toast.LENGTH_LONG).show();
            }
            return;
        }

        viewBinding.progLoading.setVisibility(View.GONE);
        viewBinding.emptyTranscript.setVisibility(View.GONE);
        viewBinding.followAudioCheckbox.setVisibility(View.VISIBLE);
        adapter.setMedia(media);
        updateActionVisibility(false);
    }

    public void scrollToPosition(int pos) {
        if (pos <= 0) {
            return;
        }
        if (!viewBinding.followAudioCheckbox.isChecked() && !doInitialScroll) {
            return;
        }
        doInitialScroll = false;

        boolean quickScroll = Math.abs(layoutManager.findFirstVisibleItemPosition() - pos) > 5;
        if (layoutManager.findFirstVisibleItemPosition() < pos - 1
                && !viewBinding.transcriptList.canScrollVertically(1)) {
            return;
        }
        if (quickScroll) {
            viewBinding.transcriptList.scrollToPosition(pos - 1);
            // Additionally, smooth scroll, so that currently active segment is on top of screen
        }
        LinearSmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return (quickScroll ? 200 : 1000) / (float) displayMetrics.densityDpi;
            }
        };
        smoothScroller.setTargetPosition(pos - 1);
        layoutManager.startSmoothScroll(smoothScroller);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
        EventBus.getDefault().unregister(this);
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            viewBinding.progLoading.setVisibility(View.VISIBLE);
            loadMediaInfo(true);
            return true;
        } else if (id == R.id.action_transcribe) {
            requestTranscription();
            return true;
        } else if (id == R.id.action_ai_summary) {
            requestAi(TranscriptionServiceInterface.AiAction.SUMMARY);
            return true;
        } else if (id == R.id.action_ai_translate) {
            requestAi(TranscriptionServiceInterface.AiAction.TRANSLATION);
            return true;
        } else if (id == R.id.action_ai_show_summary) {
            showAiResult(TranscriptionServiceInterface.AiAction.SUMMARY);
            return true;
        } else if (id == R.id.action_ai_show_translation) {
            showAiResult(TranscriptionServiceInterface.AiAction.TRANSLATION);
            return true;
        } else if (id == R.id.action_copy) {
            copySelectedText();
            setMultiselectMode(false);
            return true;
        } else if (id == R.id.action_cancel_copy) {
            setMultiselectMode(false);
            return true;
        } else if (id == R.id.action_select_all) {
            adapter.selectAll();
            return true;
        }
        return false;
    }

    private void requestTranscription() {
        if (!(media instanceof FeedMedia)) {
            return;
        }
        FeedMedia feedMedia = (FeedMedia) media;
        try {
            boolean replace = TranscriptionServiceInterface.get().hasGeneratedTranscript(requireContext(), feedMedia);
            TranscriptionServiceInterface.get().transcribe(requireContext(), feedMedia.getId(), replace);
            viewBinding.progLoading.setVisibility(View.VISIBLE);
        } catch (IllegalStateException e) {
            Toast.makeText(requireContext(), R.string.transcription_model_required, Toast.LENGTH_LONG).show();
        }
    }

    private void requestAi(TranscriptionServiceInterface.AiAction action) {
        if (!(media instanceof FeedMedia)) {
            return;
        }
        try {
            TranscriptionServiceInterface.get().requestAi(requireContext(), ((FeedMedia) media).getId(), action);
        } catch (IllegalStateException e) {
            Toast.makeText(requireContext(), R.string.transcription_ai_api_key_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void showAiResult(TranscriptionServiceInterface.AiAction action) {
        if (!(media instanceof FeedMedia)) {
            return;
        }
        File file = TranscriptionServiceInterface.get().getAiResultFile(
                requireContext(), (FeedMedia) media, action);
        try {
            if (!file.isFile()) {
                return;
            }
            String result = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(action == TranscriptionServiceInterface.AiAction.SUMMARY
                            ? R.string.transcription_ai_show_summary : R.string.transcription_ai_show_translation)
                    .setMessage(result)
                    .setPositiveButton(R.string.close_label, null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), R.string.transcription_ai_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void updateActionVisibility(boolean multiselectMode) {
        boolean localMedia = media instanceof FeedMedia && ((FeedMedia) media).localFileAvailable();
        boolean hasTranscript = transcript != null;
        TranscriptionServiceInterface service = TranscriptionServiceInterface.get();
        boolean hasAiKey = service != null && service.getAiSettings(requireContext()).isApiKeyStored();
        viewBinding.toolbar.getMenu().findItem(R.id.action_transcribe)
                .setVisible(!multiselectMode && localMedia && service != null);
        viewBinding.toolbar.getMenu().findItem(R.id.action_ai_summary)
                .setVisible(!multiselectMode && hasTranscript && hasAiKey);
        viewBinding.toolbar.getMenu().findItem(R.id.action_ai_translate)
                .setVisible(!multiselectMode && hasTranscript && hasAiKey);
        boolean summary = media instanceof FeedMedia && service != null && service
                .getAiResultFile(requireContext(), (FeedMedia) media,
                        TranscriptionServiceInterface.AiAction.SUMMARY).isFile();
        boolean translation = media instanceof FeedMedia && service != null && service
                .getAiResultFile(requireContext(), (FeedMedia) media,
                        TranscriptionServiceInterface.AiAction.TRANSLATION).isFile();
        viewBinding.toolbar.getMenu().findItem(R.id.action_ai_show_summary)
                .setVisible(!multiselectMode && summary);
        viewBinding.toolbar.getMenu().findItem(R.id.action_ai_show_translation)
                .setVisible(!multiselectMode && translation);
    }
}
