package com.pessdes.lyrics.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.BaseFragment;
public class LyricsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final int[] notificationIds = new int[] {
            NotificationCenter.messagePlayingDidReset,
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingDidSeek,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingSpeedChanged,
            NotificationCenter.messagePlayingProgressDidChanged,
            NotificationCenter.messagePlayingGoingToStop
    };

    private void configureNotifications(boolean enable) {
        for (int id : notificationIds) {
            if (enable) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, id);
            }
            else {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, id);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        configureNotifications(false);
        super.onFragmentDestroy();
    }

    @Override
    public boolean onFragmentCreate() {
        configureNotifications(true);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        return super.createView(context);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {

        }
    }
}
