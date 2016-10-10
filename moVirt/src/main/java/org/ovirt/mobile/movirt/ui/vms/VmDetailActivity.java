package org.ovirt.mobile.movirt.ui.vms;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.App;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringArrayRes;
import org.ovirt.mobile.movirt.MoVirtApp;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.facade.SnapshotFacade;
import org.ovirt.mobile.movirt.facade.VmFacade;
import org.ovirt.mobile.movirt.model.Snapshot;
import org.ovirt.mobile.movirt.model.Vm;
import org.ovirt.mobile.movirt.model.trigger.Trigger;
import org.ovirt.mobile.movirt.provider.OVirtContract;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.rest.ActionTicket;
import org.ovirt.mobile.movirt.rest.OVirtClient;
import org.ovirt.mobile.movirt.ui.Constants;
import org.ovirt.mobile.movirt.ui.FragmentListPagerAdapter;
import org.ovirt.mobile.movirt.ui.HasProgressBar;
import org.ovirt.mobile.movirt.ui.MovirtActivity;
import org.ovirt.mobile.movirt.ui.NewSnapshotListener;
import org.ovirt.mobile.movirt.ui.ProgressBarResponse;
import org.ovirt.mobile.movirt.ui.UpdateMenuItemAware;
import org.ovirt.mobile.movirt.ui.dialogs.ConfirmDialogFragment;
import org.ovirt.mobile.movirt.ui.dialogs.CreateSnapshotDialogFragment;
import org.ovirt.mobile.movirt.ui.dialogs.CreateSnapshotDialogFragment_;
import org.ovirt.mobile.movirt.ui.dialogs.ImportCertificateDialogFragment;
import org.ovirt.mobile.movirt.ui.events.EventsFragment;
import org.ovirt.mobile.movirt.ui.events.EventsFragment_;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity_;

import java.io.File;
import java.util.List;

