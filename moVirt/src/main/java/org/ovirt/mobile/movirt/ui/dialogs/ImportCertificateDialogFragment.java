package org.ovirt.mobile.movirt.ui.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.ovirt.mobile.movirt.R;
import org.ovirt.mobile.movirt.ui.auth.AuthenticatorActivity;
import org.ovirt.mobile.movirt.ui.auth.AuthenticatorActivity_;

/**
 * Dialog asking user to import certificate.
 * Created by Nika on 20.08.2015.
 */
public class ImportCertificateDialogFragment extends DialogFragment {

    public static ImportCertificateDialogFragment newInstance(@Nullable String additionalMessage,
                                                              boolean startActivity) {
        ImportCertificateDialogFragment fragment = new ImportCertificateDialogFragment();
        Bundle args = new Bundle();
        if (additionalMessage != null) {
            args.putString("additionalMessage", additionalMessage);
        }
        args.putBoolean("startActivity", startActivity);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String additionalMessage = getArguments().getString("additionalMessage");
        final boolean startActivity = getArguments().getBoolean("startActivity");

        StringBuilder message =
                new StringBuilder(getString(R.string.dialog_certificate_missing_start));
        if (additionalMessage != null) {
            message.append('\n').append(additionalMessage).append('\n');
        }
        message.append(getString(R.string.dialog_certificate_missing_end));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(android.R.string.dialog_alert_title)
                .setMessage(message.toString())
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (startActivity) {
                            final Intent intent = new Intent(getActivity(),
                                    AuthenticatorActivity_.class);
                            intent.putExtra(AuthenticatorActivity.SHOW_ADVANCED_AUTHENTICATOR, true);
                            startActivity(intent);
                        } else {
                            ((AuthenticatorActivity) getActivity()).btnAdvancedClicked();
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, null);
        return builder.create();
    }
}
