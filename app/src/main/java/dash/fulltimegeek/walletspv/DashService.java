package dash.fulltimegeek.walletspv;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.params.MainNetParams;

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

    @Override
    public void onCreate(){
        localBinder = new LocalBinder();
        params = MainNetParams.get();
        service = this;

    }

    private void buildKit(){
        Log.i(TAG, "DashKit building...");
        kit = new DashKit(params, getFilesDir(), "checkpoint") {
            @Override
            protected void onShutdownCompleted() {
                Log.i(TAG, "DashKit shutdown completed...");
                /*if (restoringCheckpoint) {
                    createCheckpoint(true);
                    startSyncing();
                }*/
            }

            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                setupCompleted = true;
                vChain.addNewBestBlockListener(service);
                if(gui!=null) {
                    setListeners(gui);
                    gui.updateGUI();
                }
            }
        };
        startSyncing();
    }

    @Override
    public int onStartCommand(Intent intentt, int flags, int startId) {
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

    public class LocalBinder extends Binder {
        public DashService getService() {
            return DashService.this;
        }
    }

    public void startSyncing() {
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
            kit.wallet().addEventListener(gui);
            kit.setDownloadListener(gui);
            kit.peerGroup().addConnectionEventListener(gui);
            return true;
        }
        return false;
    }
}
