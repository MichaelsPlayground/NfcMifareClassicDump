package de.androidcrypto.nfcmifareclassicdump;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    TextView dumpField, readResult;
    private NfcAdapter mNfcAdapter;
    String dumpExportString = "";
    String tagIdString = "";
    String tagTypeString = "";
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 100;
    Context contextSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(myToolbar);
        contextSave = getApplicationContext();

        dumpField = findViewById(R.id.tvMainDump1);
        readResult = findViewById(R.id.tvMainReadResult);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    // This method is run in another thread when a card is discovered
    // !!!! This method cannot cannot direct interact with the UI Thread
    // Use `runOnUiThread` method to change the UI from this method
    @Override
    public void onTagDiscovered(Tag tag) {
        // Read and or write to Tag here to the appropriate Tag Technology type class
        // in this example the card should be an Ndef Technology Type

        System.out.println("NFC tag discovered");

        MifareClassic mif = null;

        mif = MifareClassic.get(tag);
        //nfcA = NfcA.get(tag);

        if (mif != null) {
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(),
                        "NFC tag is MifareClassic compatible",
                        Toast.LENGTH_SHORT).show();
            });

            // Make a Sound
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, 10));
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(200);
            }

            runOnUiThread(() -> {
                readResult.setText("");
            });

            int ttype = mif.getType();
            writeToUiAppend(readResult, "MifareClassic type: " + ttype);
            int tagSize = mif.getSize();
            writeToUiAppend(readResult, "MiC size: " + tagSize);
            int sectorCount = mif.getSectorCount();
            writeToUiAppend(readResult, "MiC sector count: " + sectorCount);
            int blockCount = mif.getBlockCount();
            writeToUiAppend(readResult, "MiC block count: " + blockCount);

            // this is the complete memory
            byte[] cardMemoryBytes = new byte[tagSize];
            byte[] unreadableContent = new byte[64];
            // lets begin to read the card
            try {
                mif.connect();

                if (mif.isConnected()) {
                    for (int secCnt = 0; secCnt < sectorCount; secCnt++) {
                        byte[] sectorContent = readMifareSector(mif, secCnt);
                        if (sectorContent == null) {
                            writeToUiAppend(readResult, "ERROR - sector " + secCnt + " could not get read");
                            sectorContent = unreadableContent.clone();
                        }
                        System.arraycopy(sectorContent, 0, cardMemoryBytes, (secCnt * 64), 64);
                    }
                    String dumpContentHeader = "Card content:\n" + HexDumpOwn.prettyPrint(cardMemoryBytes);
                    writeToUiAppend(readResult, dumpContentHeader);


                }
                mif.close();

            } catch (IOException e) {
                writeToUiAppend(readResult, "IOException: " + e);
                e.printStackTrace();
            }

