package dash.fulltimegeek.walletspv;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Created by fulltimegeek on 1/14/16.
 */
public class DashKit extends WalletAppKit {
    String walletPrefix = null;
    final static String defaultWalletAndChainPrefix = "checkpoint";
    final static String defaultWalletExt = ".wallet";
    final static String defaultChainExt = ".spvchain";
    private DeterministicSeed recoverySeed = null;
    private boolean replayWallet = false;
    public DashKit(NetworkParameters params, File directory, String defaultPrefix, String walletPrefix) {
        super(params, directory, defaultPrefix);
        this.walletPrefix = walletPrefix;
    }

    final static String TAG = "DashKit.java";
    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Log.i(TAG,"DASHKIT STARTING");
        walletPrefix = DashGui.preferences.getString(DashGui.PREF_KEY_WALLET_PREFIX,null);
        Log.i(TAG,"Found custom wallet prefix??"+walletPrefix);
        Context.propagate(context);
        if(recoverySeed != null){
            this.restoreFromSeed = recoverySeed;
        }
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create directory " + directory.getAbsolutePath());
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Starting up with directory = {}", directory);
        try {
            File chainFile = new File(directory, defaultWalletAndChainPrefix + defaultChainExt);
            boolean chainFileExists = chainFile.exists();
            walletPrefix = walletPrefix==null?defaultWalletAndChainPrefix:walletPrefix;
            vWalletFile = new File(directory, walletPrefix + defaultWalletExt);
            Log.i(TAG,"Using wallet file:"+vWalletFile.getAbsolutePath());
            boolean shouldReplayWallet = (vWalletFile.exists() && !chainFileExists) || restoreFromSeed != null || replayWallet;
            vWallet = createOrLoadWallet(shouldReplayWallet);
            if(replayWallet) {
                Log.i(TAG,"RESETTING WALLET");
                vWallet.reset();
            }
            replayWallet = false;
            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = provideBlockStore(chainFile);
            if (!chainFileExists || restoreFromSeed != null) {
                if (checkpoints == null && !Utils.isAndroidRuntime()) {
                    checkpoints = CheckpointManager.openStream(params);
                }

                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;
                    if (restoreFromSeed != null) {
                        time = restoreFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Deleting the chain file in preparation from restore.");
                            vStore.close();
                            if (!chainFile.delete()) {
                                Log.e(TAG,"Failed to delete chain file in preparation for restore.1");
                                throw new IOException("Failed to delete chain file in preparation for restore.");
                            }
                            vStore = new SPVBlockStore(params, chainFile);
                        }
                    } else {
                        time = vWallet.getEarliestKeyCreationTime();
                    }
                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Deleting the chain file in preparation from restore.");
                    vStore.close();
                    if (!chainFile.delete()) {
                        Log.e(TAG,"Failed to delete chain file in preparation for restore.2");
                        throw new IOException("Failed to delete chain file in preparation for restore.");
                    }
                    vStore = new SPVBlockStore(params, chainFile);
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();
            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                vPeerGroup.setMaxConnections(peerAddresses.length);
                peerAddresses = null;
            } else if (/*params != RegTestParams.get() && !useTor*/true) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vWallet);
            vPeerGroup.addWallet(vWallet);
            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();
                completeExtensionInitiations(vPeerGroup);

                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadProgressTracker listener = new DownloadProgressTracker();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        completeExtensionInitiations(vPeerGroup);
                        final PeerDataEventListener l = downloadListener == null ? new DownloadProgressTracker() : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "onFailure");
                        throw new RuntimeException(t);

                    }
                });
            }
        } catch (BlockStoreException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    public void shutdown() throws Exception{
        super.shutDown();
        onShutdownCompleted();
    }

    public void startup() throws Exception{
        startUp();
    }

    /*public void startup(boolean replay) throws Exception{
        replayWallet = true;
        startUp();
    }*/

    public void setReplayWallet(boolean replay){
        Log.i(TAG,"setting replay wallet to: "+replay);
        replayWallet = replay;
    }

    protected void onShutdownCompleted(){}

    public void setRecoverySeed(DeterministicSeed seed){
        this.recoverySeed = seed;
    }
}
