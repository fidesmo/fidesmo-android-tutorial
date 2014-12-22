package com.fidesmo.tutorials.hellofidesmo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;

/**
 * Functions to handle the phone's NFC capabilities
 */
public class NfcHelpers {

    /**
     * Get priority to receive events when a contactless card is discovered at the NFC interface
     * @param activity the activity that will get the priority
     * @param adapter the local (phone's) NfcAdapter
     */
    public static void enableForegroundDispatch(Activity activity, NfcAdapter adapter) {
        Intent intent = activity.getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(adapter.isEnabled()) {
            PendingIntent tagIntent = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            IntentFilter iso = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
            adapter.enableForegroundDispatch(activity, tagIntent, new IntentFilter[]{iso},
                    new String[][]{new String[]{IsoDep.class.getName()}});
        }
    }

    /**
     * Unregister the activity from NFC events
     * @param activity the activity to be unregistered
     * @param adapter the local (phone's) NfcAdapter
     */
    public static void disableForegroundDispatch(Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    /**
     * Obtain a reference to the ISO 14443-4 card detected at the NFC interface from the
     * Intent representing the "tag detected" event
     * @param intent object representing the "tag detected" event
     * @return reference to the ISO card
     */
    public static IsoDep getIsoTag(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(tag != null) {
            return IsoDep.get(tag);
        } else {
            return null;
        }
    }
}
