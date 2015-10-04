package com.fidesmo.tutorials.hellofidesmo;

import android.content.Intent;
import android.nfc.Tag;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.UiThread;

import java.io.IOException;

import nordpol.Apdu;
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
public class MainActivity extends AppCompatActivity implements OnDiscoveredTagListener {

    private static final String TAG = "MainActivity";

    //APDU commands
    private static final byte[] SELECT_APDU = {0x00, (byte) 0xa4, 0x04, 0x00, 0x08, (byte) 0xa0, 0x00, 0x00, 0x05, 0x27, 0x21, 0x01, 0x01};
    private static final byte[] CALCULATE_APDU = {0x00, (byte)0xa2, 0x00, 0x01, 0x00};
    private static final byte[] SEND_REMAINING_APDU = {0x00, (byte) 0xa5, 0x00, 0x00, 0x00};
    private static final byte[] OK_APDU = {(byte) 0x90, 0x00};

    String fidesmoAidOtp = "94C3CD60";
    String helloFidesmoAid = "C8739B19";

    //TODO What is this?
    private static final int[] MOD = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    private static final String TOTP_CODE_NAME = "FidesmoOTPTutorial:tutorial@fidesmo.com";

    // The TagDispatcher is responsible for managing the NFC for the activity
    private TagDispatcher tagDispatcher;

    // UI elements
    @ViewById
    TextView mainText;

    //Two methods for setting the UI (on UI thread, because, threading...)
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
        // The second argument is the OnDiscoveredTagListener which is also
        // implemented by this activity
        // This means that tagDiscovered will be called whenever a new tag appears
        tagDispatcher = TagDispatcher.get(this, this);
        // Start listening on the NFC interface when the app gains focus.
        tagDispatcher.enableExclusiveNfc();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop listening on the NFC interface if the app loses focus
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

    //Called whenever a tag is discovered on the NFC interface
    @Override
    public void tagDiscovered(Tag tag) {
        try {
            setMainMessage(R.string.reading_card);
            IsoCard isoCard = AndroidCard.get(tag);
            communicateWithCard(isoCard);
        } catch(IOException ioe) {
            Log.e(TAG, "Failed to produce card from tag", ioe);
        }
    }

    /**
     * Sends a SELECT APDU to the cardlet on the card and parses the response
     * - If the response's status bytes are '90 00' (=APDU successfully executed), it displays the response payload
     * @param isoCard card detected at the NFC interface, supporting ISO 14443/4 standard
     */
    protected void communicateWithCard(final IsoCard isoCard) {
        try {
            //Connect to the card
            isoCard.connect();
            //TODO Send a nordpol select command to the app with versionnumber and make sure we are a go
            requireStatus(send(SELECT_APDU, isoCard), OK_APDU);
            //requireStatus(isoCard.transceive(Apdu.select(helloFidesmoAid)), OK_APDU);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Get a time stamp for creating a TOTP code APDU from
        long timeStamp = getTimeStamp();

        //Get the APDU command for generating the TOTP code with the name TOTP_CODE_NAME and generated for the time in timeStamp. We are looking for a predefined TOTP token.
        byte[] totpCodeApdu = getTotpCodeApdu(TOTP_CODE_NAME, timeStamp);

        //Get the TOTP code from the card with our APDU
        String totpCodeString = getTotpCode(totpCodeApdu, isoCard);

        //Set the generated code as main message
        setMainMessage(totpCodeString);

        //Close the card communication.
        try {
            isoCard.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long getTimeStamp() {
        //Get the current time + 10s to ensure that our generated code is valid at least for that long.
        return (System.currentTimeMillis() / 1000 + 10) / 30;
    }

    private String getTotpCode(byte[] totpCodeApdu, IsoCard isoCard) {
        //Get a raw TOTP code that has to be cleaned later
        byte[] rawTotpCode = null;
        try {
            rawTotpCode = requireStatus(send(totpCodeApdu, isoCard), OK_APDU);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Parse and fix TOTP code before returning it
        String totpCode = null;
        final byte T_RESPONSE_TAG = 0x76;
        try {
            totpCode = codeFromTruncated(parseBlock(rawTotpCode, 0, T_RESPONSE_TAG));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return totpCode;
    }

    private static boolean compareStatus(byte[] apdu, byte[] status) {
        return apdu[apdu.length - 2] == status[0] && apdu[apdu.length - 1] == status[1];
    }

    private static byte[] requireStatus(byte[] apdu, byte[] status) throws IOException {
        if (!compareStatus(apdu, status)) {
            String expected = String.format("%02x%02x", 0xff & status[0], 0xff & status[1]).toUpperCase();
            String actual = String.format("%02x%02x", 0xff & apdu[apdu.length - 2], 0xff & apdu[apdu.length - 1]).toUpperCase();
            throw new IOException("Require APDU status: " + expected + ", got " + actual);
        }
        return apdu;
    }

    private static byte[] getTotpCodeApdu(String name, long timestamp) {
        final byte NAME_TAG = 0x71;
        final byte CHALLENGE_TAG = 0x74;

        byte[] nameBytes = name.getBytes();
        byte[] data = new byte[CALCULATE_APDU.length + 2 + nameBytes.length + 10];
        System.arraycopy(CALCULATE_APDU, 0, data, 0, CALCULATE_APDU.length);
        int offset = 4;
        data[offset++] = (byte) (data.length - 5);
        data[offset++] = NAME_TAG;
        data[offset++] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, data, offset, nameBytes.length);
        offset += nameBytes.length;

        data[offset++] = CHALLENGE_TAG;
        data[offset++] = 8;
        data[offset++] = 0;
        data[offset++] = 0;
        data[offset++] = 0;
        data[offset++] = 0;
        data[offset++] = (byte) (timestamp >> 24);
        data[offset++] = (byte) (timestamp >> 16);
        data[offset++] = (byte) (timestamp >> 8);
        data[offset++] = (byte) timestamp;

        return data;
    }

    private static String codeFromTruncated(byte[] data) {
        int num_digits = data[0];
        int code = (data[1] << 24) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 8) | (data[4] & 0xff);
        return String.format("%0" + num_digits + "d", code % MOD[num_digits]);
    }

    private byte[] send(byte[] command, IsoCard isoCard) throws IOException {
        byte[] resp = isoCard.transceive(command);
        byte[] buf = new byte[2048];
        int offset = 0;

        while (resp[resp.length - 2] == 0x61) {
            System.arraycopy(resp, 0, buf, offset, resp.length - 2);
            offset += resp.length - 2;
            resp = isoCard.transceive(SEND_REMAINING_APDU);
        }

        System.arraycopy(resp, 0, buf, offset, resp.length);
        byte[] properlySized = new byte[offset + resp.length];
        System.arraycopy(buf, 0, properlySized, 0, properlySized.length);

        return properlySized;
    }

    private static byte[] parseBlock(byte[] data, int offset, byte identifier) throws IOException {
        if (data[offset] == identifier) {
            int length = data[offset + 1];
            byte[] block = new byte[length];
            System.arraycopy(data, offset + 2, block, 0, length);
            return block;
        } else {
            throw new IOException("Require block type: " + identifier + ", got: " + data[offset]);
        }
    }
}