/*
            // check that the tag is a NTAG213/215/216 manufactured by NXP - stop if not
            String ntagVersion = NfcIdentifyNtag.checkNtagType(nfcA, tag.getId());
            if (ntagVersion.equals("0")) {
                runOnUiThread(() -> {
                    readResult.setText("NFC tag is NOT of type NXP NTAG213/215/216");
                    Toast.makeText(getApplicationContext(),
                            "NFC tag is NOT of type NXP NTAG213/215/216",
                            Toast.LENGTH_SHORT).show();
                });
                return;
            }
*/
            /*
            int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // important for the readFast command
            int ntagPages = NfcIdentifyNtag.getIdentifiedNtagPages();
            int ntagMemoryBytes = NfcIdentifyNtag.getIdentifiedNtagMemoryBytes();
            tagIdString = getDec(tag.getId());
            tagTypeString = NfcIdentifyNtag.getIdentifiedNtagType();
            String nfcaContent = "raw data of " + tagTypeString + "\n" +
                    "number of pages: " + ntagPages +
                    " total memory: " + ntagMemoryBytes +
                    " bytes\n" +
                    "tag ID: " + bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId()) + "\n" +*/
            String nfcaContent = "raw data MifareClassic " +
                    "tag ID: " + tagIdString + "\n";
            //nfcaContent = nfcaContent + "maxTranceiveLength: " + nfcaMaxTranceiveLength + " bytes\n";
            // read the complete memory depending on ntag type
            byte[] headerMemory = new byte[16]; // 4 pages of each 4 bytes, e.g. manufacturer data
            byte[] ntagMemory = new byte[16]; // user memory, 888 byte for a NTAG216
            byte[] footerMemory = new byte[20]; // 5 pages, e.g. dyn. lock bytes, configuration pages, password & pack

            // read the content of the tag in several runs

            // first we are reading the header
            System.out.println("reading the header");
            headerMemory = getFastTagDataRange(mif, 0, 3);
            if (headerMemory == null) {
                writeToUiAppend(readResult, "ERROR on reading header, aborted");
            }
            String dumpContentHeader = "Header content:\n" + HexDumpOwn.prettyPrint(headerMemory);

            writeToUiAppend(readResult, nfcaContent);
            writeToUiAppend(readResult, dumpContentHeader);


            /*

            int footerStart = 4 + ntagPages;
            int footerEnd = 4 + footerStart;
            System.out.println("reading the footer");
            footerMemory = getFastTagDataRange(nfcA, footerStart, footerEnd);
            if (footerMemory == null) {
                writeToUiAppend(readResult, "ERROR on reading footer, aborted");
            }
            String dumpContentFooter = "Footer content:\n" + HexDumpOwn.prettyPrint(footerMemory);

            byte[] response;
            try {
                //int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // my device: 253 bytes
                int nfcaMaxTranceive4ByteTrunc = nfcaMaxTranceiveLength / 4; // 63
                int nfcaMaxTranceive4ByteLength = nfcaMaxTranceive4ByteTrunc * 4; // 252 bytes
                int nfcaNrOfFullReadings = ntagMemoryBytes / nfcaMaxTranceive4ByteLength; // 888 bytes / 252 bytes = 3 full readings
                int nfcaTotalFullReadingBytes = nfcaNrOfFullReadings * nfcaMaxTranceive4ByteLength; // 3 * 252 = 756
                int nfcaMaxTranceiveModuloLength = ntagMemoryBytes - nfcaTotalFullReadingBytes; // 888 bytes - 756 bytes = 132 bytes
                nfcaContent = nfcaContent + "nfcaMaxTranceive4ByteTrunc: " + nfcaMaxTranceive4ByteTrunc + "\n";
                nfcaContent = nfcaContent + "nfcaMaxTranceive4ByteLength: " + nfcaMaxTranceive4ByteLength + "\n";
                nfcaContent = nfcaContent + "nfcaNrOfFullReadings: " + nfcaNrOfFullReadings + "\n";
                nfcaContent = nfcaContent + "nfcaTotalFullReadingBytes: " + nfcaTotalFullReadingBytes + "\n";
                nfcaContent = nfcaContent + "nfcaMaxTranceiveModuloLength: " + nfcaMaxTranceiveModuloLength + "\n";

                for (int i = 0; i < nfcaNrOfFullReadings; i++) {
                    System.out.println("starting round: " + i);
                    response = getFastTagDataRange(nfcA, (4 + (nfcaMaxTranceive4ByteTrunc * i)), (4 + (nfcaMaxTranceive4ByteTrunc * (i + 1)) - 1));
                    if (response == null) {
                        writeToUiAppend(readResult, "ERROR on reading user memory, aborted");
                    } else {
                        // success: response contains ACK or actual data
                        System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * i), nfcaMaxTranceive4ByteLength);
                    }
                } // for

                // now we read the nfcaMaxTranceiveModuloLength bytes, for a NTAG216 = 132 bytes
                response = getFastTagDataRange(nfcA, (4 + (nfcaMaxTranceive4ByteTrunc * nfcaNrOfFullReadings)), (4 + (nfcaMaxTranceive4ByteTrunc * nfcaNrOfFullReadings) + (nfcaMaxTranceiveModuloLength / 4)));
                if (response == null) {
                    writeToUiAppend(readResult, "ERROR on reading user memory, aborted");
                } else {
                    // success: response contains ACK or actual data
                    System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * nfcaNrOfFullReadings), nfcaMaxTranceiveModuloLength);
                }
                nfcaContent = nfcaContent + "fast reading complete: " + "\n" + bytesToHex(ntagMemory) + "\n";

                String finalNfcaRawText = nfcaContent;
                String dumpContent = dumpContentHeader + "\n\nUser memory content:\n" + HexDumpOwn.prettyPrint(ntagMemory);
                dumpContent = dumpContent + "\n\n" + dumpContentFooter;
                System.out.println(dumpContent);
                dumpExportString = dumpContent;
                String finalDumpContent = dumpContent;
                runOnUiThread(() -> {
                    dumpField.setText(finalDumpContent);
                    readResult.setText(finalNfcaRawText);
                    System.out.println(finalNfcaRawText);
                });

            } finally {
                try {
                    nfcA.close();
                } catch (IOException e) {
                    writeToUiAppend(readResult, "ERROR IOException: " + e);
                }
            } */
        }
    }

    private byte[] readMifareSector(MifareClassic mif, int secCnt) {
        byte[] returnBytes = new byte[0];
        boolean isAuthenticated = false;
        // try to authenticate with known keys - no brute force
        try {
            if (mif.authenticateSectorWithKeyA(secCnt, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)) {
                isAuthenticated = true;
            } else if (mif.authenticateSectorWithKeyA(secCnt, MifareClassic.KEY_DEFAULT)) {
                isAuthenticated = true;
            } else if (mif.authenticateSectorWithKeyA(secCnt, MifareClassic.KEY_NFC_FORUM)) {
                isAuthenticated = true;
            } else if (mif.authenticateSectorWithKeyB(secCnt, MifareClassic.KEY_DEFAULT)) {
                isAuthenticated = true;
            } else if (mif.authenticateSectorWithKeyB(secCnt, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)) {
                isAuthenticated = true;
            } else if (mif.authenticateSectorWithKeyB(secCnt, MifareClassic.KEY_NFC_FORUM)) {
                isAuthenticated = true;
                writeToUiAppend(readResult, "auth success in sector: " + secCnt);
            } else {
                writeToUiAppend(readResult, "auth denied in sector: " + secCnt);
                // gives an error, no access to textview from background thread
                return null;
            }
            // get the blockindex
            int block_index = mif.sectorToBlock(secCnt);
            // get block in sector
            int blocksInSector = mif.getBlockCountInSector(secCnt);
            // get the data of each block
            returnBytes = new byte[(16 * blocksInSector)];
            for (int blockInSectorCount = 0; blockInSectorCount < blocksInSector; blockInSectorCount++) {
                // get following data
                byte[] block = mif.readBlock((block_index + blockInSectorCount));
                System.arraycopy(block, 0, returnBytes, (blockInSectorCount * 16), 16);
            }
        } catch (IOException e) {
            writeToUiAppend(readResult, "IOException: " + e);
            e.printStackTrace();
            return null;
        }
        return returnBytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    private String getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result + "";
    }

    private void writeToUiAppend(TextView textView, String message) {
        runOnUiThread(() -> {
            String newString = message + "\n" + textView.getText().toString();
            textView.setText(newString);
        });
    }

    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private byte[] getFastTagDataRange(MifareClassic nfcA, int fromPage, int toPage) {
        byte[] response;
        byte[] command = new byte[]{
                (byte) 0x3A,  // FAST_READ
                (byte) (fromPage & 0x0ff),
                (byte) (toPage & 0x0ff),
        };
        try {
            response = nfcA.transceive(command); // response should be 16 bytes = 4 pages
            if (response == null) {
                // either communication to the tag was lost or a NACK was received
                writeToUiAppend(readResult, "ERROR on reading page");
                return null;
            } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                // NACK response according to Digital Protocol/T2TOP
                writeToUiAppend(readResult, "ERROR NACK received");
                // Log and return
                return null;
            } else {
                // success: response contains ACK or actual data
            }
        } catch (TagLostException e) {
            // Log and return
            writeToUiAppend(readResult, "ERROR Tag lost exception");
            return null;
        } catch (IOException e) {
            writeToUiAppend(readResult, "ERROR IOException: " + e);
            e.printStackTrace();
            return null;
        }
        return response;
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    private void exportDumpMail() {
        if (dumpExportString.isEmpty()) {
            writeToUiToast("Scan a tag first before sending emails :-)");
            return;
        }
        String subject = "Dump NFC-Tag " + tagTypeString + " UID: " + tagIdString;
        String body = dumpExportString;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void exportDumpFile() {
        if (dumpExportString.isEmpty()) {
            writeToUiToast("Scan a tag first before writing files :-)");
            return;
        }
        verifyPermissionsWriteString();
    }

    // section external storage permission check
    private void verifyPermissionsWriteString() {
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[1]) == PackageManager.PERMISSION_GRANTED) {
            writeStringToExternalSharedStorage();
        } else {
            ActivityCompat.requestPermissions(this,
                    permissions,
                    REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    private void writeStringToExternalSharedStorage() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Optionally, specify a URI for the file that should appear in the
        // system file picker when it loads.
        //boolean pickerInitialUri = false;
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        // get filename from edittext
        String filename = tagTypeString + "_" + tagIdString + ".txt";
        // sanity check
        if (filename.equals("")) {
            writeToUiToast("scan a tag before writng the content to a file :-)");
            return;
        }
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        fileSaverActivityResultLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> fileSaverActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent resultData = result.getData();
                        // The result data contains a URI for the document or directory that
                        // the user selected.
                        Uri uri = null;
                        if (resultData != null) {
                            uri = resultData.getData();
                            // Perform operations on the document using its URI.
                            try {
                                // get file content from edittext
                                String fileContent = dumpExportString;
                                writeTextToUri(uri, fileContent);
                                String message = "file written to external shared storage: " + uri.toString();
                                writeToUiToast("file written to external shared storage: " + uri.toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                                writeToUiToast("ERROR: " + e.toString());
                                return;
                            }
                        }
                    }
                }
            });

    private void writeTextToUri(Uri uri, String data) throws IOException {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(contextSave.getContentResolver().openOutputStream(uri));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            System.out.println("Exception File write failed: " + e.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {

            if (!mNfcAdapter.isEnabled())
                showWirelessSettings();

            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);

        MenuItem mExportMail = menu.findItem(R.id.action_export_mail);
        mExportMail.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //Intent i = new Intent(MainActivity.this, AddEntryActivity.class);
                //startActivity(i);
                exportDumpMail();
                return false;
            }
        });

        MenuItem mExportFile = menu.findItem(R.id.action_export_file);
        mExportFile.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //Intent i = new Intent(MainActivity.this, AddEntryActivity.class);
                //startActivity(i);
                exportDumpFile();
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

}