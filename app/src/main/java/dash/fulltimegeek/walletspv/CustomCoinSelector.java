package dash.fulltimegeek.walletspv;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.DefaultCoinSelector;

/**
 * Created by fulltimegeek on 1/20/16.
 */
public class CustomCoinSelector extends DefaultCoinSelector {
    private int minConf = 6;

    @Override
    protected boolean shouldSelect(Transaction tx){
        return tx.getConfidence().getDepthInBlocks() >= minConf;
    }

    public void setMinConf(int min){
        minConf = min;
    }

    public int getMinConf(){
        return minConf;
    }
}