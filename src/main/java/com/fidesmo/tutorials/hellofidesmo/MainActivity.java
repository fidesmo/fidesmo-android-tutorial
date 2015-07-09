package com.fidesmo.tutorials.hellofidesmo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import java.io.IOException;
import java.util.Arrays;

import nordpol.IsoCard;
import nordpol.android.AndroidCard;
import nordpol.android.TagDispatcher;
import nordpol.android.OnDiscoveredTagListener;

/**
 * Unique Activity in the HelloFidesmo example app, written for the Fidesmo Android tutorial
 * It attempts to open the NFC interface (if disabled, shows a dialog to the user)
 * Once a card is detected, it sends a SELECT command towards the cardlet written in the tutorial
 *  - if successful, it displays the string returned by the card
 *  - if unsuccessful, it assumes the cardlet is not installed on the card and triggers
 *  the service delivery process using the Fidesmo App
 */
@EActivity(R.layout.activity_main)
public class MainActivity extends ActionBarActivity implements OnDiscoveredTagListener {

    // APPLICATION_ID is the value assigned to your application by Fidesmo
    final private static String APPLICATION_ID = "C8739B19";
    final private static String SERVICE_ID = "HelloFidesmo";
    final private static byte[] successfulApdu = new byte[]{(byte)0x90, 0x00};

    // Constants used to initiate cardlet delivery through the Fidesmo App
    private final static String FIDESMO_APP = "com.fidesmo.sec.android";
    private final static String SERVICE_URI = "https://api.fidesmo.com/service/";
    private final static String SERVICE_DELIVERY_CARD_ACTION = "com.fidesmo.sec.DELIVER_SERVICE";
    // Code to identify the call when starting Intent for Result
    static private final int SERVICE_DELIVERY_REQUEST_CODE = 724;

    // URLs for Google Play app and to install apps via browser
    private final static String MARKET_URI = "market://details?id=";
    private final static String MARKET_VIA_BROWSER_URI = "http://play.google.com/store/apps/details?id=";

    private static final String TAG = "MainActivity";
    // The TagDispatcher is responsible for managing the NFC for the activity
    private TagDispatcher dispatcher;

    // UI elements
    @ViewById(R.id.main_text)
    TextView mainMessage;
    @ViewById(R.id.install_button)
    Button installButton;

    // Once the UI elements have been drawn, get the object to access the NFC capabilities
    // If the phone's NFC capability is not enabled, show a dialog
    @AfterViews
    void setupNFC(){
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (!adapter.isEnabled()) {
            Log.i(TAG, "NFC is not enabled. This is an error");
            showTurnNFCDialog();
        }
        // The first argument is the activity for which the NFC is managed
        // The second argument is the OnDiscoveredTagListener which is also implemented by this activity
        // This means that tagDiscovered will be called whenever a new tag appears
        dispatcher = TagDispatcher.get(this, this);
    }

    // Stop listening on the NFC interface if the app loses focus
    @Override
    public void onPause() {
        super.onPause();
        dispatcher.disableExclusiveNfc();
    }

    // Start listening on the NFC interface when the app gains focus.
    @Override
    protected void onResume() {
        super.onResume();
        dispatcher.enableExclusiveNfc();
    }

    /**
     * This method is called when a contactless device is detected at the NFC interface
     * @param intent the PendingIntent declared in onResume
     */
    protected void onNewIntent(Intent intent) {
        dispatcher.interceptIntent(intent);
    }

    public void tagDiscovered(Tag tag) {
        Log.i(TAG, "Card detected on the NFC interface!");
        try {
            mainMessage.setText(R.string.reading_card);
            readCard(AndroidCard.get(tag));
        } catch(IOException ioe) {
            Log.e(TAG, "Failed to produce card from tag", ioe);
        }
    }

    /**
     * Sends a SELECT APDU to the HelloFidesmo cardlet on the card and parses the response
     * - If the response's status bytes are '90 00' (=APDU successfully executed), it displays the response payload
     * - If not, it assumes that HelloFidesmo cardlet was not installed and shows a button
     *   so the user can launch the installation process
     * @param card card detected at the NFC interface, supporting ISO 14443/4 standard
     */
    protected void readCard(final IsoCard card) {
        byte[] response = null;
        try {
            card.connect();
            response = card.transceive(Utils.selectApdu(APPLICATION_ID));
            card.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading card", e);
        }

        // Analyze the response. Its last two bytes are the status bytes - '90 00' means 'success'
        if (response != null && Arrays.equals(Utils.statusBytes(response), successfulApdu)) {
            Log.i(TAG, "Card returned SUCCESS");
            // print the message
            byte[] payload = Utils.responseData(response);
            String printableResponse = new String();
            for (int i=0; i<payload.length; i++) printableResponse += (char)payload[i];
            mainMessage.setText(printableResponse);

        } else {
            Log.i(TAG, "Card returned FAILURE");
            // enable the button so the user can install the cardlet
            mainMessage.setText(R.string.cardlet_not_installed);
            installButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows a dialog asking the user to enable the NFC functions, and exits when clicked
     */
    protected void showTurnNFCDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enable_nfc);
        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Calls the Fidesmo App in order to install the HelloFidesmo cardlet into the Fidesmo Card
     * First, it checks whether the Fidesmo App is installed; if not, it opens Google Play
     */
    @Click(R.id.install_button)
    protected void launchCardletInstallation() {
        if (appInstalledOrNot(FIDESMO_APP)) {
            try {
                // create Intent to the Action exposed by the Fidesmo App
                Intent intent = new Intent(SERVICE_DELIVERY_CARD_ACTION, Uri.parse(SERVICE_URI + APPLICATION_ID + "/" + SERVICE_ID));
                startActivityForResult(intent, SERVICE_DELIVERY_REQUEST_CODE);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error when parsing URI");
            }
        } else {
            notifyMustInstall();
        }

    }

    // method called when the Fidesmo App activity has finished
    // Will redraw the screen if it finished successfully
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == SERVICE_DELIVERY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Cardlet installation returned SUCCESS");
                mainMessage.setText(R.string.put_card);
                installButton.setVisibility(View.GONE);
            } else {
                Log.i(TAG, "Cardlet installation returned FAILURE");
                Toast.makeText(getApplicationContext(), getString(R.string.failure),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Log.i(TAG, "Unknown Activity has returned a result");
        }

    }

    /**
     * Use the package manager to detect if an application is installed on the phone
     * @param uri an URI identifying the application's package
     * @return 'true' is the app is installed
     */
    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = getPackageManager();
        boolean app_installed = false;
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            app_installed = true;
        }
        catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Fidesmo App not installed in phone");
            app_installed = false;
        }
        return app_installed;
    }

    /**
     * Show a Toast message to the user informing that Fidesmo App must be installed and launch the Google Play app-store
     */
    private void notifyMustInstall() {
        Toast.makeText(getApplicationContext(), R.string.install_app_message, Toast.LENGTH_LONG).show();
        // if the Google Play app is not installed, call the browser
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URI + FIDESMO_APP)));
        } catch (android.content.ActivityNotFoundException exception) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_VIA_BROWSER_URI + FIDESMO_APP)));
        }

    }

}
