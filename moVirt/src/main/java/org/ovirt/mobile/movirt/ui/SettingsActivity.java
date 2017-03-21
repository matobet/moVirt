package org.ovirt.mobile.movirt.ui;

import android.accounts.Account;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.PreferenceByKey;
import org.ovirt.mobile.movirt.Constants;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.auth.account.EnvironmentStore;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.sync.EventsHandler;
import org.ovirt.mobile.movirt.ui.auth.AuthenticatorActivity_;
import org.ovirt.mobile.movirt.util.ObjectUtils;
import org.ovirt.mobile.movirt.util.message.MessageHelper;
import org.ovirt.mobile.movirt.util.preferences.SettingsKey;
import org.ovirt.mobile.movirt.util.preferences.SharedPreferencesHelper;

import static org.ovirt.mobile.movirt.util.preferences.SettingsKey.PERIODIC_SYNC;

@EActivity
public class SettingsActivity extends BroadcastAwareAppCompatActivity {

    public static final String ACCOUNT_KEY = "ACCOUNT_KEY";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Account account = getIntent().getParcelableExtra(ACCOUNT_KEY);
        setTitle(getString(R.string.title_activity_account_settings, account.name));
        getFragmentManager().beginTransaction().replace(android.R.id.content, SettingsFragment.newInstance(account)).commit();
    }

    @EFragment
    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        public static final String ACCOUNT_NAME_KEY = "ACCOUNT_NAME_KEY";
        private static final int OBJECTS_SAVE_LEVEL_THRESHOLD = 5000;

        @Bean
        EventsHandler eventsHandler;

        @Bean
        ProviderFacade providerFacade;

        @Bean
        EnvironmentStore environmentStore;

        @Bean
        MessageHelper messageHelper;

        @PreferenceByKey(R.string.connection_button_pref_key)
        Preference connectionButton;

        @PreferenceByKey(R.string.poll_events_pref_key)
        Preference pollEvents;

        @PreferenceByKey(R.string.connection_notification_pref_key)
        Preference connectionNotification;

        @PreferenceByKey(R.string.periodic_sync_pref_key)
        Preference periodicSync;

        @PreferenceByKey(R.string.periodic_sync_interval_pref_key)
        Preference periodicSyncInterval;

        @PreferenceByKey(R.string.max_events_polled_pref_key)
        Preference maxEventsPolled;

        @PreferenceByKey(R.string.max_events_stored_pref_key)
        Preference maxEventsStored;

        @PreferenceByKey(R.string.events_search_query_pref_key)
        Preference eventsSearchQuery;

        @PreferenceByKey(R.string.max_vms_polled_pref_key)
        Preference maxVmsPolled;

        @PreferenceByKey(R.string.vms_search_query_pref_key)
        Preference vmsSearchQuery;

        @InstanceState
        Account account;

        SharedPreferencesHelper sharedPreferencesHelper;

        public static SettingsFragment newInstance(Account account) {
            SettingsFragment fragment = new SettingsActivity_.SettingsFragment_();
            Bundle args = new Bundle();
            args.putParcelable(ACCOUNT_NAME_KEY, account);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            account = getArguments().getParcelable(ACCOUNT_NAME_KEY);

            ObjectUtils.requireNotNull(account, "account");

            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName(account.name + Constants.PREFERENCES_NAME_SUFFIX);
            prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
            toggleVisibility();

            addPreferencesFromResource(R.xml.preferences);

            sharedPreferencesHelper = environmentStore.getSharedPreferencesHelper(account);
        }

        public void toggleVisibility() {
            boolean enabled = account != null;
            if (pollEvents != null && vmsSearchQuery != null) { // also the others
                pollEvents.setEnabled(enabled);
                connectionNotification.setEnabled(enabled);
                periodicSync.setEnabled(enabled);
                periodicSyncInterval.setEnabled(enabled && sharedPreferencesHelper.getBooleanPref(PERIODIC_SYNC));
                maxEventsPolled.setEnabled(enabled);
                maxEventsStored.setEnabled(enabled);
                eventsSearchQuery.setEnabled(enabled);
                maxVmsPolled.setEnabled(enabled);
                vmsSearchQuery.setEnabled(enabled);
            }
        }

        @AfterViews
        public void afterViews() {

            connectionButton.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(
                        getActivity().getApplicationContext(),
                        AuthenticatorActivity_.class);
                startActivity(intent);
                return true;
            });

            periodicSyncInterval.setOnPreferenceChangeListener((preference, newValue) -> {
                String errorMessage = "Interval should be not less then 1 minute.";
                int newValueInt;
                try {
                    newValueInt = Integer.parseInt((String) newValue);
                    if (newValueInt < 1) {
                        messageHelper.showToast(errorMessage);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    messageHelper.showToast(e.getMessage());
                    return false;
                }
                return true;
            });

            maxEventsPolled.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int polled = Integer.parseInt((String) newValue);
                    informAboutMaxValues(polled);
                    return checkEvents(polled, sharedPreferencesHelper.getMaxEventsStored());
                } catch (NumberFormatException e) {
                    messageHelper.showToast(e.getMessage());
                    return false;
                }
            });

            maxEventsStored.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int stored = Integer.parseInt((String) newValue);
                    return checkEvents(sharedPreferencesHelper.getMaxEventsPolled(), stored);
                } catch (NumberFormatException e) {
                    messageHelper.showToast(e.getMessage());
                    return false;
                }
            });

            maxVmsPolled.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int value = Integer.parseInt((String) newValue);
                    informAboutMaxValues(value);
                } catch (NumberFormatException e) {
                    messageHelper.showToast(e.getMessage());
                    return false;
                }
                return true;
            });

            periodicSyncInterval.setEnabled(sharedPreferencesHelper.isPeriodicSyncEnabled());

            setSyncIntervalPrefSummary();
            setMaxVmsSummary();
            setMaxEventsPolledSummary();
            setMaxEventsStoredSummary();
            toggleVisibility();
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String prefKey) {
            SettingsKey key = SettingsKey.from(prefKey);
            switch (key) {
                case PERIODIC_SYNC:
                    periodicSyncInterval.setEnabled(account != null && sharedPreferencesHelper.getBooleanPref(key));
                    //TODO
//                    sharedPreferencesHelper.updatePeriodicSync();
                    break;
                case PERIODIC_SYNC_INTERVAL:
                    // TODO
//                    sharedPreferencesHelper.updatePeriodicSync();
                    setSyncIntervalPrefSummary();
                    break;
                case MAX_EVENTS_POLLED:
                    setMaxEventsPolledSummary();
                    break;
                case MAX_EVENTS_STORED:
                    setMaxEventsStoredSummary();
                    eventsHandler.discardTemporaryEvents();
                    eventsHandler.discardOldEvents();
                    break;
                case MAX_VMS:
                    setMaxVmsSummary();
                    break;
            }
        }

        private void setMaxEventsPolledSummary() {
            int maxEvents = sharedPreferencesHelper.getMaxEventsPolled();
            maxEventsPolled.setSummary(getString(
                    R.string.prefs_max_events_polled_summary, maxEvents));
        }

        private void setMaxEventsStoredSummary() {
            int maxEvents = sharedPreferencesHelper.getMaxEventsStored();
            maxEventsStored.setSummary(getString(
                    R.string.prefs_max_events_stored_summary, maxEvents));
        }

        private void setMaxVmsSummary() {
            int maxVms = sharedPreferencesHelper.getMaxVms();
            maxVmsPolled.setSummary(getString(
                    R.string.prefs_max_vms_polled_summary, maxVms));
        }

        private void setSyncIntervalPrefSummary() {
            int interval = sharedPreferencesHelper.getPeriodicSyncInterval();
            periodicSyncInterval.setSummary(getString(
                    R.string.prefs_periodic_sync_interval_summary, interval));
        }

        private class IntegerValidator implements Preference.OnPreferenceChangeListener {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    int value = Integer.parseInt((String) newValue);
                    informAboutMaxValues(value);
                } catch (NumberFormatException e) {
                    messageHelper.showToast(e.getMessage());
                    return false;
                }
                return true;
            }
        }

        private void informAboutMaxValues(int objectsLimit) {
            if (objectsLimit > OBJECTS_SAVE_LEVEL_THRESHOLD) {
                messageHelper.showToast(getString(R.string.objects_save_level_threshold_message));
            }
        }

        private boolean checkEvents(int eventsPolled, int eventsStored) {
            if (eventsStored < eventsPolled) {
                messageHelper.showToast("events polled shouldn't be larger than events stored");
                return false;
            }
            return true;
        }
    }
}
