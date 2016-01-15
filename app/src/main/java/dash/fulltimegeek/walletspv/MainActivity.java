package dash.fulltimegeek.walletspv;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Base58;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Dimension;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.ITFWriter;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet2Params;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.core.listeners.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringBufferInputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;


import javax.annotation.Nullable;


public class MainActivity extends Activity implements PeerDataEventListener, PeerConnectionEventListener, WalletEventListener, NewBestBlockListener, Button.OnClickListener {

    final static String TAG = "MainActivity.javaa";
    static public MainActivity activity;
    final static int MENU_MAIN = 0;
    final static int MENU_OTHER = 1;
    NetworkParameters params;
    DashKit kit;
    IntentIntegrator scanIntegrator;
    TextView tvBalance;
    TextView tvPending;
    TextView tvRecipient;
    TextView tvReceivingAddress;
    TextView tvIxFeeWarning;
    TextView tvAlertSend;
    TextView tvBlockHeight;
    CheckBox cbIx;
    EditText etAmountSending;
    RelativeLayout rlPending;
    LinearLayout llMenuButtons;
    Dialog sendDialog;
    Dialog receiveDialog;
    ImageView qrImg;
    ImageView logo;
    static boolean waitingToSend = false;
    static boolean waitingToImport = false;
    Button scanSend;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG,"DASH-SPV STARTING....");
        setContentView(R.layout.activity_main);
        activity = this;
        setupDialogs();
        setupButtons();
        setupTextViews();
        scanIntegrator = new IntentIntegrator(activity);
        try {
            MnemonicCode.INSTANCE = new MnemonicCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        params = MainNetParams.get();
        createCheckpoint(false);
        kit = new DashKit(params, getFilesDir(), "checkpoint") {
            @Override
            protected  void onShutdownCompleted(){
                if(restoringCheckpoint) {
                    Log.i(TAG,"Restoring Checkpoint...");
                    Looper.prepare();
                    createCheckpoint(true);
                    startSyncing();
                }
            }
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                wallet().addEventListener(activity);
                vChain.addNewBestBlockListener(activity);
                updateBlockHeight(vChain.getChainHead());
                updateGUI();
                this.peerGroup().addConnectionEventListener(activity);
                progress.dismiss();

            }
        };
        startSyncing();
    }

    public static List<String> ConvertStringsToStringList(String items, String separator)
    {
        List<String> list = new ArrayList<String>();
        String[] listItmes = items.split(separator);
        for(String item : listItmes)
        {
            list.add(item);
        }
        return list;
    }

    Button btnSend;
    Button btnReceive;
    Button btnOther;
    Button btnImport;
    Button btnRescan;
    Button btnMainMenu;
    public void setupButtons(){
        btnOther = (Button) findViewById(R.id.btn_other);
        btnOther.setOnClickListener(this);
        btnSend = (Button) findViewById(R.id.btn_send);
        btnSend.setOnClickListener(this);
        btnReceive = (Button) findViewById(R.id.btn_receive);
        btnReceive.setOnClickListener(this);
        btnImport = (Button) findViewById(R.id.btn_import_key);
        btnImport.setOnClickListener(this);
        btnRescan = (Button) findViewById(R.id.btn_rescan_chain);
        btnRescan.setOnClickListener(this);
        btnMainMenu = (Button) findViewById(R.id.btn_main_menu);
        btnMainMenu.setOnClickListener(this);
        Button btn = (Button) sendDialog.findViewById(R.id.btn_cancel_send);
        btn.setOnClickListener(this);
        scanSend = (Button) sendDialog.findViewById(R.id.btn_scan_send);
        scanSend.setOnClickListener(this);
        btn = (Button) sendDialog.findViewById(R.id.btn_ok_send);
        btn.setOnClickListener(this);
        btn = (Button) receiveDialog.findViewById(R.id.btn_cancel_receive);
        btn.setOnClickListener(this);
        btn = (Button) receiveDialog.findViewById(R.id.btn_share_receive);
        btn.setOnClickListener(this);
        cbIx = (CheckBox) sendDialog.findViewById(R.id.cb_ix);
        cbIx.setOnClickListener(this);
    }

    public void setupDialogs(){
        LayoutInflater inflater = LayoutInflater.from(activity);
        sendDialog = new Dialog(activity);
        sendDialog.setCancelable(true);
        sendDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sendDialog.setContentView(inflater.inflate(
                R.layout.layout_sending, null));

        receiveDialog = new Dialog(activity);
        receiveDialog.setCancelable(true);
        receiveDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        receiveDialog.setContentView(inflater.inflate(R.layout.layout_receiving,null));

    }

    public void setupTextViews(){
        rlPending = (RelativeLayout) findViewById(R.id.rl_pending);
        llMenuButtons = (LinearLayout) findViewById(R.id.ll_menu_buttons);
        tvBalance = (TextView) findViewById(R.id.tv_balance);
        tvPending = (TextView) findViewById(R.id.tv_pending);
        tvRecipient = (TextView) sendDialog.findViewById(R.id.tv_recipient);
        etAmountSending = (EditText) sendDialog.findViewById(R.id.et_amount_send);
        qrImg = (ImageView) receiveDialog.findViewById(R.id.img_qr_receive);
        logo = (ImageView) findViewById(R.id.img_logo);
        logo.setOnClickListener(this);
        tvReceivingAddress = (TextView) receiveDialog.findViewById(R.id.tv_receiving_address);
        tvIxFeeWarning = (TextView) sendDialog.findViewById(R.id.tv_ix_fee);
        tvAlertSend = (TextView) sendDialog.findViewById(R.id.tv_alert_send);
        tvBlockHeight = (TextView) findViewById(R.id.tv_block_height);
    }

    public void resetWaiting(){
        waitingToImport = false;
        waitingToSend = false;
    }

    public void createCheckpoint(boolean rebuild){
        File chain = new File(getFilesDir(),"checkpoint.spvchain");
        OutputStream output = null;
        if(!chain.exists() || rebuild){
            try {
                Log.i(TAG,"Restoring checkpoint");
                chain.createNewFile();
                output = new FileOutputStream(chain);
                InputStream input = getResources().openRawResource(R.raw.checkpoint);
                try {
                    byte[] buffer = new byte[4 * 1024]; // or other buffer size
                    int read;

                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                } finally {
                    output.close();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(output != null){
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void updateGUI(){
        final int minConf = 1;
        if(kit.wallet() != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvBalance.setText(MonetaryFormat.BTC.format(kit.wallet().getBalance(Wallet.BalanceType.AVAILABLE, minConf)));
                    if (kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED).subtract(kit.wallet().getBalance(Wallet.BalanceType.AVAILABLE, minConf)).isPositive()) {
                        rlPending.setVisibility(View.VISIBLE);
                        tvPending.setText("+(" + MonetaryFormat.BTC.format(Coin.valueOf(kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED).subtract(kit.wallet().getBalance(Wallet.BalanceType.AVAILABLE, minConf)).getValue())) + ")");
                    } else {
                        rlPending.setVisibility(View.INVISIBLE);

                    }
                }
            });
        }
    }

    public void sendCoins(String amount, String recipient,boolean isIX) {
        if(isIX){
            BitcoinSerializer.names.remove(Transaction.class);
            BitcoinSerializer.names.put(Transaction.class, "ix");
            Transaction.REFERENCE_DEFAULT_MIN_TX_FEE = Transaction.DEFAULT_MIN_IX_FEE;
        }else{
            BitcoinSerializer.names.remove(Transaction.class);
            BitcoinSerializer.names.put(Transaction.class, "tx");
            Transaction.REFERENCE_DEFAULT_MIN_TX_FEE = Transaction.DEFAULT_MIN_TX_FEE;
        }
        Coin amountToSend = Coin.parseCoin(amount);
        //amountToSend.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        try {
            final Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), new Address(params, recipient), amountToSend);
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    Log.i(TAG, "Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, new Executor() {
                @Override
                public void execute(Runnable command) {
                    sendDialog.dismiss();
                    resetSendDialog();
                }
            });
        }catch (InsufficientMoneyException e){
            sendAlert("NOT ENOUGH FUNDS");
            e.printStackTrace();
        }catch(NumberFormatException e){
            sendAlert("CHECK NUMBER FORMAT");
            e.printStackTrace();
        }catch(Wallet.DustySendRequested e){
            sendAlert("AMOUNT TOO LOW");
            e.printStackTrace();
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        //if (id == R.id.action_settings) {
        //   return true;
        //}

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(intent != null) {
            IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanningResult != null) {
                try {
                    String scanContent = scanningResult.getContents();
                    String scanFormat = scanningResult.getFormatName();
                    scanContent = scanContent.replaceAll("dash:", "dash://");
                    if (!scanContent.contains("dash://")) {
                        scanContent = "dash://" + scanContent;
                    }
                    final Uri qrInfo = Uri.parse(scanContent);
                    if (waitingToSend) {
                        if (qrInfo.getHost() != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    scanSend.setVisibility(View.INVISIBLE);
                                    tvRecipient.setText(qrInfo.getHost());
                                    tvRecipient.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                        if (qrInfo.getQueryParameter("amount") != null) {
                            etAmountSending.setText(qrInfo.getQueryParameter("amount"));

                        }
                        resetWaiting();
                    } else if (waitingToImport) {
                        try {
                            DumpedPrivateKey dumpKey = new DumpedPrivateKey(params, qrInfo.getHost());
                            if (kit.wallet().importKey(dumpKey.getKey())) {
                                Toast.makeText(activity, "Succesfully imported", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(activity, "Failed to import key", Toast.LENGTH_LONG).show();
                            }
                            resetWaiting();
                        }catch(org.bitcoinj.core.AddressFormatException e){
                            Toast.makeText(activity, "Failed to import key", Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (UnsupportedOperationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {

    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {

    }

    @Override
    public Message onPreMessageReceived(Peer peer, Message m) {
        // Log.i(TAG,"onPreMessageReceived");
        return null;
    }

    @Nullable
    @Override
    public List<Message> getData(Peer peer, GetDataMessage m) {
        return null;
    }

    @Override
    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
        //Log.i(TAG,"onPeerDiscovered");
    }

    @Override
    public void onPeerConnected(Peer peer, int peerCount) {
        //Log.i(TAG,"onPeerConnected");

    }

    @Override
    public void onPeerDisconnected(Peer peer, int peerCount) {
        //Log.i(TAG,"onPeerDisconnected");
    }

    @Override
    public void onReorganize(Wallet wallet) {

    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
        //Log.i(TAG, "onTransactionConfChanged for tx: "+tx.getHashAsString()+" | confidence depth="+tx.getConfidence().getDepthInBlocks()+" | balance= "+wallet.getBalance());
    }

    @Override
    public void onWalletChanged(Wallet wallet) {
        updateGUI();
    }

    @Override
    public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {

    }

    @Override
    public void onKeysAdded(List<ECKey> keys) {

    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        updateGUI();
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_other:
                buildMenuButtons(MENU_OTHER);
                break;
            case R.id.btn_import_key:
                waitingToImport = true;
                scanIntegrator.initiateScan();
                break;
            case R.id.btn_rescan_chain:
                rescanFromCheckpoint();
                break;
            case R.id.img_logo:
                buildMenuButtons(MENU_MAIN);
                break;
            case R.id.btn_main_menu:
                buildMenuButtons(MENU_MAIN);
                break;
            case R.id.btn_scan_send:
                tvAlertSend.setVisibility(View.INVISIBLE);
                tvAlertSend.setText("");
                waitingToSend = true;
                scanIntegrator.initiateScan();
                break;
            case R.id.cb_ix:
                if(cbIx.isChecked()) {
                    tvIxFeeWarning.setVisibility(View.VISIBLE);
                }else{
                    tvIxFeeWarning.setVisibility(View.INVISIBLE);
                }
                break;
            case R.id.btn_share_receive:
                shareQRCode();
                break;
            case R.id.btn_receive:
                if(kit != null && kit.wallet() != null) {
                    updateReceiveQR("dash:" + kit.wallet().currentReceiveAddress().toString());
                    tvReceivingAddress.setText(kit.wallet().currentReceiveAddress().toString());
                    receiveDialog.show();
                }else{
                    Toast.makeText(activity,"Wallet is still initializing...",Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.btn_cancel_receive:
                receiveDialog.dismiss();
                break;
            case R.id.btn_send:
                sendDialog.show();
                break;
            case R.id.btn_ok_send:
                if(tvRecipient.getText()!=null &&
                        tvRecipient.getText().toString() != "" &&
                        etAmountSending.getText().toString() != "" &&
                        Float.parseFloat(etAmountSending.getText().toString()) > 0){
                        sendDash(etAmountSending.getText().toString(),tvRecipient.getText().toString(),cbIx.isChecked());
                }else if(tvRecipient.getText().toString().equals("")){
                    sendAlert("ADD RECIPIENT");
                }else if(etAmountSending.getText().toString() != "" || Float.parseFloat(etAmountSending.getText().toString())==0){
                    sendAlert("ENTER AMOUNT");
                }
                break;
            case R.id.btn_cancel_send:
                sendDialog.dismiss();
                resetSendDialog();
                break;
            default:
                break;
        }
    }

    public void updateReceiveQR(String content){
        //Encode with a QR Code image
        QREncoder qrCodeEncoder = new QREncoder(content,
                null,
                Contents.Type.TEXT,
                BarcodeFormat.QR_CODE.toString(),
                512);
        try {
            Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
            qrImg.setImageBitmap(bitmap);

        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void shareQRCode() {
        qrImg.buildDrawingCache();
        Bitmap icon = qrImg.getDrawingCache();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/jpeg");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        File f = new File(Environment.getExternalStorageDirectory() + File.separator + "temporary_file.jpg");
        try {
            f.createNewFile();
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/temporary_file.jpg"));
        share.putExtra(Intent.EXTRA_SUBJECT,"Dash Payment Request");
        share.putExtra(Intent.EXTRA_TEXT,kit.wallet().currentReceiveAddress().toString());
        startActivity(share);
    }

    boolean restoringCheckpoint = false;
    public void rescanFromCheckpoint(){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(kit.wallet()!=null)
                        kit.wallet().reset();
                    restoringCheckpoint = true;
                    kit.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        t.start();
    }

    public void rescan(){

    }

    public void showToast(final String string){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity,string,Toast.LENGTH_LONG).show();
            }
        });
    }

    public void sendDash(String amount, String recipient, boolean isIX){
        String decrypt = "";
        KeyCrypter crypter = null;
        KeyParameter keyParameter = null;
        if(kit.wallet().isEncrypted()) {
            crypter = kit.wallet().getKeyCrypter();
            decrypt = "test";
            keyParameter = crypter.deriveKey(decrypt);
            try {
                kit.wallet().decrypt(keyParameter);
            }catch(KeyCrypterException e){
                e.printStackTrace();
            }
        }
        if(!kit.wallet().isEncrypted()) {
            Coin minFee = Transaction.DEFAULT_MIN_TX_FEE;
            if (isIX) {
                minFee = Transaction.DEFAULT_MIN_IX_FEE;
            }
            if (!Coin.parseCoin(amount).isGreaterThan(isIX ? kit.wallet().getBalance().subtract(minFee) : kit.wallet().getBalance().subtract(minFee))) {
                if (isIX) {
                    if (kit.wallet().getBalance(Wallet.BalanceType.AVAILABLE, 6).subtract(minFee).isPositive()) {
                        sendCoins(amount, recipient, isIX);
                    } else {
                        sendAlert("IX -- CONF TOO LOW");
                    }
                } else {
                    sendCoins(amount, recipient, isIX);
                }
            } else {
                sendAlert("INSUFFICIENT FUNDS");
            }
            if(!decrypt.equals("") && crypter != null && keyParameter != null){
                kit.wallet().encrypt(crypter,keyParameter);
            }
        }
    }

    ProgressDialog progress=null;
    boolean ranStartAsync = false;
    public void startSyncing(){
            progress = new ProgressDialog(activity);
            progress.setTitle("Please wait...");
            progress.setMessage("Starting wallet");
            progress.setCancelable(false);
            progress.show();

        kit.setDownloadListener(this);
        if(!ranStartAsync){
            ranStartAsync=true;
            kit.startAsync();
        }else{
            try {
                kit.startup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void removeMenuButtons(){
        while(llMenuButtons.getChildAt(0) != null){
            llMenuButtons.removeViewAt(0);
        }
    }

    public void buildMenuButtons(int whichMenu) {
        removeMenuButtons();
        if (whichMenu == MENU_MAIN) {
            llMenuButtons.addView(btnSend);
            llMenuButtons.addView(btnReceive);
            llMenuButtons.addView(btnOther);
        }else if(whichMenu == MENU_OTHER){
            llMenuButtons.addView(btnImport);
            llMenuButtons.addView(btnRescan);
            llMenuButtons.addView(btnMainMenu);
            btnImport.setVisibility(View.VISIBLE);
            btnRescan.setVisibility(View.VISIBLE);
            btnMainMenu.setVisibility(View.VISIBLE);
        }
    }

    public void resetSendDialog(){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanSend.setVisibility(View.VISIBLE);
                tvIxFeeWarning.setVisibility(View.INVISIBLE);
                tvAlertSend.setText("");
                tvAlertSend.setVisibility(View.INVISIBLE);
                tvRecipient.setVisibility(View.INVISIBLE);
                tvRecipient.setText("");
                cbIx.setChecked(false);
                etAmountSending.setText("0.000");
            }
        });
    }

    public void sendAlert(final String string){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvAlertSend.setVisibility(View.VISIBLE);
                tvAlertSend.setText(string);
            }
        });
    }

    @Override
    public void notifyNewBestBlock(final StoredBlock block) throws VerificationException {
        updateBlockHeight(block);
    }

    public void updateBlockHeight(final StoredBlock block){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //tvBlockHeight.setText("" + kit.wallet().getLastBlockSeenHeight());
                tvBlockHeight.setText("" +block.getHeight());
            }
        });
    }
}
