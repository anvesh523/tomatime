package io.github.nfdz.tomatina.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import io.github.nfdz.tomatina.R;
import io.github.nfdz.tomatina.TomatinaApp;
import io.github.nfdz.tomatina.common.model.PomodoroInfoRealm;
import io.github.nfdz.tomatina.common.model.PomodoroRealm;
import io.github.nfdz.tomatina.common.model.PomodoroState;
import io.github.nfdz.tomatina.common.utils.LifecycleUtils;
import io.github.nfdz.tomatina.common.utils.OverlayPermissionHelper;
import io.github.nfdz.tomatina.common.utils.SettingsPreferencesUtils;
import io.github.nfdz.tomatina.main.view.MainActivity;
import io.realm.Realm;
import timber.log.Timber;

public class PomodoroService extends Service {

    public static void startPomodoro(Context context) {
        Intent starter = new Intent(context, PomodoroService.class);
        starter.setAction(START_POMODORO_ACTION);
        context.startService(starter);
    }

    public static void startPomodoro(Context context, String infoKey) {
        Intent starter = new Intent(context, PomodoroService.class);
        starter.putExtra(INFO_KEY_EXTRA, infoKey);
        starter.setAction(START_POMODORO_ACTION);
        context.startService(starter);
    }

    public static void stopPomodoro(Context context) {
        Intent starter = new Intent(context, PomodoroService.class);
        starter.setAction(STOP_POMODORO_ACTION);
        context.startService(starter);
    }

    public static void skipStage(Context context) {
        Intent starter = new Intent(context, PomodoroService.class);
        starter.setAction(SKIP_STAGE_ACTION);
        context.startService(starter);
    }

    public static final String CHANNEL_ID = "tomatina_channel";
    public static final int NOTIFICATION_ID = 5341;

    public static final String START_POMODORO_ACTION = "start_pomodoro";
    public static final String STOP_POMODORO_ACTION = "stop_pomodoro";
    public static final String SKIP_STAGE_ACTION = "skip_stage";
    public static final String CONTINUE_POMODORO_ACTION = "continue_pomodoro";

    public static final String INFO_KEY_EXTRA = "info_key";

    private static final long WATCHER_RATE_MILLIS = 500;

    private OverlayHandler overlayHandler;
    private Handler handler;
    private boolean destroyed;

    // Pomodoro state
    private long pomodoroId;
    private long stateStartTime;
    private @PomodoroState int pomodoroState;
    private long pomodoroTimeInMillis;
    private long shortBreakTimeInMillis;
    private long longBreakTimeInMillis;
    private int pomodorosToLongBreak;
    private int pomodoroCounter;
    private boolean waitingContinue;
    private String infoKey;

