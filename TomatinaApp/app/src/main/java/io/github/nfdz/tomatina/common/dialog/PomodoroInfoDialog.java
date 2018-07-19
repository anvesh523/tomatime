package io.github.nfdz.tomatina.common.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import io.github.nfdz.tomatina.R;
import io.github.nfdz.tomatina.common.model.PomodoroInfoRealm;

public class PomodoroInfoDialog extends DialogFragment {

    public interface UpdateInfoCallback {
        void onUpdateInfo(String title, String notes, String category);
    }

    public static PomodoroInfoDialog newInstance(@Nullable PomodoroInfoRealm info) {
        PomodoroInfoDialog fragment = new PomodoroInfoDialog();
        if (info != null) {
            Bundle args = new Bundle();
            args.putString(TITLE_EXTRA, info.getTitle());
            args.putString(NOTES_EXTRA, info.getNotes());
            args.putString(CATEGORY_EXTRA, info.getCategory());
            fragment.setArguments(args);
        }
        return fragment;
    }

    private static final String TITLE_EXTRA = "title";
    private static final String NOTES_EXTRA = "notes";
    private static final String CATEGORY_EXTRA = "category";

    private UpdateInfoCallback callback;

    private String initialTitle = "";
    private String initialNotes = "";
    private String initialCategory = "";

    public PomodoroInfoDialog() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            initialTitle = args.getString(TITLE_EXTRA, "");
            initialNotes = args.getString(NOTES_EXTRA, "");
            initialCategory = args.getString(CATEGORY_EXTRA, "");
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppAlertDialog);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_pomodoro_info, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.setTitle(R.string.info_dialog_title)
                .setPositiveButton(R.string.info_dialog_save,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (callback != null) {
                                    //callback.onUpdateInfo();
                                }
                                dialog.dismiss();
                            }
                        }
                )
                .setNegativeButton(R.string.info_dialog_close,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                ).create();
        return dialog;
    }

    public void setCallback(UpdateInfoCallback callback) {
        this.callback = callback;
    }

}
