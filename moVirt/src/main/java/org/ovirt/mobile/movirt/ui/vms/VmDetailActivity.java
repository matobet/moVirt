package org.ovirt.mobile.movirt.ui.vms;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringArrayRes;
import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.auth.MovirtAuthenticator;
import org.ovirt.mobile.movirt.facade.VmFacade;
import org.ovirt.mobile.movirt.model.Vm;
import org.ovirt.mobile.movirt.model.trigger.Trigger;
import org.ovirt.mobile.movirt.provider.ProviderFacade;
import org.ovirt.mobile.movirt.rest.ActionTicket;
import org.ovirt.mobile.movirt.rest.OVirtClient;
import org.ovirt.mobile.movirt.ui.AdvancedAuthenticatorActivity;
import org.ovirt.mobile.movirt.ui.AdvancedAuthenticatorActivity_;
import org.ovirt.mobile.movirt.ui.AreYouSureDialog;
import org.ovirt.mobile.movirt.ui.Constants;
import org.ovirt.mobile.movirt.ui.DiskDetailFragment;
import org.ovirt.mobile.movirt.ui.DiskDetailFragment_;
import org.ovirt.mobile.movirt.ui.EventsFragment;
import org.ovirt.mobile.movirt.ui.EventsFragment_;
import org.ovirt.mobile.movirt.ui.FragmentListPagerAdapter;
import org.ovirt.mobile.movirt.ui.HasProgressBar;
import org.ovirt.mobile.movirt.ui.MovirtActivity;
import org.ovirt.mobile.movirt.ui.NicDetailFragment;
import org.ovirt.mobile.movirt.ui.NicDetailFragment_;
import org.ovirt.mobile.movirt.ui.ProgressBarResponse;
import org.ovirt.mobile.movirt.ui.UpdateMenuItemAware;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity;
import org.ovirt.mobile.movirt.ui.triggers.EditTriggersActivity_;

import java.io.File;

@EActivity(R.layout.activity_vm_detail)
@OptionsMenu(R.menu.vm)
public class VmDetailActivity extends MovirtActivity implements HasProgressBar, UpdateMenuItemAware<Vm> {

    private static final String TAG = VmDetailActivity.class.getSimpleName();
    private static final int REQUEST_MIGRATE = 0;
    @ViewById
    ViewPager viewPager;
    @ViewById
    PagerTabStrip pagerTabStrip;
    @StringArrayRes(R.array.vm_detail_pager_titles)
    String[] PAGER_TITLES;
    @Bean
    OVirtClient client;
    @ViewById
    ProgressBar progress;
    @Bean
    VmFacade vmFacade;

    @Bean
    ProviderFacade providerFacade;

    @Bean
    MovirtAuthenticator authenticator;
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
    private String vmId = null;
    private Vm currentVm = null;

    @AfterViews
    void init() {
        Uri vmUri = getIntent().getData();
        vmId = vmUri.getLastPathSegment();

        initPagers();
        setProgressBar(progress);
    }

    private void initPagers() {
        EventsFragment eventsList = new EventsFragment_();
        DiskDetailFragment diskDetails = new DiskDetailFragment_();
        NicDetailFragment nicDetails = new NicDetailFragment_();

        eventsList.setFilterVmId(vmId);
        diskDetails.setVmId(vmId);
        nicDetails.setVmId(vmId);

        FragmentListPagerAdapter pagerAdapter = new FragmentListPagerAdapter(
                getSupportFragmentManager(), PAGER_TITLES,
                new VmDetailGeneralFragment_(),
                eventsList,
                diskDetails,
                nicDetails);

        viewPager.setAdapter(pagerAdapter);
        pagerTabStrip.setTabIndicatorColorResource(R.color.material_deep_teal_200);
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
        client.startVm(vmId);
        syncVm();
    }

    @OptionsItem(R.id.action_stop)
    @UiThread
    void stop() {
        AreYouSureDialog.show(this, getResources(), "stop the VM", new Runnable() {
            @Override
            public void run() {
                doStop();
            }
        });
    }

    @Background
    void doStop() {
        client.stopVm(vmId);
        syncVm();
    }

    @OptionsItem(R.id.action_reboot)
    @UiThread
    void reboot() {
        AreYouSureDialog.show(this, getResources(), "reboot the VM", new Runnable() {
            @Override
            public void run() {
                doReboot();
            }
        });
    }

    @Background
    void doReboot() {
        client.rebootVm(vmId);
        syncVm();
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
        client.migrateVmToDefaultHost(vmId);
        syncVm();
    }

    @Background
    public void doMigrationTo(String hostId) {
        client.migrateVmToHost(vmId, hostId);
        syncVm();
    }

    @OptionsItem(R.id.action_cancel_migration)
    @UiThread
    void cancelMigration() {
        AreYouSureDialog.show(this, getResources(), "cancel VM migration", new Runnable() {
            @Override
            public void run() {
                doCancelMigration();
            }
        });
    }

    @Background
    void doCancelMigration() {
        client.cancelMigration(vmId);
        syncVm();
    }

    @OptionsItem(R.id.action_console)
    @Background
    void openConsole() {
        vmFacade.sync(vmId, new ProgressBarResponse<Vm>(this) {

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
        });
    }

    @UiThread
    void showMissingCaCertDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.missing_ca_cert)
                .setPositiveButton(R.string.import_str, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(VmDetailActivity.this, AdvancedAuthenticatorActivity_.class);
                        intent.putExtra(AdvancedAuthenticatorActivity.MODE, AdvancedAuthenticatorActivity.MODE_SPICE_CA_MANAGEMENT);
                        intent.putExtra(AdvancedAuthenticatorActivity.ENFORCE_HTTP_BASIC_AUTH, authenticator.enforceBasicAuth());
                        intent.putExtra(AdvancedAuthenticatorActivity.CERT_HANDLING_STRATEGY, authenticator.getCertHandlingStrategy());
                        intent.putExtra(AdvancedAuthenticatorActivity.LOAD_CA_FROM, authenticator.getApiUrl());
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        makeToast(getString(R.string.can_not_run_console_without_ca));
                    }
                })
                .show();
    }

    private void syncVm() {
        vmFacade.sync(vmId, new ProgressBarResponse<Vm>(this));
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
                        String caCertPath = Constants.getCaCertPath(this);
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
        File file = new File(Constants.getCaCertPath(this));
        return file.exists();
    }

    @UiThread
    void makeToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    @Override
    //maybe not quite right name for this function now,
    //it updates reference to VM entity after been loaded and rendered in fragment.
    //Used for Menu and migrate Dialog.
    public void updateMenuItem(Vm vm) {
        currentVm = vm;
        invalidateOptionsMenu();
    }
}
