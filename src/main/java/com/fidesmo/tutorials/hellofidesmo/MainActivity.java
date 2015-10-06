package com.fidesmo.tutorials.hellofidesmo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.Tag;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.UiThread;

import java.io.IOException;
import java.util.Arrays;

import nordpol.IsoCard;
import nordpol.android.AndroidCard;
import nordpol.android.TagDispatcher;
import nordpol.android.OnDiscoveredTagListener;
import nordpol.Apdu;
/**
 * Unique Activity in the HelloFidesmo example app, written for the Fidesmo Android tutorial
 * It attempts to open the NFC interface (if disabled, shows a dialog to the user)
 * Once a card is detected, it sends a SELECT command towards the cardlet written in the tutorial
 *  - if successful, it displays the string returned by the card
 *  - if unsuccessful, it assumes the cardlet is not installed on the card and triggers
 *  the service delivery process using the Fidesmo App
 */
@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity implements OnDiscoveredTagListener {

    // APPLICATION_ID is the value assigned to your application by Fidesmo
    final private static String APPLICATION_ID = "C8739B19";
    final private static String APP_VERSION = "0101";
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
    private TagDispatcher tagDispatcher;

    // UI elements
    @ViewById
    TextView mainText;
    @ViewById
    Button installButton;

    @UiThread
    void setMainMessage(int resource) {
        mainText.setText(resource);
    }

    @UiThread
    void setMainMessage(String text) {
        mainText.setText(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The first argument is the activity for which the NFC is managed
        // The second argument is the OnDiscoveredTagListener which is also implemented by this activity
        // This means that tagDiscovered will be called whenever a new tag appears
        tagDispatcher = TagDispatcher.get(this, this);
        // Start listening on the NFC interface when the app gains focus.
        tagDispatcher.enableExclusiveNfc();
    }

    // Stop listening on the NFC interface if the app loses focus
    @Override
    public void onPause() {
        super.onPause();
        tagDispatcher.disableExclusiveNfc();
    }

    /**
     * This method is called when a contactless device is detected at the NFC interface
     * @param intent the PendingIntent declared in onResume
     */
    @Override
    protected void onNewIntent(Intent intent) {
        tagDispatcher.interceptIntent(intent);
    }

    @Override
    public void tagDiscovered(Tag tag) {
        Log.i(TAG, "Card detected on the NFC interface!");
        try {
            setMainMessage(R.string.reading_card);
            IsoCard isoCard = AndroidCard.get(tag);
            readCard(isoCard);
        } catch(IOException ioe) {
            Log.e(TAG, "Failed to produce card from tag", ioe);
        }
    }

    /**
     * Sends a SELECT APDU to the HelloFidesmo cardlet on the card and parses the response
     * - If the response's status bytes are '90 00' (=APDU successfully executed), it displays the response payload
     * - If not, it assumes that HelloFidesmo cardlet was not installed and shows a button
     *   so the user can launch the installation process
     * @param isoCard card detected at the NFC interface, supporting ISO 14443/4 standard
     */
    protected void readCard(final IsoCard isoCard) {
        byte[] response = null;
        try {
            isoCard.connect();
            response = isoCard.transceive(Apdu.select(APPLICATION_ID, APP_VERSION));
            isoCard.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading card", e);
        }

        // Analyze the response. Its last two bytes are the status bytes - '90 00' means 'success'
        if (response != null && Arrays.equals(Apdu.statusBytes(response), successfulApdu)) {
            Log.i(TAG, "Card returned SUCCESS");
            // print the message
            byte[] payload = Apdu.responseData(response);
            String printableResponse = new String();
            for (int i=0; i<payload.length; i++) printableResponse += (char)payload[i];
            setMainMessage(printableResponse);

        } else {
            Log.i(TAG, "Card returned FAILURE");
            // enable the button so the user can install the cardlet
            setMainMessage(R.string.cardlet_not_installed);
            installButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Calls the Fidesmo App in order to install the HelloFidesmo cardlet into the Fidesmo Card
     * First, it checks whether the Fidesmo App is installed; if not, it opens Google Play
     */
    @Click
    void installButtonClicked() {
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
    @OnActivityResult(SERVICE_DELIVERY_REQUEST_CODE)
    void onResult(int resultCode) {
        if (resultCode == RESULT_OK) {
            Log.i(TAG, "Cardlet installation returned SUCCESS");
            setMainMessage(R.string.put_card);
            installButton.setVisibility(View.GONE);
        } else {
            Log.i(TAG, "Cardlet installation returned FAILURE");
            Toast.makeText(getApplicationContext(), getString(R.string.failure),
                    Toast.LENGTH_LONG).show();
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
