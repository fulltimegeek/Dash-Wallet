package dash.fulltimegeek.walletspv;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.DefaultCoinSelector;

/**
 * Created by fulltimegeek on 1/20/16.
 */
public class IXCoinSelector extends DefaultCoinSelector {
    final static int IX_CONF = 6;
    @Override
    protected boolean shouldSelect(Transaction tx){
        return tx.getConfidence().getDepthInBlocks() >= IX_CONF;
    }
}
