package dash.fulltimegeek.walletspv;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.MonetaryFormat;
import org.spongycastle.util.encoders.Hex;


import java.util.List;

/**
 * Created by fulltimegeek on 1/31/16.
 */
public class TransactionListAdapter extends ArrayAdapter<Transaction> {
    List<Transaction> objects;
    DashGui context;
    int viewResourceId;

    public TransactionListAdapter(Context context, int viewResourceId,
                            List<Transaction> objects) {
        super(context, viewResourceId, objects);
        this.context = (DashGui)context;
        this.viewResourceId = viewResourceId;
        this.objects = objects;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        Transaction tx = objects.get(position);
        if(row == null){
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(viewResourceId, parent, false);
        }
        TextView tvAddress = (TextView) row.findViewById(R.id.tv_history_row_address);
        Address address =  tx.getOutput(0).getScriptPubKey().getToAddress(MainNetParams.get());
        boolean mine = context.service.kit.wallet().isPubKeyHashMine(address.getHash160());
        String direction = mine==true?"received":"sent";
        tvAddress.setText(tx.getOutput(0).getScriptPubKey().getToAddress(MainNetParams.get()).toString()+"\n"+direction+"  "+tx.getOutput(0).getValue().toFriendlyString());
        return row;

    }
}
