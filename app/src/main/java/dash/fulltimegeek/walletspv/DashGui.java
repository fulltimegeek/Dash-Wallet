package dash.fulltimegeek.walletspv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.core.listeners.*;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.DeterministicSeed;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;


import javax.annotation.Nullable;


public class DashGui extends Activity implements PeerDataEventListener, PeerConnectionEventListener, WalletEventListener, NewBestBlockListener, Button.OnClickListener {

    final static String TAG = "DashGui.java";
    static public DashGui activity;
    DashService service = null;
    final static int MENU_MAIN = 0;
    final static int MENU_OTHER = 1;
    static int currentMenu = MENU_MAIN;
    final static int PROGRESS_NONE = -1;
    final static int PROGRESS_STARTING = 0;
    final static int PROGRESS_RESCANING =1;
    final static int PROGRESS_ENCRYPTING = 2;
    final static int PROGRESS_UNLOCKING = 3;
    final static int PIN_MIN_LENGTH = 6;
    final static int MAX_WORD_LIST = 12;
    final static public int REQUEST_FILE_SELECT = 1235;
    final static String WALLET_BACKUP_DIR = "/sdcard/dash/backups/";
    final static public File saveDir = new File(WALLET_BACKUP_DIR);
    static int currentProgress = PROGRESS_NONE;
    DialogConfirmPreparer genesisScanConfirm;
    IntentIntegrator scanIntegrator;
    TextView tvBalance;
    TextView tvPending;
    TextView tvRecipient;
    TextView tvReceivingAddress;
    TextView tvIxFeeWarning;
    TextView tvAlertSend;
    TextView tvBlockHeight;
    TextView tvSendMsg;
    TextView tvAlertEncrypt;
    TextView tvSeedBox;
    TextView tvTitleEnterWord;
    TextView tvPopUp;
    CheckBox cbIx;
    EditText etEnterWord;
    EditText etAmountSending;
    EditText etPin;
    EditText etPinConfirm;
    EditText etEnterPin;
    RelativeLayout rlPending;
    RelativeLayout rlMain;
    LinearLayout llMenuButtons;
    Dialog sendDialog;
    Dialog receiveDialog;
    Dialog initWalletDialog;
    Dialog restoreWalletDialog;
    Dialog encryptDialog;
    Dialog enterPinDialog;
    Dialog backupDialog;
    Dialog seedBoxDialog;
    Dialog enterWordDialog;
    Dialog historyDialog;
    ListView historyListView;
    ImageView qrImg;
    ImageView logo;
    static boolean waitingToSend = false;
    static boolean waitingToImport = false;
    static boolean isBound =  false;
    Button scanSend;
    String walletPrefix = null;
    ArrayList<String> recoveryWords = new ArrayList<String>();
    static SharedPreferences preferences;
    static final String PREF_KEY_WALLET_PREFIX ="walletPrefix";
    ECKey tmpKey = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "DASH-SPV STARTING.....");
        setContentView(R.layout.activity_main);
        activity = this;
        preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setupConfirmers();
        setupDialogs();
        setupButtons();
        setupTextViews();
        scanIntegrator = new IntentIntegrator(activity);
        try {
            MnemonicCode.INSTANCE = new MnemonicCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(progress != null)
            progress.dismiss();
    }

    @Override
    protected void onResume(){
        super.onResume();
        buildMenuButtons(DashGui.currentMenu);
        showProgress(DashGui.currentProgress);
        doBindService();
    }

    @Override
    public void onStop(){
        super.onStop();
        if(service != null){
            service.gui = null;
        }
        if(isBound){
            isBound = false;
            unbindService(sConnection);
        }
    }


    public void startDashService(){
        if(service != null && !service.hasStarted()){
            Log.i(TAG,"DashService was not running .... starting");
            showProgress(PROGRESS_STARTING);
            startService(new Intent(this,DashService.class));
        }else{
            Log.i(TAG,"DashService was already running");
        }
    }

    public boolean defaultWalletExists(){
        String walletPrefix = preferences.getString(PREF_KEY_WALLET_PREFIX,null) == null?
                DashKit.defaultWalletAndChainPrefix:preferences.getString(PREF_KEY_WALLET_PREFIX,null);
        File file = new File(getFilesDir(),walletPrefix+DashKit.defaultWalletExt);
        Log.i(TAG,"Default wallet: "+file.getAbsolutePath());
        return file.exists();
    }

    void doBindService() {
        bindService(new Intent(DashGui.this, DashService.class),
                sConnection, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection sConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName classname, IBinder binder) {
            Log.i("DashGui.java","onServiceConnected");
            isBound = true;
            service = ((DashService.LocalBinder) binder).getService();
            service.gui = activity;
            service.setListeners(activity);
            updateGUI();
            if(defaultWalletExists()){
                startDashService(); // only starts if not already running
            }else if(!service.hasStarted() && currentProgress == PROGRESS_NONE){
                initWalletDialog.show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub

        }//
    };

    public static List<String> ConvertStringsToStringList(String items, String separator) {
        List<String> list = new ArrayList<String>();
        String[] listItmes = items.split(separator);
        for (String item : listItmes) {
            list.add(item);
        }
        return list;
    }

    Button btnSend;
    Button btnReceive;
    Button btnOther;
    Button btnHistory;
    Button btnEncrypt;
    Button btnMainMenu;
    Button btnCreateNewWallet;
    Button btnRestoreWallet;
    Button btnRestoreWalletFile;
    Button btnRestoreWalletSeed;
    Button btnRestoreWalletCancel;
    Button btnOkEncrypt;
    Button btnCancelEncrypt;
    Button btnOkEnterPin;
    Button btnCancelEnterPin;
    Button btnBackup;
    Button btnCancelBackup;
    Button btnShowSeed;
    Button btnOkSeedBox;
    Button btnOkEnterWord;
    Button btnCancelEnterWord;
    Button btnToFile;


    public void setupConfirmers(){
        genesisScanConfirm = new DialogConfirmPreparer(activity,new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(which == DialogConfirmPreparer.OK){
                    rescanFromCheckpoint(false);
                }
            }
        },"Rescan from Genesis","CANCEL","OK",true);
    }

    public void setupButtons() {
        btnOther = (Button) findViewById(R.id.btn_other);
        btnOther.setOnClickListener(this);
        btnSend = (Button) findViewById(R.id.btn_send);
        btnSend.setOnClickListener(this);
        btnReceive = (Button) findViewById(R.id.btn_receive);
        btnReceive.setOnClickListener(this);
        btnHistory = (Button) findViewById(R.id.btn_history);
        btnHistory.setOnClickListener(this);
        btnEncrypt = (Button) findViewById(R.id.btn_encrypt);
        btnEncrypt.setOnClickListener(this);
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
        btnCreateNewWallet = (Button) initWalletDialog.findViewById(R.id.btn_create_new_wallet);
        btnCreateNewWallet.setOnClickListener(this);
        btnRestoreWallet = (Button) initWalletDialog.findViewById(R.id.btn_restore_wallet);
        btnRestoreWallet.setOnClickListener(this);
        btnRestoreWalletFile = (Button) restoreWalletDialog.findViewById(R.id.btn_restore_wallet_file);
        btnRestoreWalletFile.setOnClickListener(this);
        btnRestoreWalletSeed = (Button) restoreWalletDialog.findViewById(R.id.btn_restore_wallet_seed);
        btnRestoreWalletSeed.setOnClickListener(this);
        btnRestoreWalletCancel = (Button) restoreWalletDialog.findViewById(R.id.btn_restore_wallet_cancel);
        btnRestoreWalletCancel.setOnClickListener(this);
        btnCancelEncrypt = (Button) encryptDialog.findViewById(R.id.btn_cancel_encrypt);
        btnCancelEncrypt.setOnClickListener(this);
        btnOkEncrypt = (Button) encryptDialog.findViewById(R.id.btn_ok_encrypt);
        btnOkEncrypt.setOnClickListener(this);
        btnOkEnterPin = (Button) enterPinDialog.findViewById(R.id.btn_ok_enter_pin);
        btnOkEnterPin.setOnClickListener(this);
        btnCancelEnterPin = (Button) enterPinDialog.findViewById(R.id.btn_cancel_enter_pin);
        btnCancelEnterPin.setOnClickListener(this);
        btnBackup = (Button) findViewById(R.id.btn_backup);
        btnBackup.setOnClickListener(this);
        btnCancelBackup = (Button) backupDialog.findViewById(R.id.btn_cancel_backup);
        btnCancelBackup.setOnClickListener(this);
        btnShowSeed = (Button) backupDialog.findViewById(R.id.btn_show_seed);
        btnShowSeed.setOnClickListener(this);
        btnOkSeedBox = (Button) seedBoxDialog.findViewById(R.id.btn_ok_seed_box);
        btnOkSeedBox.setOnClickListener(this);
        btnOkEnterWord = (Button) enterWordDialog.findViewById(R.id.btn_ok_enter_word);
        btnOkEnterWord.setOnClickListener(this);
        btnCancelEnterWord = (Button) enterWordDialog.findViewById(R.id.btn_cancel_enter_word);
        btnCancelEnterWord.setOnClickListener(this);
        btnToFile = (Button) backupDialog.findViewById(R.id.btn_to_file);
        btnToFile.setOnClickListener(this);
    }

    public void setupDialogs() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        sendDialog = new Dialog(activity);
        sendDialog.setCancelable(true);
        sendDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sendDialog.setContentView(inflater.inflate(
                R.layout.layout_sending, null));

        receiveDialog = new Dialog(activity);
        receiveDialog.setCancelable(true);
        receiveDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        receiveDialog.setContentView(inflater.inflate(R.layout.layout_receiving, null));
        initWalletDialog = new Dialog(activity);
        initWalletDialog.setCancelable(false);
        initWalletDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        initWalletDialog.setContentView(inflater.inflate(R.layout.layout_init_wallet, null));
        restoreWalletDialog = new Dialog(activity);
        restoreWalletDialog.setCancelable(false);
        restoreWalletDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        restoreWalletDialog.setContentView(inflater.inflate(R.layout.layout_restore_wallet_dialog, null));
        encryptDialog = new Dialog(activity);
        encryptDialog.setCancelable(true);
        encryptDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        encryptDialog.setContentView(inflater.inflate(R.layout.layout_encrypt, null));
        enterPinDialog = new Dialog(activity);
        enterPinDialog.setCancelable(true);
        enterPinDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterPinDialog.setContentView(inflater.inflate(R.layout.layout_enter_pin, null));
        backupDialog = new Dialog(activity);
        backupDialog.setCancelable(true);
        backupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        backupDialog.setContentView(inflater.inflate(R.layout.layout_backup, null));
        seedBoxDialog = new Dialog(activity);
        seedBoxDialog.setCancelable(true);
        seedBoxDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        seedBoxDialog.setContentView(inflater.inflate(R.layout.layout_show_seed, null));
        enterWordDialog = new Dialog(activity);
        enterWordDialog.setCancelable(false);
        enterWordDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterWordDialog.setContentView(inflater.inflate(R.layout.layout_enter_word, null));
        historyDialog = new Dialog(activity);
        historyDialog.setCancelable(true);
        historyDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        historyListView = new ListView(activity);
        historyDialog.setContentView(historyListView);
    }

    public void setupTextViews() {
        rlPending = (RelativeLayout) findViewById(R.id.rl_pending);
        rlMain = (RelativeLayout) findViewById(R.id.rl_main);
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
        tvSendMsg = (TextView) sendDialog.findViewById(R.id.tv_send_label);
        etPin = (EditText) encryptDialog.findViewById(R.id.et_encrypt_pin);
        etPinConfirm = (EditText) encryptDialog.findViewById(R.id.et_encrypt_pin_confirm);
        tvAlertEncrypt = (TextView) encryptDialog.findViewById(R.id.tv_alert_encrypt);
        etEnterPin = (EditText) enterPinDialog.findViewById(R.id.et_enter_pin);
        tvSeedBox = (TextView) seedBoxDialog.findViewById(R.id.tv_seed_box);
        tvTitleEnterWord = (TextView) enterWordDialog.findViewById(R.id.tv_enter_word_title);
        etEnterWord = (EditText) enterWordDialog.findViewById(R.id.et_enter_word);
        tvPopUp = (TextView) findViewById(R.id.tv_pop_up);
    }

    public void resetWaiting() {
        waitingToImport = false;
        waitingToSend = false;
    }

    public void updateBalance() {
        final int minConf = 1;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (service != null && service.setupCompleted && service.kit != null && service.kit.wallet() != null) {
                            tvBalance.setText(service.kit.wallet().getBalance().toFriendlyString());
                            if (service.kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED).subtract(service.kit.wallet().getBalance()).isPositive()) {
                                rlPending.setVisibility(View.VISIBLE);
                                tvPending.setText("+(" + service.kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED).subtract(service.kit.wallet().getBalance()).toFriendlyString() + ")");
                            } else {
                                rlPending.setVisibility(View.INVISIBLE);

                            }
                        }
                    }catch (NullPointerException e){
                        e.printStackTrace();
                    }
                }
            });

    }


    public void sendCoins(String amount, String recipient, boolean isIX) {
        if (isIX) {
            BitcoinSerializer.names.remove(Transaction.class);
            BitcoinSerializer.names.put(Transaction.class, "ix");
            Transaction.REFERENCE_DEFAULT_MIN_TX_FEE = Transaction.DEFAULT_MIN_IX_FEE;
        } else {
            BitcoinSerializer.names.remove(Transaction.class);
            BitcoinSerializer.names.put(Transaction.class, "tx");
            Transaction.REFERENCE_DEFAULT_MIN_TX_FEE = Transaction.DEFAULT_MIN_TX_FEE;
        }
        final Coin amountToSend = Coin.parseCoin(amount);
        //amountToSend.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        try {
            Wallet.SendRequest request = Wallet.SendRequest.to(new Address(service.params, recipient), amountToSend);
            if(isIX)
                request.coinSelector = new IXCoinSelector(); // We require inputs with
                                                             // sufficient confs for IX to work
            final Wallet.SendResult sendResult = service.kit.wallet().sendCoins(request);
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {

                }
            }, new Executor() {
                @Override
                public void execute(Runnable command) {
                    //sendDialog.dismiss();
                    //resetSendDialog();
                    showToast(MonetaryFormat.BTC.format(amountToSend)+"\nSENT");
                }
            });
        } catch (InsufficientMoneyException e) {
            sendAlert("NOT ENOUGH FUNDS");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            sendAlert("CHECK NUMBER FORMAT");
            e.printStackTrace();
        } catch (Wallet.DustySendRequested e) {
            sendAlert("AMOUNT TOO LOW");
            showToast("AMOUNT TOO LOW");
            e.printStackTrace();
        }
    }


    final static int MENU_BTN_IMPORT = 1;
    final static int MENU_BTN_RESCAN_CHECKPOINT = 2;
    final static int MENU_BTN_RESCAN_GENESIS = 3;
    final static int MENU_BTN_RESTORE = 4;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i("MainScreen.java","Creating Options Menu");
        menu.add(0, MENU_BTN_RESCAN_GENESIS, MENU_BTN_RESCAN_GENESIS, "Rescan (Genesis)");
        menu.add(0, MENU_BTN_RESCAN_CHECKPOINT, MENU_BTN_RESCAN_CHECKPOINT, "Rescan (Checkpoint)");
        menu.add(0, MENU_BTN_IMPORT,MENU_BTN_IMPORT, "Import Key");
        menu.add(0, MENU_BTN_RESTORE,MENU_BTN_RESTORE," Restore Wallet");
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_BTN_RESCAN_CHECKPOINT:
                rescanFromCheckpoint(true);
                break;

            case MENU_BTN_RESCAN_GENESIS:
                genesisScanConfirm.alert.show();
                break;

            case MENU_BTN_IMPORT:
                waitingToImport = true;
                scanIntegrator.initiateScan();
                break;

            case MENU_BTN_RESTORE:
                restoreWalletDialog.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private File restoreWallet(File bkFile){
        File newFile = new File(getFilesDir(),bkFile.getName());
        FileChannel src = null;
        FileChannel dest = null;
        try {
            src = new FileInputStream(bkFile).getChannel();
            dest = new FileOutputStream(newFile).getChannel();
            dest.transferFrom(src, 0, src.size());
        }catch (FileNotFoundException e){

        }catch (IOException e){

        }finally {
            if(src !=null) {
                try {
                    src.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(dest != null){
                try {
                    dest.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return newFile;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG,"onActivityResult() requestCode:"+requestCode+"| resultCode:"+resultCode);
        if (intent != null) {
            if(requestCode == REQUEST_FILE_SELECT){
                Log.i(TAG,"File selected:"+intent.getExtras().getString("fileSelected"));
                File walletBK = new File(intent.getExtras().getString("fileSelected"));
                if(walletBK.exists() && walletBK.canRead()){
                    if(restoreWallet(walletBK).exists()){
                        restoreWalletDialog.dismiss();
                        service.walletPrefix = walletBK.getName().replaceFirst("[.][^.]+$", "");
                        preferences.edit().putString(PREF_KEY_WALLET_PREFIX,service.walletPrefix).commit();
                        service.setReplayWallet(true);
                        rescanFromCheckpoint(true);
                    }
                }else{
                    //FILE GOT DELETED ALREADY SOMEHOW?
                }
            }
            IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanningResult != null) {
                try {
                    String scanContent = scanningResult.getContents();
                    Log.i(TAG,scanContent);
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
                        if (qrInfo.getQueryParameter("message") != null){
                            tvSendMsg.setText(qrInfo.getQueryParameter("message"));
                            tvSendMsg.setVisibility(View.VISIBLE);
                        }else if(qrInfo.getQueryParameter("label") != null){
                            tvSendMsg.setText(qrInfo.getQueryParameter("label"));
                            tvSendMsg.setVisibility(View.VISIBLE);
                        }
                        resetWaiting();
                    } else if (waitingToImport) {
                        try {
                            DumpedPrivateKey dumpKey = new DumpedPrivateKey(service.params, qrInfo.getHost());
                            resetWaiting();
                            if(service != null && service.kit != null && service.kit.wallet() != null) {
                                if(!service.kit.wallet().isEncrypted()) {
                                    importKey(dumpKey.getKey());
                                }else{
                                    tmpKey = dumpKey.getKey();
                                    btnOkEnterPin.setTag("import");
                                    enterPinDialog.show();
                                    enterPinDialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                                }
                            }else{
                                showToast("Failure: Could not connect to wallet");
                            }
                        } catch (org.bitcoinj.core.AddressFormatException e) {
                            showToast("Failure: Key Not Imported");
                            e.printStackTrace();
                        }
                    }
                } catch (UnsupportedOperationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void importKey(ECKey key){
        if(service != null && service.kit != null && !service.kit.wallet().isEncrypted()){
            boolean imported = service.kit.wallet().importKey(key);
            if (imported) {
                rescanFromCheckpoint(true);
                showToast("Success: Key Imported");
            } else {
                showToast("Success: Key Already In Wallet");
            }
        }else if(service.kit.wallet().isEncrypted()){
            showToast("Wallet is encrypted \n Unable to import key");
        }
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
        Log.i(TAG,"onBlocksDownloaded: "+blocksLeft);
    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        Log.i(TAG,"onChainDownloadedStarted with blocks left: "+blocksLeft);
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
        //Log.i(TAG,"onWalletChanged calling updateBalance");
        updateBalance();
    }

    @Override
    public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {

    }

    @Override
    public void onKeysAdded(List<ECKey> keys) {

    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        Log.i(TAG,"onCoinsReceived calling updateBalance");
        updateBalance();
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {

    }


    @Override
    public void onClick(View v) {
        Coin minFee = Transaction.DEFAULT_MIN_TX_FEE;
        CoinSelector coinSelector;

        switch (v.getId()) {
            case R.id.btn_restore_wallet_cancel:
                restoreWalletDialog.dismiss();
                if(!defaultWalletExists() && !service.hasStarted()){
                    initWalletDialog.show();
                }
                break;
            case R.id.btn_show_seed:
                backupDialog.dismiss();
                btnOkEnterPin.setTag("seed");
                enterPinDialog.show();
                enterPinDialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                break;
            case R.id.btn_encrypt:
                encryptDialog.show();
                etPin.requestFocus();
                encryptDialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                break;
            case R.id.btn_cancel_encrypt:
                resetEncryptDialog();
                break;
            case R.id.btn_ok_encrypt:
                if(etPin.getText().toString().equals(etPinConfirm.getText().toString())){
                    if(etPin.length() >= PIN_MIN_LENGTH){
                        tvAlertEncrypt.setText("");
                        if(service != null && service.kit !=null && service.kit.wallet() != null){
                            if(service.kit.wallet().isEncrypted()){
                                tvAlertEncrypt.setText("Wallet already encrypted");
                            }else {
                                final String pin = etPin.getText().toString();
                                resetEncryptDialog();
                                showProgress(PROGRESS_ENCRYPTING);
                                Thread t = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        service.kit.wallet().encrypt(pin);
                                        dismissProgress();
                                        showProgress(PROGRESS_NONE);
                                        buildMenuButtons(MENU_OTHER);
                                    }
                                });
                                t.start();

                            }
                        }
                    }else{
                        tvAlertEncrypt.setText("Pin Minimum Length is ["+PIN_MIN_LENGTH+"]");
                    }
                }else{
                    tvAlertEncrypt.setText("Pin Does Not Match");
                }
                break;
            case R.id.btn_ok_enter_pin:
                detectPin();
                break;
            case R.id.btn_ok_seed_box:
                seedBoxDialog.dismiss();
                tvSeedBox.setText("");
                break;
            case R.id.btn_restore_wallet:
                initWalletDialog.dismiss();
                restoreWalletDialog.show();
                break;
            case R.id.btn_create_new_wallet:
                initWalletDialog.dismiss();
                startDashService();
                break;
            case R.id.btn_other:
                buildMenuButtons(MENU_OTHER);
                break;
            case R.id.btn_restore_wallet_seed:
                restoreWalletDialog.dismiss();
                resetEnterWord();
                enterWordDialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                enterWordDialog.show();
                break;
            case R.id.btn_ok_enter_word:
                if(addRecoveryWord()){
                    enterWordDialog.dismiss();
                    try {
                        MnemonicCode.INSTANCE.check(recoveryWords);
                        Log.i(TAG,"Recovery seed:"+recoveryWords.toString());
                        byte[] seed = MnemonicCode.toSeed(recoveryWords,"");
                        DeterministicSeed dseed = new DeterministicSeed(recoveryWords, seed, "", 1);
                        service.setRecoverySeed(dseed);
                        if(service.setupCompleted){
                            rescanFromCheckpoint(true);
                        }else {
                            startDashService();
                        }
                    } catch (MnemonicException e) {
                        restoreWalletDialog.show();
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn_cancel_enter_word:
                resetEnterWord();
                enterWordDialog.dismiss();
                restoreWalletDialog.show();
                break;
            case R.id.img_logo:
                openOptionsMenu();
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
                if (cbIx.isChecked()) {
                    tvIxFeeWarning.setVisibility(View.VISIBLE);
                } else {
                    tvIxFeeWarning.setVisibility(View.INVISIBLE);
                }
                break;
            case R.id.btn_share_receive:
                shareQRCode();
                break;
            case R.id.btn_receive:
                if (service.kit != null && service.kit.wallet() != null) {
                    updateReceiveQR("dash:" + service.kit.wallet().currentReceiveAddress().toString());
                    tvReceivingAddress.setText(service.kit.wallet().currentReceiveAddress().toString());
                    receiveDialog.show();
                } else {
                    Toast.makeText(activity, "Wallet is still initializing...", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.btn_cancel_receive:
                receiveDialog.dismiss();
                break;
            case R.id.btn_send:
                resetSendDialog();
                sendDialog.show();
                break;
            case R.id.btn_ok_send:
                final boolean isIX = (cbIx.isChecked());
                final String amount = etAmountSending.getText().toString();
                final String recipient = tvRecipient.getText().toString();
                if (tvRecipient.getText() != null &&
                        tvRecipient.getText().toString() != "" &&
                        etAmountSending.getText().toString() != "" &&
                        Float.parseFloat(etAmountSending.getText().toString()) > 0) {
                    minFee = Transaction.DEFAULT_MIN_TX_FEE;
                    if (isIX) {
                        minFee = Transaction.DEFAULT_MIN_IX_FEE;
                        coinSelector = new IXCoinSelector();
                    }else{
                        coinSelector = new DefaultCoinSelector();
                    }
                    if(!Coin.parseCoin(amount).isGreaterThan(isIX ? service.kit.wallet().getBalance().subtract(minFee) : service.kit.wallet().getBalance().subtract(minFee))) {
                        if (service.kit.wallet().getBalance(coinSelector).subtract(minFee).isPositive()) {
                            if(service.kit.wallet().isEncrypted()){
                                sendDialog.dismiss();
                                btnOkEnterPin.setTag("send");
                                enterPinDialog.show();
                                enterPinDialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                            }else{
                                sendCoins(amount, recipient, isIX);
                            }
                        }else{
                            sendAlert("IX -- CONF TOO LOW");
                        }
                    }else{
                        sendAlert("INSUFFICIENT FUNDS");
                    }
                } else if (tvRecipient.getText().toString().equals("")) {
                    sendAlert("ADD RECIPIENT");
                } else if (etAmountSending.getText().toString() != "" || Float.parseFloat(etAmountSending.getText().toString()) == 0) {
                    sendAlert("ENTER AMOUNT");
                }
                break;
            case R.id.btn_cancel_send:
                sendDialog.dismiss();
                //resetSendDialog();
                break;
            case R.id.btn_cancel_enter_pin:
                etEnterPin.setText("");
                enterPinDialog.dismiss();
                break;
            case R.id.btn_cancel_backup:
                backupDialog.dismiss();
                break;
            case R.id.btn_backup:
                backupDialog.show();
                break;
            case R.id.btn_to_file:
                if(saveDir.mkdirs() || saveDir.exists()){
                    File save = new File(saveDir.getPath()+"/"+System.currentTimeMillis()+".wallet");
                    try {
                        showToast("Wallet Saved: "+save.getAbsolutePath());
                        service.kit.wallet().saveToFile(save);
                        backupDialog.dismiss();
                    } catch (IOException e) {
                        Log.i(TAG,"Failed to save wallet to:"+save.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn_restore_wallet_file:
                if(saveDir.mkdirs() || saveDir.exists()) {
                    startActivityForResult(new Intent(activity, br.com.thinkti.android.filechooser.FileChooser.class), REQUEST_FILE_SELECT);
                }
                break;
            case R.id.btn_history:
                List<Transaction> txes = new ArrayList<Transaction>(service.kit.wallet().getTransactionsByTime());
                TransactionListAdapter adapter = new TransactionListAdapter(activity,R.layout.layout_history_row,txes);
                historyListView.setAdapter(adapter);
                historyDialog.show();
                break;
            default:
                break;
        }
    }

    public void onWalletUnlockFail(){
        showProgress(PROGRESS_NONE);
        if(btnOkEnterPin.getTag().toString().equals("send")){
            sendAlert("Incorrect Pin");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sendDialog.show();
                }
            });
        }else if(btnOkEnterPin.getTag().toString().equals("seed")){

        }else if(btnOkEnterPin.getTag().toString().equals("import")){

        }
        showToast("Failed to unlock wallet");
    }

    public void onWalletUnlockSuccess(KeyCrypter crypter, KeyParameter key){
        showProgress(PROGRESS_NONE);
        if(btnOkEnterPin.getTag().toString().equals("send")){
            final boolean isIX = (cbIx.isChecked());
            final String amount = etAmountSending.getText().toString();
            final String recipient = tvRecipient.getText().toString();
            sendCoins(amount, recipient, isIX);
        }else if(btnOkEnterPin.getTag().toString().equals("seed")){
            Log.i(TAG,"SHOWING SEED");
            String tmpMnemonic = "";
            for(String word : service.kit.wallet().getKeyChainSeed().getMnemonicCode()){
                tmpMnemonic = tmpMnemonic.equals("")?word:tmpMnemonic+" "+word;
            }
            final String mnemonic = tmpMnemonic;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvSeedBox.setText(mnemonic);
                    seedBoxDialog.show();
                }
            });
        }else if(btnOkEnterPin.getTag().toString().equals("import")){
            if(tmpKey != null) {
                importKey(tmpKey);
            }
            tmpKey = null;
        }
        service.kit.wallet().encrypt(crypter, key);
    }

    public void unlockWallet(final String pin){
        if(currentProgress != PROGRESS_UNLOCKING){
            showProgress(PROGRESS_UNLOCKING);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    KeyCrypter crypter = service.kit.wallet().getKeyCrypter();
                    KeyParameter keyParameter = crypter.deriveKey(pin);
                    if (service.kit.wallet().checkAESKey(keyParameter)) {
                        service.kit.wallet().decrypt(keyParameter);
                        onWalletUnlockSuccess(crypter, keyParameter);
                    } else {
                        onWalletUnlockFail();
                    }
                }
            });
            t.start();
        }
    }

    public void detectPin(){
        final String pin = etEnterPin.getText().toString();
        if(service.kit.wallet().isEncrypted() && pin != null && !pin.equals("")) {
            unlockWallet(pin);
        }

        etEnterPin.setText("");
        enterPinDialog.dismiss();
    }


    public void updateReceiveQR(String content) {
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

    private void resetEncryptDialog(){
        encryptDialog.dismiss();
        etPin.setText("");
        etPinConfirm.setText("");
        tvAlertEncrypt.setText("");
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
        share.putExtra(Intent.EXTRA_SUBJECT, "Dash Payment Request");
        share.putExtra(Intent.EXTRA_TEXT, service.kit.wallet().currentReceiveAddress().toString());
        startActivity(share);
    }

    boolean restoringCheckpoint = false;
    static int rescanToBlock = 0;
    public void rescanFromCheckpoint(final boolean checkpoint) {
        Log.i(TAG,"rescanFromCheckpoint()");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (service.kit.wallet() != null)
                        service.kit.wallet().reset();
                    service.restoringCheckpoint = checkpoint;
                    service.restoringGenesis = !checkpoint;
                    service.kit.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        if(service != null && service.setupCompleted) {
            rescanToBlock = service.kit.chain().getBestChainHeight();
            if(rescanToBlock > -1) {
                Log.i(TAG,"Rescanning until block "+rescanToBlock);
                //DashGui.currentProgress=PROGRESS_RESCANING;
                showProgress(PROGRESS_RESCANING);
                t.start();
            }else{
                //currentProgress = PROGRESS_NONE;
                //dismissProgress();
                showProgress(PROGRESS_NONE);
                Log.i(TAG,"rescanToBlock too low");
                showToast("Connection to Internet required for rescan");
            }
        }else if(service != null && !service.setupCompleted){
            startDashService();
        }
    }

    public void dismissProgress(){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(progress != null) {
                    progress.dismiss();
                }else{
                    Log.i(TAG,"progress was null....nothing to dismiss");
                }
            }
        });
    }

    public void rescan() {

    }

    public void showToast(final String string) {
        Log.i(TAG, "Showing toast:" + string);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(rlMain != null && tvPopUp != null) {
                    rlMain.removeView(tvPopUp);
                    tvPopUp.setText(string);
                    rlMain.addView(tvPopUp);
                    tvPopUp.setVisibility(View.VISIBLE);
                }
            }
        });
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(rlMain != null && tvPopUp != null) {
                            rlMain.removeView(tvPopUp);
                        }
                    }
                });
            }
        });
        t.start();
    }


    static ProgressDialog progress = null;
    static boolean ranStartAsync = false;


    public void showProgress(final int type){
        DashGui.currentProgress = type;
        if(type != PROGRESS_NONE) {
            if(progress != null)
                progress.dismiss();
            Log.i(TAG,"showProgress() type:"+type);
            final String title = "Please Wait";
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress = new ProgressDialog(activity);
                    progress.setTitle(title);
                    progress.setCancelable(false);
                    if (type == PROGRESS_STARTING) {
                        final String msg = "Starting wallet";
                        progress.setMessage(msg + "...");
                    } else if (type == PROGRESS_RESCANING) {
                        final String msg = "Rescanning chain";
                        progress.setMessage(msg + "...");
                    } else if (type == PROGRESS_ENCRYPTING) {
                        final String msg = "Encrypting wallet";
                        progress.setMessage(msg);
                    } else if (type == PROGRESS_UNLOCKING){
                        final String msg = "Unlocking wallet";
                        progress.setMessage(msg);
                    }else {
                        final String msg = "Please wait";
                        progress.setMessage(msg + "...");
                    }
                    progress.show();
                }
            });
        }else{
            dismissProgress();
        }
    }

    private void removeMenuButtons() {
        while (llMenuButtons.getChildAt(0) != null) {
            llMenuButtons.removeViewAt(0);
        }
    }

    public void buildMenuButtons(final int whichMenu) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentMenu = whichMenu;
                removeMenuButtons();
                if (whichMenu == MENU_MAIN) {
                    llMenuButtons.addView(btnSend);
                    llMenuButtons.addView(btnReceive);
                    llMenuButtons.addView(btnOther);
                } else if (whichMenu == MENU_OTHER) {
                    llMenuButtons.addView(btnHistory);
                    if (service != null && service.kit != null && !service.kit.wallet().isEncrypted()) {
                        llMenuButtons.addView(btnEncrypt);
                    } else {
                        llMenuButtons.addView(btnBackup);
                    }
                    llMenuButtons.addView(btnMainMenu);
                }
            }
        });
    }

    public void resetSendDialog() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanSend.setVisibility(View.VISIBLE);
                tvIxFeeWarning.setVisibility(View.VISIBLE);
                tvAlertSend.setText("");
                tvAlertSend.setVisibility(View.INVISIBLE);
                tvRecipient.setVisibility(View.INVISIBLE);
                tvRecipient.setText("");
                cbIx.setChecked(true);
                tvSendMsg.setText("");
                tvSendMsg.setVisibility(View.GONE);
                etAmountSending.setText("0.000");
            }
        });
    }

    public void sendAlert(final String string) {
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
        if(block.getHeight() == rescanToBlock) {
            if(DashGui.currentProgress == PROGRESS_RESCANING) {
                //DashGui.currentProgress = PROGRESS_NONE;
                //dismissProgress();
                showProgress(PROGRESS_NONE);
            }
        }
    }

    public void updateBlockHeight(final StoredBlock block) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //tvBlockHeight.setText("" + kit.wallet().getLastBlockSeenHeight());
                if(block != null) {
                    tvBlockHeight.setText("" + block.getHeight());
                }else if(service != null && service.kit != null && service.kit.chain() != null){
                    tvBlockHeight.setText(""+service.kit.chain().getBestChainHeight());
                }
            }
        });
    }

    private boolean isDashServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DashService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void updateGUI(){
        updateBalance();
        updateBlockHeight(null);
    }

    private void resetEnterWord(){
        recoveryWords.clear();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                etEnterWord.setText("");
                tvTitleEnterWord.setText("Word 1 of "+MAX_WORD_LIST);
            }
        });
    }

    private boolean addRecoveryWord(){
        boolean full = false;
        if(!etEnterWord.getText().toString().equals("")){
            if(recoveryWords.size() < MAX_WORD_LIST){
                recoveryWords.add(etEnterWord.getText().toString().toLowerCase().replaceAll("\\s",""));
            }
            if(recoveryWords.size() == MAX_WORD_LIST){
                full=true;
            }else{
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvTitleEnterWord.setText("Word "+(recoveryWords.size()+1)+" of "+MAX_WORD_LIST);
                        etEnterWord.setText("");
                    }
                });
            }
        }
        return full;
    }
}
