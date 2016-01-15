package dash.fulltimegeek.walletspv;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;

import java.io.File;

/**
 * Created by fulltimegeek on 1/14/16.
 */
public class DashKit extends WalletAppKit {
    public DashKit(NetworkParameters params, File directory, String filePrefix) {
        super(params, directory, filePrefix);
    }

    public DashKit(Context context, File directory, String filePrefix) {
        super(context, directory, filePrefix);
    }

    public void shutdown() throws Exception{
        super.shutDown();
        onShutdownCompleted();
    }

    public void startup() throws Exception{
        super.startUp();
    }

    protected void onShutdownCompleted(){}
}