    public PomodoroService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        handler = new Handler();
        overlayHandler = new OverlayHandler(this);
        resetState();
        handler.postDelayed(new WatcherTask(), WATCHER_RATE_MILLIS);
        Timber.d("Pomodoro service created");
    }

    @Override
    public void onDestroy() {
        Timber.d("Pomodoro service destroyed");
        destroyed = true;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            action = action == null ? "" : action;
            switch (action) {
                case START_POMODORO_ACTION:
                    Timber.d("Start command received");
                    infoKey = intent.getStringExtra(INFO_KEY_EXTRA);
                    handleStopPomodoro();
                    resetState();
                    handleStartPomodoro();
                    break;
                case STOP_POMODORO_ACTION:
                    Timber.d("Stop command received");
                    handleStopPomodoro();
                    stopForegroundService();
                    break;
                case CONTINUE_POMODORO_ACTION:
                    Timber.d("Continue command received");
                    handleContinuePomodoro();
                    break;
                case SKIP_STAGE_ACTION:
                    Timber.d("Skip command received");
                    handleSkipStage();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void resetState() {
        pomodoroId = 0;
        stateStartTime = 0;
        pomodoroState = PomodoroState.NONE;
        pomodoroTimeInMillis = 0;
        shortBreakTimeInMillis = 0;
        longBreakTimeInMillis = 0;
        pomodorosToLongBreak = 0;
        pomodoroCounter = 0;
        waitingContinue = false;
    }

    private void handleStartPomodoro() {
        if (pomodoroId <= 0) {
            final long id = System.currentTimeMillis();
            final int state = PomodoroState.WORKING;
            final long startTime = System.currentTimeMillis();
            final long pomodoroTimeInMillis = SettingsPreferencesUtils.getPomodoroTimeInMillis();
            final long shortBreakTimeInMillis = SettingsPreferencesUtils.getShortBreakTimeInMillis();
            final long longBreakTimeInMillis = SettingsPreferencesUtils.getLongBreakTimeInMillis();
            final int pomodorosToLongBreak = SettingsPreferencesUtils.getPomodorosToLongBreak();
            TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    PomodoroRealm pomodoro = realm.createObject(PomodoroRealm.class, id);
                    pomodoro.setStartTimeMillis(startTime);
                    pomodoro.setState(state);
                    pomodoro.setPomodoroTimeInMillis(pomodoroTimeInMillis);
                    pomodoro.setShortBreakTimeInMillis(shortBreakTimeInMillis);
                    pomodoro.setLongBreakTimeInMillis(longBreakTimeInMillis);
                    pomodoro.setPomodorosToLongBreak(pomodorosToLongBreak);

                    if (infoKey != null) {
                        PomodoroInfoRealm info = realm.where(PomodoroInfoRealm.class).equalTo(PomodoroInfoRealm.KEY_FIELD, infoKey).findFirst();
                        if (info != null) {
                            pomodoro.setPomodoroInfo(info);
                        }
                    }
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    Timber.d("Pomodoro started successfully");
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(@NonNull Throwable error) {
                    Timber.e(error, "There was an error starting pomodoro");
                }
            });
            this.pomodoroId = id;
            this.stateStartTime = startTime;
            this.pomodoroState = state;
            this.pomodoroTimeInMillis = pomodoroTimeInMillis;
            this.shortBreakTimeInMillis = shortBreakTimeInMillis;
            this.longBreakTimeInMillis = longBreakTimeInMillis;
            this.pomodorosToLongBreak = pomodorosToLongBreak;

            createNotificationChannel();
            startForeground(NOTIFICATION_ID,
                    createNotification(getString(R.string.notif_working_title),
                            getString(R.string.notif_working_text),
                            false,
                            true,
                            false,
                            true));
        }
    }

    private void handleStopPomodoro() {
        if (pomodoroId > 0) {
            final long id = pomodoroId;
            final long finishTime = System.currentTimeMillis();
            TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    PomodoroRealm pomodoro = realm.where(PomodoroRealm.class).equalTo(PomodoroRealm.ID_FIELD, id).findFirst();
                    pomodoro.setStartTimeMillis(finishTime);
                    pomodoro.setState(PomodoroState.FINISHED);
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    Timber.d("Pomodoro finished successfully");
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(@NonNull Throwable error) {
                    Timber.e(error, "There was an error finishing pomodoro");
                }
            });
        }
    }

    private void handleContinuePomodoro() {
        if (pomodoroId > 0 && waitingContinue) {
            waitingContinue = false;
            final int nextState;
            String notifTitle;
            String notifText;
            if (pomodoroState == PomodoroState.WORKING) {
                pomodoroCounter++;
                if (pomodoroCounter >= pomodorosToLongBreak) {
                    nextState = PomodoroState.LONG_BREAK;
                    notifTitle = getString(R.string.notif_long_break_title);
                    notifText = getString(R.string.notif_long_break_text);
                } else {
                    nextState = PomodoroState.SHORT_BREAK;
                    notifTitle = getString(R.string.notif_short_break_title);
                    notifText = getString(R.string.notif_short_break_text);
                }
            } else {
                nextState = PomodoroState.WORKING;
                notifTitle = getString(R.string.notif_working_title);
                notifText = getString(R.string.notif_working_text);
            }
            pomodoroState = nextState;
            final long now = System.currentTimeMillis();
            stateStartTime = now;
            final long id = pomodoroId;
            final int counter = pomodoroCounter;
            TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    PomodoroRealm pomodoro = realm.where(PomodoroRealm.class).equalTo(PomodoroRealm.ID_FIELD, id).findFirst();
                    pomodoro.setStartTimeMillis(now);
                    pomodoro.setState(nextState);
                    pomodoro.setCounter(counter);
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    Timber.d("Pomodoro continued successfully");
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(@NonNull Throwable error) {
                    Timber.e(error, "There was an error continuing pomodoro");
                }
            });
            startForeground(NOTIFICATION_ID,
                    createNotification(notifTitle,
                            notifText,
                            false,
                            true,
                            false,
                            true));
        }
    }

    private void handleSkipStage() {
        if (pomodoroId > 0) {
            final int nextState;
            String notifTitle;
            String notifText;
            if (pomodoroState == PomodoroState.WORKING) {
                pomodoroCounter++;
                if (pomodoroCounter >= pomodorosToLongBreak) {
                    nextState = PomodoroState.LONG_BREAK;
                    notifTitle = getString(R.string.notif_long_break_title);
                    notifText = getString(R.string.notif_long_break_text);
                } else {
                    nextState = PomodoroState.SHORT_BREAK;
                    notifTitle = getString(R.string.notif_short_break_title);
                    notifText = getString(R.string.notif_short_break_text);
                }
            } else if (pomodoroState == PomodoroState.SHORT_BREAK){
                nextState = PomodoroState.WORKING;
                notifTitle = getString(R.string.notif_working_title);
                notifText = getString(R.string.notif_working_text);
            } else {
                nextState = PomodoroState.FINISHED;
                notifTitle = getString(R.string.notif_restart_title);
                notifText = getString(R.string.notif_restart_text);
            }
            pomodoroState = nextState;
            final long now = System.currentTimeMillis();
            stateStartTime = now;
            final long id = pomodoroId;
            final int counter = pomodoroCounter;
            TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    PomodoroRealm pomodoro = realm.where(PomodoroRealm.class).equalTo(PomodoroRealm.ID_FIELD, id).findFirst();
                    pomodoro.setStartTimeMillis(now);
                    pomodoro.setState(nextState);
                    pomodoro.setCounter(counter);
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    Timber.d("Pomodoro skipped successfully");
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(@NonNull Throwable error) {
                    Timber.e(error, "There was an error skipping pomodoro");
                }
            });

            if (nextState == PomodoroState.FINISHED) {
                resetState();
                startForeground(NOTIFICATION_ID,
                        createNotification(notifTitle,
                                notifText,
                                true,
                                false,
                                false,
                                true));
            } else {
                startForeground(NOTIFICATION_ID,
                        createNotification(notifTitle,
                                notifText,
                                false,
                                true,
                                false,
                                true));
            }
        }
    }

    private Notification createNotification(String title, String text, boolean showStart, boolean showSkip, boolean showContinue, boolean showStop) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(R.mipmap.ic_launcher);

        // Force heads up notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPriority(NotificationManager.IMPORTANCE_HIGH);
        } else {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        builder.setVibrate(new long[0]);

        // Main activity intent
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, 0);
        builder.setContentIntent(mainPendingIntent);

        if (showStart) {
            Intent starter = new Intent(this, PomodoroService.class);
            starter.setAction(START_POMODORO_ACTION);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, starter, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_notif_play_light, getString(R.string.notif_action_start_pomodoro), pendingIntent);
            builder.addAction(action);
        }

        if (showSkip) {
            Intent starter = new Intent(this, PomodoroService.class);
            starter.setAction(SKIP_STAGE_ACTION);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, starter, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_notif_skip_light, getString(R.string.notif_action_skip_stage), pendingIntent);
            builder.addAction(action);
        }

        if (showContinue) {
            Intent starter = new Intent(this, PomodoroService.class);
            starter.setAction(CONTINUE_POMODORO_ACTION);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, starter, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_notif_check_light, getString(R.string.notif_action_continue_pomodoro), pendingIntent);
            builder.addAction(action);
        }

        if (showStop) {
            Intent starter = new Intent(this, PomodoroService.class);
            starter.setAction(STOP_POMODORO_ACTION);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, starter, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_notif_stop_light, getString(R.string.notif_action_stop_pomodoro), pendingIntent);
            builder.addAction(action);
        }

        return builder.build();
    }

    private void stopForegroundService() {
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setVibrationPattern(null);
            channel.setSound(null, null);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private class WatcherTask implements Runnable {
        @Override
        public void run() {
            if (!destroyed) {
                try {
                    boolean overlayEnabled = false;

                    // Process pomodoro
                    if (pomodoroId > 0 && !waitingContinue) {
                        long now = System.currentTimeMillis();
                        switch (pomodoroState) {
                            case PomodoroState.WORKING:
                                if (now > (stateStartTime + pomodoroTimeInMillis)) {
                                    waitingContinue = true;
                                    if (pomodoroCounter + 1 < pomodorosToLongBreak) {
                                        triggerGoToShortBreakNotification();
                                    } else {
                                        triggerGoToLongBreakNotification();
                                    }
                                }
                                overlayEnabled = true;
                                break;
                            case PomodoroState.SHORT_BREAK:
                                if (now > (stateStartTime + shortBreakTimeInMillis)) {
                                    waitingContinue = true;
                                    triggerGoToWorkNotification();
                                }
                                break;
                            case PomodoroState.LONG_BREAK:
                                if (now > (stateStartTime + longBreakTimeInMillis)) {
                                    waitingContinue = true;
                                    handlePomodoroFinish();
                                }
                                break;
                            case PomodoroState.FINISHED:
                            case PomodoroState.NONE:
                            default:
                        }
                    }

                    // Process overlay view
                    if (overlayEnabled &&
                            OverlayPermissionHelper.hasOverlayPermission(PomodoroService.this) &&
                            SettingsPreferencesUtils.getOverlayViewFlag() &&
                            !LifecycleUtils.isAppInForeground(PomodoroService.this)) {
                        overlayHandler.show();
                    } else {
                        overlayHandler.hide();
                    }
                } catch (Exception e) {
                    Timber.e(e, "There was an error processing watcher");
                } finally {
                    handler.postDelayed(this, WATCHER_RATE_MILLIS);
                }
            }
        }
    }

    private void triggerGoToWorkNotification() {
        startForeground(NOTIFICATION_ID,
                createNotification(getString(R.string.notif_back_to_work_title),
                getString(R.string.notif_back_to_work_text),
                        false,
                        false,
                        true,
                        true));
        triggerWaitingContinueBroadcast();
    }

    private void triggerGoToShortBreakNotification() {
        startForeground(NOTIFICATION_ID,
                createNotification(getString(R.string.notif_take_short_break_title),
                        getString(R.string.notif_take_short_break_text),
                        false,
                        false,
                        true,
                        true));
        triggerWaitingContinueBroadcast();
    }

    private void triggerGoToLongBreakNotification() {
        startForeground(NOTIFICATION_ID,
                createNotification(getString(R.string.notif_take_long_break_title),
                        getString(R.string.notif_take_long_break_text),
                        false,
                        false,
                        true,
                        true));
        triggerWaitingContinueBroadcast();
    }

    private void handlePomodoroFinish() {
        final long id = pomodoroId;
        TomatinaApp.REALM.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                PomodoroRealm pomodoro = realm.where(PomodoroRealm.class).equalTo(PomodoroRealm.ID_FIELD, id).findFirst();
                pomodoro.setStartTimeMillis(System.currentTimeMillis());
                pomodoro.setState(PomodoroState.FINISHED);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                Timber.d("Pomodoro finished successfully");
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(@NonNull Throwable error) {
                Timber.e(error, "There was an error finishing pomodoro");
            }
        });
        resetState();
        startForeground(NOTIFICATION_ID,
                createNotification(getString(R.string.notif_restart_title),
                        getString(R.string.notif_restart_text),
                        true,
                        false,
                        false,
                        true));
    }

    private void triggerWaitingContinueBroadcast() {
        try {
            Intent intent = new Intent(CONTINUE_POMODORO_ACTION);
            sendBroadcast(intent);
        } catch (Exception e) {
            Timber.e(e, "Cannot send waiting continue broadcast");
        }
    }

}
