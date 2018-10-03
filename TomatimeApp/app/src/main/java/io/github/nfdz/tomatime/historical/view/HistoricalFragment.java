package io.github.nfdz.tomatime.historical.view;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.nfdz.tomatime.R;
import io.github.nfdz.tomatime.common.dialog.PomodoroInfoDialog;
import io.github.nfdz.tomatime.common.utils.SnackbarUtils;
import io.github.nfdz.tomatime.historical.HistoricalContract;
import io.github.nfdz.tomatime.historical.model.PomodoroHistoricalEntry;
import io.github.nfdz.tomatime.historical.presenter.HistoricalPresenter;
import io.github.nfdz.tomatime.main.view.MainActivity;

public class HistoricalFragment extends Fragment implements HistoricalContract.View,
        CategoriesAdapter.Callback, PomodorosAdapter.Callback, PomodoroInfoDialog.UpdateInfoCallback {

    public static HistoricalFragment newInstance() {
        return new HistoricalFragment();
    }

    @BindView(R.id.historical_rv_categories) RecyclerView historical_rv_categories;
    @BindView(R.id.historical_rv_pomodoros) RecyclerView historical_rv_pomodoros;
    @BindView(R.id.historical_tv_no_categories) TextView historical_tv_no_categories;
    @BindView(R.id.historical_tv_no_records) TextView historical_tv_no_records;

    private HistoricalContract.Presenter presenter;
    private CategoriesAdapter categoriesAdapter;
    private PomodorosAdapter pomodorosAdapter;
    private PomodoroHistoricalEntry dialogEntry;

    public HistoricalFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historical, container, false);
        ButterKnife.bind(this, view);
        setupView();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        presenter = new HistoricalPresenter(this);
        presenter.create();
    }

    @Override
    public void onDestroyView() {
        presenter.destroy();
        super.onDestroyView();
    }

    private void setupView() {
        setupCategories();
        setupPomodoros();
    }

    private void setupCategories() {
        categoriesAdapter = new CategoriesAdapter(getActivity(), this);
        historical_rv_categories.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
        historical_rv_categories.setAdapter(categoriesAdapter);
    }

    private void setupPomodoros() {
        pomodorosAdapter = new PomodorosAdapter(getActivity(), this);
        historical_rv_pomodoros.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        historical_rv_pomodoros.setAdapter(pomodorosAdapter);
    }

    @Override
    public void showData(List<PomodoroHistoricalEntry> data) {
        historical_tv_no_records.setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
        pomodorosAdapter.setData(data);
    }

    @Override
    public void showCategories(Set<String> categories) {
        historical_tv_no_categories.setVisibility(categories == null || categories.isEmpty() ? View.VISIBLE : View.GONE);
        categoriesAdapter.setCategories(categories);
    }

    @Override
    public void setSelectedCategories(Set<String> selectedCategories) {
        categoriesAdapter.setSelectedCategories(selectedCategories);
    }

    @Override
    public void showPomodoroInfoDialog(PomodoroHistoricalEntry entry) {
        this.dialogEntry = entry;
        PomodoroInfoDialog dialog = TextUtils.isEmpty(entry.infoKey) ?
                PomodoroInfoDialog.newInstance("", "", "")
                : PomodoroInfoDialog.newInstance(entry.title, entry.notes, entry.category);
        dialog.setCallback(this);
        dialog.show(getFragmentManager(), "pomodoro_info_dialog");
    }

    @Override
    public void showSaveInfoError() {
        SnackbarUtils.show(getView(), R.string.home_save_info_error, Snackbar.LENGTH_LONG);
    }

    @Override
    public void showDeleteInfoError() {
        SnackbarUtils.show(getView(), R.string.home_delete_info_error, Snackbar.LENGTH_LONG);
    }

    @Override
    public void showSaveInfoConflict(final PomodoroHistoricalEntry entry, final String title, final String notes, final String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppAlertDialog);
        builder.setTitle(R.string.info_conflict_dialog_title)
                .setMessage(R.string.info_conflict_dialog_content)
                .setPositiveButton(R.string.info_conflict_dialog_overwrite,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                presenter.overwritePomodoroInfo(entry, title, notes, category);
                                dialog.dismiss();
                            }
                        }
                )
                .setNeutralButton(R.string.info_conflict_dialog_existing,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                presenter.useExistingPomodoroInfo(entry, title, notes, category);
                                dialog.dismiss();
                            }
                        }
                )
                .setNegativeButton(R.string.info_conflict_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                ).show();
    }

    @Override
    public void navigateToPomodoro() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity)getActivity()).navigateToPomodoroTab();
        }
    }

    @Override
    public void onCategoryClick(String category) {
        presenter.onCategoryClick(category);
    }

    @Override
    public void onInfoChange(String title, String notes, String category) {
        if (dialogEntry != null) {
            presenter.savePomodoroInfo(dialogEntry, title, notes, category);
            dialogEntry = null;
        }
    }

    @Override
    public void onPomodoroClick(PomodoroHistoricalEntry entry) {
        presenter.onPomodoroClick(entry);
    }

    @Override
    public void onStartPomodoroClick(PomodoroHistoricalEntry entry) {
        presenter.onStartPomodoroClick(entry);
    }

    @Override
    public void onDeletePomodoroClick(PomodoroHistoricalEntry entry) {
        presenter.onDeletePomodoroClick(entry);
    }

}