@EActivity(R.layout.activity_vm_detail)
@OptionsMenu(R.menu.vm)
public class VmDetailActivity extends MovirtActivity implements HasProgressBar,
        UpdateMenuItemAware<Vm>, ConfirmDialogFragment.ConfirmDialogListener,
        NewSnapshotListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = VmDetailActivity.class.getSimpleName();
    private static final int REQUEST_MIGRATE = 0;
    private static final int ACTION_STOP_VM = 0;
    private static final int ACTION_REBOOT_VM = 1;
    private static final int ACTION_STOP_MIGRATE_VM = 2;

    private static final int SNAPSHOTS_LOADER = 1; // 0 in MovirtActivity

    @ViewById
    ViewPager viewPager;
    @ViewById
    PagerTabStrip pagerTabStrip;
    @StringArrayRes(R.array.vm_detail_pager_titles)
    String[] PAGER_TITLES;
    @Bean
    OVirtClient client;
    @Bean
    ProviderFacade provider;
    @ViewById
    ProgressBar progress;
    @Bean
    VmFacade vmFacade;
    @Bean
    SnapshotFacade snapshotFacade;
    @App
    MoVirtApp app;
    @OptionsMenuItem(R.id.action_run)
    MenuItem menuRun;
    @OptionsMenuItem(R.id.action_stop)
    MenuItem menuStop;
    @OptionsMenuItem(R.id.action_reboot)
    MenuItem menuReboot;
    @OptionsMenuItem(R.id.action_start_migration)
    MenuItem menuStartMigration;
    @OptionsMenuItem(R.id.action_cancel_migration)
    MenuItem menuCancelMigration;
    @OptionsMenuItem(R.id.action_console)
    MenuItem menuConsole;
    @OptionsMenuItem(R.id.action_create_snapshot)
    MenuItem menuCreateSnapshot;

    private String vmId = null;
    private Vm currentVm = null;
    private boolean menuCreateSnapshotVisibility = false;

    @AfterViews
    void init() {
        Uri vmUri = getIntent().getData();
        vmId = vmUri.getLastPathSegment();

        initPagers();
        initLoaders();
        setProgressBar(progress);
    }

    private void initPagers() {
        EventsFragment eventList = new EventsFragment_();
        VmDisksFragment diskList = new VmDisksFragment_();
        VmNicsFragment nicList = new VmNicsFragment_();
        VmSnapshotsFragment snapshotList = new VmSnapshotsFragment_();

        eventList.setFilterVmId(vmId);
        diskList.setFilterVmId(vmId);
        diskList.setFilterSnapshotId("");
        nicList.setFilterVmId(vmId);
        nicList.setFilterSnapshotId("");
        snapshotList.setFilterVmId(vmId);
        snapshotList.addOrdering(OVirtContract.Snapshot.SNAPSHOT_STATUS);
        snapshotList.addOrdering(OVirtContract.Snapshot.TYPE);
        snapshotList.addOrdering(OVirtContract.Snapshot.NAME);

        FragmentListPagerAdapter pagerAdapter = new FragmentListPagerAdapter(
                getSupportFragmentManager(), PAGER_TITLES,
                new VmDetailGeneralFragment_(),
                eventList,
                snapshotList,
                diskList,
                nicList);

        viewPager.setAdapter(pagerAdapter);
        pagerTabStrip.setTabIndicatorColorResource(R.color.material_deep_teal_200);
    }

    private void initLoaders() {
        getSupportLoaderManager().initLoader(SNAPSHOTS_LOADER, null, this);
    }

    @Override
    public void restartLoader() {
        super.restartLoader();
        getSupportLoaderManager().restartLoader(SNAPSHOTS_LOADER, null, this);
    }

    @Override
    public void destroyLoader() {
        super.destroyLoader();
        getSupportLoaderManager().destroyLoader(SNAPSHOTS_LOADER);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Loader<Cursor> loader = null;

        if (id == SNAPSHOTS_LOADER) {
            loader = provider.query(Snapshot.class).where(OVirtContract.Snapshot.VM_ID, vmId).asLoader();
        }

        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!data.moveToNext()) {
            Log.e(TAG, "Error loading Host");
            return;
        }

        if (loader.getId() == SNAPSHOTS_LOADER) {
            List<Snapshot> snapshots = snapshotFacade.mapAllFromCursor(data);
            menuCreateSnapshotVisibility = !Snapshot.containsOneOfStatuses(snapshots, Snapshot.SnapshotStatus.LOCKED, Snapshot.SnapshotStatus.IN_PREVIEW);
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // do nothing
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (currentVm != null) {
            Vm.Status status = currentVm.getStatus();
            menuRun.setVisible(Vm.Command.RUN.canExecute(status));
            menuStop.setVisible(Vm.Command.STOP.canExecute(status));
            menuReboot.setVisible(Vm.Command.REBOOT.canExecute(status));
            menuStartMigration.setVisible(Vm.Command.START_MIGRATION.canExecute(status));
            menuCancelMigration.setVisible(Vm.Command.CANCEL_MIGRATION.canExecute(status));
            menuConsole.setVisible(Vm.Command.CONSOLE.canExecute(status));
            menuCreateSnapshot.setVisible(menuCreateSnapshotVisibility);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @OptionsItem(R.id.action_edit_triggers)
    void editTriggers() {
        final Intent intent = new Intent(this, EditTriggersActivity_.class);
        intent.putExtra(EditTriggersActivity.EXTRA_TARGET_ENTITY_ID, vmId);
        intent.putExtra(EditTriggersActivity.EXTRA_TARGET_ENTITY_NAME, vmId);
        intent.putExtra(EditTriggersActivity.EXTRA_SCOPE, Trigger.Scope.ITEM);
        startActivity(intent);
    }


    @OptionsItem(R.id.action_run)
    @Background
    void start() {
        client.startVm(vmId, new SyncVmResponse());
    }

    @OptionsItem(R.id.action_stop)
    void stop() {
        DialogFragment confirmDialog = ConfirmDialogFragment
                .newInstance(ACTION_STOP_VM, getString(R.string.dialog_action_stop_vm));
        confirmDialog.show(getFragmentManager(), "confirmStopVM");
    }

    @OptionsItem(R.id.action_reboot)
    void reboot() {
        DialogFragment confirmDialog = ConfirmDialogFragment
                .newInstance(ACTION_REBOOT_VM, getString(R.string.dialog_action_reboot_vm));
        confirmDialog.show(getFragmentManager(), "confirmRebootVM");
    }

    @OptionsItem(R.id.action_cancel_migration)
    void cancelMigration() {
        DialogFragment confirmDialog = ConfirmDialogFragment
                .newInstance(ACTION_STOP_MIGRATE_VM,
                        getString(R.string.dialog_action_stop_migrate_vm));
        confirmDialog.show(getFragmentManager(), "confirmStopMigrateVM");
    }

    @Override
    public void onDialogResult(int dialogButton, int actionId) {
        if (dialogButton == DialogInterface.BUTTON_POSITIVE) {
            switch (actionId) {
                case ACTION_STOP_VM:
                    doStop();
                    break;
                case ACTION_REBOOT_VM:
                    doReboot();
                    break;
                case ACTION_STOP_MIGRATE_VM:
                    doCancelMigration();
                    break;
            }
        }
    }

    @Background
    void doStop() {
        client.stopVm(vmId, new SyncVmResponse());
    }

    @Background
    void doReboot() {
        client.rebootVm(vmId, new SyncVmResponse());
    }

    @Background
    void doCancelMigration() {
        client.cancelMigration(vmId, new SyncVmResponse());
    }

    @OptionsItem(R.id.action_start_migration)
    void showMigrationDialog() {
        if (currentVm != null) {
            Intent migrateIntent = new Intent(this, VmMigrateActivity_.class);
            migrateIntent.putExtra(VmMigrateActivity.HOST_ID_EXTRA, currentVm.getHostId());
            migrateIntent.putExtra(VmMigrateActivity.CLUSTER_ID_EXTRA, currentVm.getClusterId());
            startActivityForResult(migrateIntent, REQUEST_MIGRATE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MIGRATE) {
            if (resultCode == VmMigrateActivity.RESULT_DEFAULT) {
                doMigrationToDefault();
            }
            if (resultCode == VmMigrateActivity.RESULT_SELECT) {
                doMigrationTo(data.getStringExtra(VmMigrateActivity.RESULT_HOST_ID_EXTRA));
            }
        }
    }

    @Background
    public void doMigrationToDefault() {
        client.migrateVmToDefaultHost(vmId, new SyncVmResponse());
    }

    @Background
    public void doMigrationTo(String hostId) {
        client.migrateVmToHost(vmId, hostId, new SyncVmResponse());
    }

    @OptionsItem(R.id.action_create_snapshot)
    void createSnapshot() {
        CreateSnapshotDialogFragment dialog = new CreateSnapshotDialogFragment_();
        dialog.setVmId(vmId);
        dialog.show(getFragmentManager(), "createSnapshot");
    }

    @Override
    @Background
    public void onDialogResult(org.ovirt.mobile.movirt.rest.Snapshot snapshot) {
        client.createSnapshot(snapshot, vmId, new OVirtClient.SimpleResponse<Void>() {
            @Override
            public void onResponse(Void aVoid) throws RemoteException {
                snapshotFacade.syncAll(vmId);
            }
        });
    }


    @OptionsItem(R.id.action_console)
    @Background
    void openConsole() {
        vmFacade.syncOne(new ProgressBarResponse<Vm>(this) {

            @Override
            public void onResponse(final Vm freshVm) throws RemoteException {

                client.getConsoleTicket(vmId, new ProgressBarResponse<ActionTicket>(VmDetailActivity.this) {
                    @Override
                    public void onResponse(ActionTicket ticket) throws RemoteException {
                        try {
                            if (freshVm.getDisplayType() == Vm.Display.SPICE && freshVm.getDisplaySecurePort() != -1 && !isCaFileExists()) {
                                showMissingCaCertDialog();
                            } else {
                                Intent intent = new Intent(Intent.ACTION_VIEW)
                                        .setType("application/vnd.vnc")
                                        .setData(Uri.parse(makeConsoleUrl(freshVm, ticket)));
                                startActivity(intent);
                            }
                        } catch (IllegalArgumentException e) {
                            makeToast(e.getMessage());
                        } catch (Exception e) {
                            makeToast("Failed to open console client. Check if aSPICE/bVNC is installed.");
                        }
                    }
                });
            }
        }, vmId);
    }

    @UiThread(propagation = UiThread.Propagation.REUSE)
    void showMissingCaCertDialog() {
        ImportCertificateDialogFragment importCertificateDialog = ImportCertificateDialogFragment
                .newSpiceCaInstance(getString(R.string.can_not_run_console_without_ca),
                        authenticator.getCertHandlingStrategy().id(),
                        authenticator.getApiUrl());
        importCertificateDialog.show(getFragmentManager(), "certificateDialog");
    }

    private void syncVm() {
        vmFacade.syncOne(new ProgressBarResponse<Vm>(this), vmId);
    }

    /**
     * Returns URL for running console intent.
     *
     * @throws java.lang.IllegalArgumentException with description
     *                                            if the URL can't be created from input.
     */
    private String makeConsoleUrl(Vm vm, ActionTicket ticket)
            throws IllegalArgumentException {

        if (vm.getDisplayType() == null) {
            throw new IllegalArgumentException("Vm's display type cannot be null");
        }

        String parameters = "";
        if (ticket != null && ticket.ticket != null && ticket.ticket.value != null
                && !ticket.ticket.value.isEmpty()) {
            switch (vm.getDisplayType()) {
                case VNC:
                    String vncPasswordPart = Constants.PARAM_VNC_PWD + "=" + ticket.ticket.value;
                    parameters = vncPasswordPart;
                    break;
                case SPICE:
                    String spicePasswordPart = Constants.PARAM_SPICE_PWD + "=" + ticket.ticket.value;
                    parameters = spicePasswordPart;
                    if (vm.getDisplaySecurePort() != -1) {
                        String caCertPath = Constants.getCaCertPath();
                        String tlsPortPart = Constants.PARAM_TLS_PORT + "=" + vm.getDisplaySecurePort();
                        String certSubjectPart = Constants.PARAM_CERT_SUBJECT + "=" + vm.getCertificateSubject();
                        String caCertPathPart = Constants.PARAM_CA_CERT_PATH + "=" + caCertPath;

                        parameters += "&" + tlsPortPart + "&" + certSubjectPart + "&" + caCertPathPart;
                    }
                    break;
            }
        }

        String url = vm.getDisplayType().getProtocol() + "://" + vm.getDisplayAddress() + ":" + vm.getDisplayPort()
                + "?" + parameters;
        return url;
    }

    private boolean isCaFileExists() {
        File file = new File(Constants.getCaCertPath());
        return file.exists();
    }

    @UiThread(propagation = UiThread.Propagation.REUSE)
    void makeToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread(propagation = UiThread.Propagation.REUSE)
    @Override
    //maybe not quite right name for this function now,
    //it updates reference to VM entity after been loaded and rendered in fragment.
    //Used for Menu and migrate Dialog.
    public void updateMenuItem(Vm vm) {
        currentVm = vm;
        invalidateOptionsMenu();
    }

    /**
     * Refreshes VM upon success
     */
    private class SyncVmResponse extends OVirtClient.SimpleResponse<Void> {
        @Override
        public void onResponse(Void obj) throws RemoteException {
            syncVm();
        }
    }

    @OptionsItem(android.R.id.home)
    public void homeSelected() {
        app.startMainActivity();
    }
}
