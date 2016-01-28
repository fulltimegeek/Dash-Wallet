package dash.fulltimegeek.walletspv;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicSeed;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by fulltimegeek on 1/17/16.
 */
public class DashService extends Service implements NewBestBlockListener{
    LocalBinder localBinder;
    DashKit kit =null;
    DashGui gui =null;
    DashService service = null;
    NewBestBlockListener bestBlockListener = null;
    static NetworkParameters params;
    final static String TAG = "DashService.java";
    boolean setupCompleted = false;
    boolean restoringCheckpoint = false;
    boolean restoringGenesis = false;
    static private boolean replayWallet = false;
    String walletPrefix = null;
    private boolean hasStarted = false;
    DeterministicSeed recoverySeed = null;

    @Override
    public void onCreate(){
        localBinder = new LocalBinder();
        params = MainNetParams.get();
        service = this;

    }

    public void buildKit(){
        Log.i(TAG, "DashKit building...");
        createCheckpoint(false);
        kit = new DashKit(params, getFilesDir(), DashKit.defaultWalletAndChainPrefix, walletPrefix) {
            @Override
            protected void onShutdownCompleted() {
                Log.i(TAG, "DashKit shutdown completed....");
                if (restoringCheckpoint) {
                    restoringCheckpoint = false;
                    createCheckpoint(true);
                    startSyncing();
                }else if(restoringGenesis){
                    deleteCheckpoint();
                    startSyncing();
                }
            }

            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                Log.i(TAG,"DashGui -- onSetupCompleted()");
                setupCompleted = true;
                vChain.addNewBestBlockListener(service);
                if(gui!=null) {
                    if(DashGui.currentProgress == DashGui.PROGRESS_STARTING) {
                        Log.i(TAG,"DashGui -- onSetupCompleted() dimissing dialog");
                        gui.showProgress(DashGui.PROGRESS_NONE);
                    }
                    setListeners(gui);
                    gui.updateGUI();
                }
            }
        };
        if(recoverySeed != null)
            kit.setRecoverySeed(recoverySeed);
        startSyncing();
    }

    @Override
    public int onStartCommand(Intent intentt, int flags, int startId) {
        hasStarted=true;
        buildKit();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
        if(bestBlockListener != null)
            bestBlockListener.notifyNewBestBlock(block);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if(kit != null)
                kit.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class LocalBinder extends Binder {
        public DashService getService() {
            return DashService.this;
        }
    }

    public void startSyncing() {
        kit.setReplayWallet(replayWallet);
        setReplayWallet(false);
        if (!setupCompleted) {
            kit.startAsync();
        } else {
            try {
                kit.startup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public boolean setListeners(DashGui gui){
        if(setupCompleted) {
            bestBlockListener = gui;
            if(kit != null)
                kit.setDownloadListener(gui);
            if(kit != null && kit.wallet() != null)
                kit.wallet().addEventListener(gui);
            if(kit.peerGroup() != null)
                kit.peerGroup().addConnectionEventListener(gui);
            return true;
        }
        return false;
    }

    public boolean deleteCheckpoint(){
        File chain = new File(getFilesDir(), DashKit.defaultWalletAndChainPrefix+".spvchain");
        return chain.delete();
    }

    public void createCheckpoint(boolean rebuild) {
        File chain = new File(getFilesDir(), DashKit.defaultWalletAndChainPrefix+".spvchain");
        OutputStream output = null;
        if (!chain.exists() || rebuild) {
            try {
                Log.i(TAG, "Restoring checkpoint");
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
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void setReplayWallet(boolean replay){
        Log.i(TAG,"setting replay wallet to: "+replay);
        replayWallet = replay;
    }


    public void setWalletPrefix(String prefix){
        walletPrefix = prefix;
    }

    public boolean hasStarted(){return hasStarted;}

    public void setRecoverySeed(DeterministicSeed seed){
        this.recoverySeed = seed;
    }
}
