package io.github.nfdz.tomatina.historical.model;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.nfdz.tomatina.R;
import io.github.nfdz.tomatina.TomatinaApp;
import io.github.nfdz.tomatina.common.model.PomodoroInfoRealm;
import io.github.nfdz.tomatina.common.model.PomodoroRealm;
import io.github.nfdz.tomatina.common.utils.RealmUtils;
import io.github.nfdz.tomatina.historical.HistoricalContract;
import io.github.nfdz.tomatina.service.PomodoroService;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import timber.log.Timber;

public class HistoricalInteractor implements HistoricalContract.Interactor, RealmChangeListener<RealmResults<PomodoroRealm>> {

    private DataListener listener;
    private RealmResults<PomodoroRealm> observedData;

    @Override
    public void initialize(DataListener listener) {
        this.listener = listener;
        if (observedData == null) {
            observedData = TomatinaApp.REALM.where(PomodoroRealm.class).findAllAsync();
            observedData.addChangeListener(this);
        }
    }

    @Override
    public void destroy() {
        if (observedData != null) {
            observedData.removeChangeListener(this);
            observedData = null;
        }
    }

    @Override
    public void onChange(@NonNull RealmResults<PomodoroRealm> pomodoroRealms) {
        if (listener != null) {
            Timber.d("There is some changes, reloading historical data...");
            loadDataAsync();
        }
    }

    private void loadDataAsync() {
        final PomodoroInfoRealm noInfo = new PomodoroInfoRealm();
        noInfo.setTitle(TomatinaApp.INSTANCE.getString(R.string.historical_no_info_title));
        final Set<String> categories = new HashSet<>();
        final List<PomodoroHistoricalEntry> data = new ArrayList<>();
        TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                RealmResults<PomodoroRealm> pomodoros = realm.where(PomodoroRealm.class).findAll();
                Map<PomodoroInfoRealm,List<PomodoroRealm>> map = new HashMap<>();
                for (PomodoroRealm pomodoro : pomodoros) {
                    PomodoroInfoRealm info = pomodoro.getPomodoroInfo();
                    if (info == null) {
                        info = noInfo;
                    } else {
                        categories.add(info.getCategory());
                    }
                    List<PomodoroRealm> pomodorosOfInfo = map.get(info);
                    if (pomodorosOfInfo == null) {
                        pomodorosOfInfo = new ArrayList<>();
                        map.put(info, pomodorosOfInfo);
                    }
                    pomodorosOfInfo.add(pomodoro);
                }
                for (Map.Entry<PomodoroInfoRealm,List<PomodoroRealm>> entry : map.entrySet()) {
                    PomodoroInfoRealm info = entry.getKey();
                    data.add(new PomodoroHistoricalEntry(info.getKey(),
                            info.getTitle(),
                            info.getNotes(),
                            info.getCategory(),
                            entry.getValue().size()));
                }
                Collections.sort(data);
                categories.remove("");
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                listener.onNotifyData(categories, data);
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(@NonNull Throwable error) {
                Timber.e(error, "Problem loading pomodoros");
            }
        });
    }

    @Override
    public void startPomodoro(PomodoroHistoricalEntry entry) {
        PomodoroService.stopPomodoro(TomatinaApp.INSTANCE);
        PomodoroService.startPomodoro(TomatinaApp.INSTANCE, entry.infoKey);
    }

    @Override
    public void deletePomodoros(final PomodoroHistoricalEntry entry, final DeleteCallback callback) {
        TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                RealmResults<PomodoroRealm> results = realm.where(PomodoroRealm.class)
                        .equalTo(PomodoroRealm.INFO_FIELD + "." + PomodoroInfoRealm.KEY_FIELD, entry.infoKey)
                        .findAll();
                if (results != null && !results.isEmpty()) {
                    for (PomodoroRealm pomodoro : results) {
                        if (!pomodoro.isOngoing()) {
                            pomodoro.deleteFromRealm();
                        }
                    }
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(@NonNull Throwable error) {
                Timber.e(error, "Problem deleting pomodoros");
                callback.onError();
            }
        });
    }

    @Override
    public void savePomodoroInfo(final PomodoroHistoricalEntry entry,
                                 final String title,
                                 final String notes,
                                 final String category,
                                 final boolean solveConflict,
                                 final boolean overwriteIfNeed,
                                 final SaveInfoCallback callback) {
        TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                RealmResults<PomodoroRealm> pomodoros = realm.where(PomodoroRealm.class)
                        .equalTo(PomodoroRealm.INFO_FIELD + "." + PomodoroInfoRealm.KEY_FIELD, entry.infoKey)
                        .findAll();
                for (PomodoroRealm pomodoro : pomodoros) {
                    RealmUtils.savePomodoroInfo(realm, pomodoro.getId(), title, notes, category, solveConflict, overwriteIfNeed);
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(@NonNull Throwable error) {
                if (error instanceof RealmUtils.ConflictException) {
                    callback.onConflict();
                } else {
                    Timber.e(error, "Problem saving pomodoro info");
                    callback.onError();
                }
            }
        });
    }

}
